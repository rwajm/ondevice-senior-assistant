# EXT-01 문서 분석 API 테스트 가이드

사람이 직접 따라 하면서 EXT-01(문서 사진 분석) 기능을 테스트하기 위한 가이드다.

## 1. 배경

- 프로젝트: 시니어 생활정보 선제 안내 AI (팀 K2J3)
- 레포: https://github.com/rwajm/ondevice-senior-assistant
- 테스트 대상: 문서 사진 분석 기능(요구사항 EXT-01, FR-04)
  - 사용자가 고지서, 안내문, 문자 캡처를 찍으면 외부 AI API가 일정 정보를 JSON으로 추출한다.
  - 관련 코드: `device/src/main/java/com/k2j3/seniorassistant/device/ai/document/`
    - `DocumentAnalyzer.kt`: 요청 구성, 타임아웃, 재시도, 결과 해석
    - `ExternalAiClient.kt`: API 호출 인터페이스 (실제 구현체는 아직 없고 `FakeExternalAiClient`만 있음)
- 테스트 범위 밖: 온디바이스 LLM(`ai/llm/`, `<maum_0>`/`<maum_1>` function call). 이건 별도 작업이다.

## 2. 테스트 목적

1. 레포 코드의 요청 형식이 실제 OpenAI API에서 에러 없이 동작하는지 확인한다.
2. 실제 문서 사진에서 필드 추출이 얼마나 정확한지 측정한다.
3. 앱 로직(타임아웃 20초, 재시도 1회, 날짜/시각 파싱)이 실제 응답과 맞는지 확인한다.
4. 실패 사례를 원인별로 분류해 프롬프트나 코드 수정 근거를 만든다.

## 3. 요청 명세 (레포 코드 기준)

| 항목 | 값 | 출처 |
|---|---|---|
| 모델 | `gpt-6-luna` | `DocumentAnalyzer.MODEL` |
| API | OpenAI Responses API, `text.format` = `json_schema`, `strict: true` | `ExternalAiRequest` 필드 구성 |
| 입력 | instructions(기준 시각 포함) + base64 이미지 1장 | `DocumentAnalyzer.analyze()` |
| 허용 이미지 | jpeg, png, webp, gif (그 외는 요청하지 않고 `UNSUPPORTED_TYPE`) | `mimeTypeOf()` — 확장자가 아니라 파일 내용으로 판별 |
| `store` | `false` | `ExternalAiRequest.store` |
| 타임아웃 | 시도당 20초 | `TIMEOUT_MILLIS` |
| 재시도 | 총 2회 시도 (네트워크 오류는 재시도 없음) | `ATTEMPTS` |

출력 스키마 필드:

| 필드 | 형식 | 앱에서의 처리 |
|---|---|---|
| `document_detected` | boolean | `false`면 "다시 찍어 주세요" 안내 |
| `title` | string | 무엇(일정 제목) |
| `date` | `YYYY-MM-DD` | `LocalDate.parse` 실패 시 빈 값 + 확인 필요 표시 |
| `time` | `HH:MM` (24시간) | `LocalTime.parse` 실패 시 빈 값 + 확인 필요 표시 |
| `place` | string | 장소 |
| `items` | string 배열 | 챙길 것, 빈 문자열은 제거 |
| `uncertain_fields` | `title/date/time/place/items` 중 일부 | 사용자에게 확인 필요로 강조 표시 |

연도 없는 날짜는 "기준 시각 이후 가장 가까운 날짜"로 해석하도록 instructions에 적혀 있다. 이게 `labels.csv`의 `base_time` 필드가 필요한 이유다 (§6 참고).

## 4. 환경과 파일

로컬 PC에서 실행한다. GPU나 서버는 필요 없다.

```
ondevice-senior-assistant/tools/document_eval/
├── eval_document.py       # 테스트 스크립트 (이미 작성돼 있음)
├── TEST_GUIDE.md          # 이 문서
├── labels.csv             # 정답 데이터 (직접 작성, 레포에 커밋됨)
├── .env.example           # .env 템플릿 (커밋됨)
├── .env                   # OPENAI_API_KEY=... (커밋 금지, .gitignore에 걸림)
├── .gitignore
├── .venv/                 # 파이썬 가상환경 (커밋 금지)
└── images/                # 테스트 사진 (커밋 금지)
```

**최초 1회 설치:**
```bash
cd tools/document_eval
python3 -m venv .venv
./.venv/bin/pip install openai python-dotenv
cp .env.example .env   # 연 다음 OPENAI_API_KEY=sk-... 로 실제 키 입력
```

이후로는 `./.venv/bin/python eval_document.py ...` 형태로 바로 실행하면 된다 (가상환경을 따로 활성화할 필요 없음).

## 5. 테스트 데이터 구성

사진 20~30장. 아래 비율을 목표로 한다.

| 분류 | 장수 | 확인하려는 것 |
|---|---|---|
| 정상 촬영 고지서/안내문 | 10~15 | 기본 추출 정확도 |
| 문자 메시지 캡처 | 3~5 | 촬영물이 아닌 화면 이미지 처리 |
| 흐림, 기울어짐, 그림자, 일부 잘림 | 4~6 | `uncertain_fields`가 제대로 붙는지 |
| 날짜가 여러 개 있는 문서 (납부기한 + 발행일 등) | 2~3 | 어느 날짜를 일정으로 고르는지 |
| 연도 없는 날짜 (연말에 다음 해 1월 일정 등) | 2~3 | `base_time`과 함께 연도 해석 확인 |
| 문서가 아닌 사진 (음식, 풍경, 사람 없는 실내 등) | 3~5 | `document_detected=false` |

규칙:
- 이름, 주소, 고객번호, 계좌번호, 연락처는 가린 뒤 사용한다 (지난 문서를 써도 무방하지만 개인정보 가리기는 동일하게 적용).
- 정답은 **모델을 돌리기 전에 사진만 보고** 작성한다.
- 파일 형식은 jpeg/png/webp/gif만 테스트 대상이다. HEIC 등은 jpg/png로 변환해서 넣는다.
- 파일명은 되도록 단순하게 짓는다 (공백·특수문자 최소화). 한글 파일명 자체는 문제없지만, 뒤의 §6 유의사항을 꼭 읽는다.

## 6. 정답 CSV 형식 (`labels.csv`)

```
file,document_detected,title,date,time,place,items,base_time,note
```

| 필드 | 설명 |
|---|---|
| `file` | `images/` 안의 파일명 (확장자 포함, 정확히 일치해야 함) |
| `document_detected` | `true` 또는 `false` |
| `title` | 일정 제목 |
| `date` | **`YYYY-MM-DD` 형식만** (예: `2026-10-28`). `2026.10.28`, `26-10-28` 등은 안 됨 |
| `time` | **`HH:MM` 24시간, 0 채운 두 자리** (예: `09:00`). `9:00`은 안 됨 |
| `place` | 장소 |
| `items` | 챙길 것, `;`로 구분 (예: `신분증;검진표`) |
| `base_time` | 연도 해석을 시험할 때만 ISO 형식으로 입력 (예: `2026-12-20T10:00+09:00`). 비우면 실행 시각 기준 |
| `note` | 사진 특징, 정답 판단이 애매했던 이유, 여러 날짜 중 왜 이걸 골랐는지 등 |

해당 값이 문서에 없으면 빈칸으로 둔다.

## 7. 실행 순서

1. 스모크 테스트:
   ```bash
   ./.venv/bin/python eval_document.py --smoke images/<사진>.jpg
   ```
   JSON이 출력되면 통과. 에러가 나면 메시지 전문을 기록한다. 호출 없이 요청 형식만 보고 싶으면 `--dry-run`을 추가한다.
2. 본 테스트:
   ```bash
   ./.venv/bin/python eval_document.py --images images --labels labels.csv --runs 3
   ```
   같은 사진을 3회씩 돌려 결과가 일관적인지도 본다. `labels.csv`에 정답이 없는 사진은 자동으로 채점에서 빠지고 경고가 뜬다.
3. `results.csv`와 터미널 요약을 확인한다.

## 8. 측정 지표와 판정 기준

판정 기준은 팀 합의 전 임시값이다. 팀과 확정한 뒤 이 표를 수정한다.

| 지표 | 계산 | 임시 기준 |
|---|---|---|
| API 오류율 | 오류 건수 / 전체 호출 (UNSUPPORTED_TYPE은 제외) | 0% |
| 문서 감지 정확도 | `document_detected` 일치 비율 | 95% 이상 |
| date 정답률 | 정확히 일치 | 90% 이상 |
| time 정답률 | 앞 5자리(HH:MM) 일치 | 90% 이상 |
| title, place 정답률 | 공백 제거 후 유사도 0.8 이상 | 80% 이상 |
| items 재현율 | 정답 항목 중 맞힌 비율 | 80% 이상 |
| 형식 오류 | date/time이 앱 파싱에 실패한 건수 | 0건 |
| 미표시 오류 | 틀린 필드가 `uncertain_fields`에 없는 이미지 수 | 최소화 (가장 중요) |
| 지연 p95 | 성공 호출 지연의 95번째 값 | 20초 미만 |
| 일관성 | 같은 사진 3회 결과가 모두 같은 비율 | 참고용 |

미표시 오류를 가장 중요하게 보는 이유: 틀린 값이 확인 표시 없이 저장되면 사용자(어르신)가 잘못된 날짜를 믿게 된다.

## 9. 실패 분석 방법

틀린 사례마다 아래 중 하나로 분류한다.

| 분류 | 예시 | 대응 |
|---|---|---|
| 이미지 품질 | 글자가 사람 눈에도 안 보임 | 앱 안내 문구, 재촬영 유도 |
| 프롬프트 모호 | 날짜가 여러 개일 때 기준 없음 | `instructions()` 수정 실험 |
| 스키마/형식 | 시각을 "오전 9시"나 범위로 출력 | 프롬프트에 형식 예시 추가 |
| 정답 오류 | 정답 CSV를 잘못 씀 | 정답 수정 후 재실행 |
| 모델 한계 | 위에 해당 없음 | 상위 모델(`gpt-6-sol`) 비교 검토 |

프롬프트를 고칠 때는 스크립트의 `instructions()`만 바꿔 다시 돌리고, 바꾸기 전과 후의 지표를 함께 기록한다.