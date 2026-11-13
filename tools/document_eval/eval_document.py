#!/usr/bin/env python3
"""EXT-01 문서 분석 API 테스트 스크립트 (TEST_GUIDE.md 참고).

instructions()와 OUTPUT_SCHEMA, 타임아웃/재시도/파싱 규칙은
device/.../ai/document/DocumentAnalyzer.kt 에서 옮긴 것이다.
DocumentAnalyzer.kt 가 바뀌면 이 파일도 맞추고 보고서에 기준 커밋을 적는다.
기준 커밋: 8b37f2a
"""

import argparse
import base64
import csv
import json
import math
import os
import re
import sys
import time
import unicodedata
from dataclasses import dataclass, field
from datetime import datetime
from difflib import SequenceMatcher
from pathlib import Path


def norm(name: str) -> str:
    """한글 자모 분리(NFD, macOS 파일시스템)와 완성형(NFC, Excel 등)이
    같은 글자로 보여도 다른 바이트라 비교가 깨진다. 비교할 땐 항상 이걸 거친다."""
    return unicodedata.normalize("NFC", name)

MODEL = "gpt-6-luna"          # DocumentAnalyzer.MODEL
TIMEOUT_SECONDS = 20.0        # TIMEOUT_MILLIS, 시도당
ATTEMPTS = 2                  # ATTEMPTS, 재시도 1회

FIELDS = ("title", "date", "time", "place", "items")

# mimeTypeOf(): 서비스가 받는 형식만 요청한다
SIGNATURES = (
    ("image/jpeg", ((0, b"\xff\xd8\xff"),)),
    ("image/png", ((0, b"\x89PNG"),)),
    ("image/webp", ((0, b"RIFF"), (8, b"WEBP"))),
    ("image/gif", ((0, b"GIF8"),)),
)

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "document_detected": {"type": "boolean"},
        "title": {"type": "string"},
        "date": {"type": "string"},
        "time": {"type": "string"},
        "place": {"type": "string"},
        "items": {"type": "array", "items": {"type": "string"}},
        "uncertain_fields": {"type": "array", "items": {"type": "string",
            "enum": ["title", "date", "time", "place", "items"]}},
    },
    "required": ["document_detected", "title", "date", "time", "place", "items",
        "uncertain_fields"],
    "additionalProperties": False,
}


def instructions(base_time: str) -> str:
    return (
        "사진 속 문서(고지서, 안내문, 문자 캡처 등)에서 일정 하나의 항목을 추출한다.\n"
        "title은 무엇, date는 날짜(YYYY-MM-DD), time은 시각(24시간 HH:MM),\n"
        "place는 안내하는 행사나 서비스가 실제로 이뤄지는 장소(접수처, 행사장 등)다.\n"
        "온라인과 방문이 둘 다 가능하면 방문 장소를 적는다. 단순 납부를 위해 아무 은행이나\n"
        "이용할 수 있는 계좌 정보는 place가 아니다. 자동이체·온라인 전용처럼 방문 자체가\n"
        "불가능한 경우에도 place를 빈 문자열로 둔다.\n"
        "items는 챙길 것 목록이다. 값이 없으면 빈 문자열이나 빈 목록으로 둔다.\n"
        "날짜가 여러 개면 접수·신청 마감일처럼 그 전에 행동해야 하는 날짜를 우선하고,\n"
        "안내 기간 자체(교육 기간, 운영 기간 등)는 후순위로 둔다. 어느 쪽인지 애매하면\n"
        "가장 이른 날짜를 쓰고 date를 uncertain_fields에 넣는다.\n"
        f"연도가 없는 날짜는 기준 시각 {base_time} 이후 가장 가까운 날짜로 해석한다.\n"
        "읽기 어렵거나 확실하지 않은 항목의 이름은 uncertain_fields에 넣는다.\n"
        "문서나 글자를 찾지 못하면 document_detected를 false로 둔다."
    )


def mime_type_of(data: bytes):
    for mime, parts in SIGNATURES:
        if all(data[off:off + len(sig)] == sig for off, sig in parts):
            return mime
    return None


def base_time_of(raw: str) -> str:
    """Kotlin now().atZone(systemDefault()).toOffsetDateTime().toString() 과 같은 모양."""
    moment = datetime.fromisoformat(raw) if raw else datetime.now().astimezone()
    if moment.utcoffset() is None:
        moment = moment.astimezone()
    stamp = moment.strftime("%Y-%m-%dT%H:%M")
    if moment.second or moment.microsecond:
        stamp += moment.strftime(":%S")
    offset = moment.strftime("%z")
    return f"{stamp}{offset[:3]}:{offset[3:]}"


# --- 앱 쪽 파싱 (DocumentAnalyzer.toAnalysis) -------------------------------

DATE_RE = re.compile(r"^(\d{4})-(\d{2})-(\d{2})$")              # LocalDate.parse
TIME_RE = re.compile(r"^(\d{2}):(\d{2})(?::(\d{2})(\.\d{1,9})?)?$")  # LocalTime.parse


def parse_date(value: str):
    match = DATE_RE.match(value.strip())
    if not match:
        return None
    try:
        return datetime(*(int(g) for g in match.groups())).strftime("%Y-%m-%d")
    except ValueError:
        return None


def parse_time(value: str):
    match = TIME_RE.match(value.strip())
    if not match:
        return None
    hour, minute = int(match.group(1)), int(match.group(2))
    second = int(match.group(3) or 0)
    if hour > 23 or minute > 59 or second > 59:
        return None
    return f"{hour:02d}:{minute:02d}"


@dataclass
class Analysis:
    """앱이 실제로 저장하게 되는 값."""
    detected: bool
    title: str = ""
    date: str = ""          # 파싱 실패 시 빈 값
    time: str = ""
    place: str = ""
    items: list = field(default_factory=list)
    uncertain: set = field(default_factory=set)
    format_errors: list = field(default_factory=list)  # 앱 파싱에 실패한 필드


def to_analysis(output: dict) -> Analysis:
    if not output.get("document_detected"):
        return Analysis(detected=False)
    uncertain = {name.lower() for name in output.get("uncertain_fields", [])
                 if name.lower() in FIELDS}
    raw_date, raw_time = output.get("date", ""), output.get("time", "")
    date, time_ = parse_date(raw_date), parse_time(raw_time)
    errors = []
    if raw_date.strip() and date is None:
        uncertain.add("date")
        errors.append("date")
    if raw_time.strip() and time_ is None:
        uncertain.add("time")
        errors.append("time")
    return Analysis(
        detected=True,
        title=output.get("title", "").strip(),
        date=date or "",
        time=time_ or "",
        place=output.get("place", "").strip(),
        items=[i.strip() for i in output.get("items", []) if i.strip()],
        uncertain=uncertain,
        format_errors=errors,
    )


# --- API 호출 --------------------------------------------------------------

@dataclass
class Call:
    output: dict = None
    error: str = ""      # UnavailableCause 또는 메시지
    latency: float = 0.0
    raw: str = ""
    attempts: int = 0


def call_api(client, model: str, image: bytes, mime: str, base_time: str) -> Call:
    """DocumentAnalyzer.requestWithRetry 와 같은 순서로 시도한다."""
    from openai import APIConnectionError, APIStatusError, APITimeoutError

    payload = dict(
        model=model,
        instructions=instructions(base_time),
        input=[{"role": "user", "content": [{
            "type": "input_image",
            "image_url": f"data:{mime};base64,{base64.b64encode(image).decode()}",
        }]}],
        text={"format": {"type": "json_schema", "name": "document_extraction",
                         "schema": OUTPUT_SCHEMA, "strict": True}},
        store=False,
    )
    cause, detail = "SERVICE_FAILED", ""
    started = time.monotonic()
    for attempt in range(1, ATTEMPTS + 1):
        try:
            response = client.responses.create(**payload)
            raw = response.output_text
            return Call(output=json.loads(raw), latency=time.monotonic() - started,
                        raw=raw, attempts=attempt)
        except APITimeoutError:
            cause, detail = "TIMEOUT", f"{TIMEOUT_SECONDS}s 초과"
        except APIConnectionError as e:                       # 네트워크, 재시도 없음
            return Call(error=f"NETWORK_UNAVAILABLE: {e}",
                        latency=time.monotonic() - started, attempts=attempt)
        except APIStatusError as e:
            cause, detail = "SERVICE_FAILED", f"HTTP {e.status_code} {e.message}"
        except json.JSONDecodeError as e:                     # 스키마와 다른 출력
            cause, detail = "SERVICE_FAILED", f"JSON 파싱 실패: {e}"
    return Call(error=f"{cause}: {detail}", latency=time.monotonic() - started,
                attempts=ATTEMPTS)


# --- 채점 ------------------------------------------------------------------

def squeeze(value: str) -> str:
    return re.sub(r"\s+", "", value or "")


def similar(a: str, b: str) -> float:
    a, b = squeeze(a), squeeze(b)
    if not a and not b:
        return 1.0
    return SequenceMatcher(None, a, b).ratio()


def loose_match(label_value: str, got_value: str) -> bool:
    """title/place는 정답을 그대로 베끼길 기대하지 않는다 — 정답의 핵심 어구가
    모델 출력에 포함돼 있거나(짧은 정답 vs 긴 설명형 출력), 전체적으로 많이
    닮았으면(패러프레이즈) 맞다고 본다."""
    a, b = squeeze(label_value), squeeze(got_value)
    if not a and not b:
        return True
    if not a or not b:
        return False
    if a in b or b in a:
        return True
    return SequenceMatcher(None, a, b).ratio() >= 0.5


def score(label: dict, got: Analysis) -> dict:
    """필드별 정답 여부. None = 채점 대상 아님."""
    marks = {name: None for name in FIELDS}
    marks["document_detected"] = got.detected == (label["document_detected"] == "true")
    if label["document_detected"] != "true" or not got.detected:
        return marks
    marks["date"] = got.date == label["date"].strip()
    marks["time"] = got.time[:5] == label["time"].strip()[:5]
    marks["title"] = loose_match(label["title"], got.title)
    marks["place"] = loose_match(label["place"], got.place)
    wanted = [i for i in label["items"].split(";") if i.strip()]
    hit = [w for w in wanted
           if any(loose_match(w, g) for g in got.items)]
    marks["items"] = len(hit) == len(wanted)
    marks["_items_recall"] = (len(hit) / len(wanted)) if wanted else None
    return marks


def percentile(values, p=0.95):
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(p * len(ordered)))
    return ordered[rank - 1]


def ratio(hit, total):
    return f"{hit}/{total} ({hit / total * 100:.1f}%)" if total else "해당 없음"


def summarize(rows):
    # UNSUPPORTED_TYPE 은 앱이 요청 자체를 하지 않으므로 호출 수에서 뺀다
    skipped = [r for r in rows if r["error"].startswith("UNSUPPORTED_TYPE")]
    called = [r for r in rows if r not in skipped]
    errors = [r for r in called if r["error"]]
    ok = [r for r in called if not r["error"]]
    print(f"\n=== 요약 (호출 {len(called)}회) ===")
    print(f"API 오류율       : {ratio(len(errors), len(called))}   기준 0%")
    for cause in sorted({r["error"].split(":")[0] for r in errors}):
        print(f"  - {cause}: {sum(1 for r in errors if r['error'].startswith(cause))}건")
    if skipped:
        print(f"요청 안 함       : {len(skipped)}건 (UNSUPPORTED_TYPE, "
              f"{', '.join(sorted({r['file'] for r in skipped}))})")
    if not ok:
        print("성공한 호출이 없어 정확도를 낼 수 없다.")
        return

    scored = [r for r in ok if r["labeled"]]
    def tally(name):
        marked = [r for r in scored if r["marks"].get(name) is not None]
        return sum(1 for r in marked if r["marks"][name]), len(marked)

    print(f"문서 감지 정확도 : {ratio(*tally('document_detected'))}   기준 95%+")
    print(f"date 정답률      : {ratio(*tally('date'))}   기준 90%+")
    print(f"time 정답률      : {ratio(*tally('time'))}   기준 90%+")
    print(f"title 정답률     : {ratio(*tally('title'))}   기준 80%+")
    print(f"place 정답률     : {ratio(*tally('place'))}   기준 80%+")
    recalls = [r["marks"]["_items_recall"] for r in scored
               if r["marks"].get("_items_recall") is not None]
    print("items 재현율     : " + (f"{sum(recalls) / len(recalls) * 100:.1f}%"
          if recalls else "해당 없음") + "   기준 80%+")
    print(f"형식 오류        : {sum(len(r['format_errors']) for r in ok)}건   기준 0건")

    unflagged = {r["file"] for r in scored if r["unflagged"]}
    print(f"미표시 오류      : 이미지 {len(unflagged)}장   (가장 중요)")
    for name in sorted(unflagged):
        shown = {r["file"]: r["unflagged"] for r in scored if r["file"] == name}
        print(f"  - {name}: {','.join(sorted(set(sum(shown.values(), []))))}")

    p95 = percentile([r["latency"] for r in ok])
    print(f"지연 p95         : {p95:.1f}s   기준 20s 미만")

    groups = {}
    for r in ok:
        groups.setdefault(r["file"], []).append(r["fingerprint"])
    repeated = {k: v for k, v in groups.items() if len(v) > 1}
    if repeated:
        same = sum(1 for v in repeated.values() if len(set(v)) == 1)
        print(f"일관성           : {ratio(same, len(repeated))} (사진별 전 회차 동일)")
        for name, prints in repeated.items():
            if len(set(prints)) > 1:
                print(f"  - 불일치: {name}")


# --- 실행 ------------------------------------------------------------------

_LABEL_DATE_RE = re.compile(r"^(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})$")
_LABEL_TIME_RE = re.compile(r"^(\d{1,2}):(\d{2})$")


def normalize_label_date(value: str) -> str:
    """Excel이 저장할 때 ISO 날짜를 'YYYY.M.D'(점 구분, 0 안 채움)로 되돌려놓는
    일이 반복돼서, 어떤 모양으로 들어와도 YYYY-MM-DD로 맞춰 읽는다."""
    m = _LABEL_DATE_RE.match(value.strip())
    if not m:
        return value
    y, mo, d = m.groups()
    return f"{y}-{int(mo):02d}-{int(d):02d}"


def normalize_label_time(value: str) -> str:
    """같은 이유로 'H:MM'(0 안 채움)도 HH:MM으로 맞춘다."""
    m = _LABEL_TIME_RE.match(value.strip())
    if not m:
        return value
    h, mi = m.groups()
    return f"{int(h):02d}:{mi}"


def load_labels(path: Path) -> dict:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    labels = {}
    for row in rows:
        clean = {k: (v or "").strip() for k, v in row.items() if k}
        clean["document_detected"] = clean.get("document_detected", "").lower()
        if clean.get("date"):
            clean["date"] = normalize_label_date(clean["date"])
        if clean.get("time"):
            clean["time"] = normalize_label_time(clean["time"])
        labels[norm(clean["file"])] = clean
    return labels


def make_client(timeout=TIMEOUT_SECONDS):
    from openai import OpenAI
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        sys.exit("OPENAI_API_KEY 가 없다. tools/document_eval/.env 를 만든다.")
    return OpenAI(api_key=key, timeout=timeout, max_retries=0)  # 재시도는 직접 한다


def read_image(path: Path):
    data = path.read_bytes()
    mime = mime_type_of(data)
    if mime is None:                      # 앱은 요청하지 않고 UNSUPPORTED_TYPE
        return None, None
    return data, mime


def run_one(client, model, path: Path, base_time: str):
    data, mime = read_image(path)
    if mime is None:
        return Call(error="UNSUPPORTED_TYPE: 서비스가 받지 않는 형식")
    return call_api(client, model, data, mime, base_time)


def smoke(args):
    path = Path(args.smoke)
    data, mime = read_image(path)
    if mime is None:
        sys.exit(f"UNSUPPORTED_TYPE: {path.name} 은 jpeg/png/webp/gif 가 아니다.")
    base = base_time_of(args.base_time)
    if args.dry_run:
        print(f"model={model_of(args)} mime={mime} store=False "
              f"timeout={TIMEOUT_SECONDS}s attempts={ATTEMPTS}")
        print(f"--- instructions ---\n{instructions(base)}")
        print(f"--- text.format ---\n{json.dumps({'type': 'json_schema', 'strict': True, 'schema': OUTPUT_SCHEMA}, ensure_ascii=False, indent=2)}")
        print(f"--- image ---\n{len(data)} bytes -> base64 {len(base64.b64encode(data))} chars")
        return
    call = run_one(make_client(), model_of(args), path, base)
    if call.error:
        sys.exit(f"실패: {call.error}  (시도 {call.attempts}회, {call.latency:.1f}s)")
    print(json.dumps(call.output, ensure_ascii=False, indent=2))
    got = to_analysis(call.output)
    print(f"\n앱 해석: date={got.date or '(빈 값)'} time={got.time or '(빈 값)'} "
          f"uncertain={sorted(got.uncertain) or '없음'} "
          f"형식오류={got.format_errors or '없음'}")
    print(f"지연 {call.latency:.1f}s, 시도 {call.attempts}회")


def model_of(args):
    return args.model or MODEL


def main_run(args):
    images_dir = Path(args.images)
    labels = load_labels(Path(args.labels)) if args.labels else {}
    files = sorted(p for p in images_dir.iterdir()
                   if p.is_file() and not p.name.startswith("."))
    if not files:
        sys.exit(f"{images_dir} 에 사진이 없다.")
    missing = [p.name for p in files if norm(p.name) not in labels]
    if missing:
        print(f"정답 없는 사진 {len(missing)}장은 채점에서 빠진다: {', '.join(missing)}")

    client = make_client()
    rows = []
    for run in range(1, args.runs + 1):
        for path in files:
            label = labels.get(norm(path.name))
            base = base_time_of((label or {}).get("base_time", ""))
            call = run_one(client, model_of(args), path, base)
            got = to_analysis(call.output) if call.output else Analysis(detected=False)
            marks = score(label, got) if (label and call.output) else {}
            unflagged = [n for n in FIELDS
                         if marks.get(n) is False and n not in got.uncertain]
            rows.append({
                "run": run, "file": path.name, "error": call.error,
                "latency": call.latency, "labeled": bool(label) and bool(call.output),
                "marks": marks, "unflagged": unflagged,
                "format_errors": got.format_errors,
                "fingerprint": json.dumps(
                    [got.detected, got.title, got.date, got.time, got.place,
                     got.items, sorted(got.uncertain)], ensure_ascii=False),
                "analysis": got, "raw": call.raw, "base_time": base,
            })
            mark = "x" if call.error else ("." if not unflagged else "!")
            print(f"[{run}/{args.runs}] {path.name} {mark} {call.latency:.1f}s"
                  + (f" {call.error}" if call.error else ""), flush=True)

    write_results(Path(args.out), rows)
    print(f"\n{args.out} 에 {len(rows)}행을 썼다. (x=오류, !=미표시 오류)")
    summarize(rows)


def write_results(path: Path, rows):
    header = ["run", "file", "error", "latency_s", "document_detected", "title",
              "date", "time", "place", "items", "uncertain_fields", "format_errors",
              "unflagged", "marks", "base_time", "raw_output"]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        for r in rows:
            got = r["analysis"]
            writer.writerow([
                r["run"], norm(r["file"]), r["error"], f"{r['latency']:.2f}",
                got.detected, got.title, got.date, got.time, got.place,
                ";".join(got.items), ";".join(sorted(got.uncertain)),
                ";".join(got.format_errors), ";".join(r["unflagged"]),
                ";".join(f"{k}={v}" for k, v in r["marks"].items()
                         if v is not None and not k.startswith("_")),
                r["base_time"], r["raw"],
            ])


def parse_args(argv):
    parser = argparse.ArgumentParser(description="EXT-01 문서 분석 API 테스트")
    parser.add_argument("--smoke", metavar="IMAGE", help="사진 1장으로 스모크 테스트")
    parser.add_argument("--images", default="images", help="사진 폴더 (기본 images)")
    parser.add_argument("--labels", default="labels.csv", help="정답 CSV")
    parser.add_argument("--runs", type=int, default=1, help="사진당 반복 횟수")
    parser.add_argument("--out", default="results.csv", help="결과 CSV")
    parser.add_argument("--model", help=f"모델 이름 (기본 {MODEL}, 레포 코드 값)")
    parser.add_argument("--base-time", default="",
                        help="기준 시각 ISO (스모크용, 기본 실행 시각)")
    parser.add_argument("--dry-run", action="store_true",
                        help="호출 없이 요청 형식만 출력")
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    try:
        from dotenv import load_dotenv
        load_dotenv(Path(__file__).parent / ".env")
    except ModuleNotFoundError:
        pass
    smoke(args) if args.smoke else main_run(args)
