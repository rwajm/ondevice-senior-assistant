#!/usr/bin/env bash
# 온디바이스 음성 모델(VAD, STT, TTS)을 받아서 보드의 앱 폴더에 올린다. (Mac / Linux)
# Windows는 setup-models.ps1을 쓴다.
#
# 사용법:
#   scripts/setup-models.sh                    # 연결된 기기 하나에 올린다
#   scripts/setup-models.sh -s 192.168.0.61:5555
#   scripts/setup-models.sh --download-only    # 받아서 정리만 (보드 없이)
#
# 먼저 앱을 debug 빌드로 보드에 한 번 설치해 둔다 (run-as는 debug 빌드에서만 된다).
# 받은 파일은 ~/.cache/senior-assistant-models 에 남겨서 다음에는 다시 받지 않는다.
set -euo pipefail

PKG=com.k2j3.seniorassistant.device
RELEASES=https://github.com/k2-fsa/sherpa-onnx/releases/download
CACHE="${SENIOR_MODELS_CACHE:-$HOME/.cache/senior-assistant-models}"
SERIAL=""
DOWNLOAD_ONLY=0

while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        --download-only) DOWNLOAD_ONLY=1; shift ;;
        -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
        *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
    esac
done

step() { printf '\n==> %s\n' "$*"; }
fail() { printf '\n[실패] %s\n' "$*" >&2; exit 1; }

sha256() { shasum -a 256 "$1" 2>/dev/null | cut -d' ' -f1 || sha256sum "$1" | cut -d' ' -f1; }

# name|url|sha256 (받는 파일)
DOWNLOADS=(
    "silero_vad.onnx|$RELEASES/asr-models/silero_vad.onnx|9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
    # 2024-07-17 원본을 쓴다. 2025-09-09 판은 광둥어로 다시 학습한 모델이라 한국어를 광둥어로 알아듣는다
    "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2|$RELEASES/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2|7d1efa2138a65b0b488df37f8b89e3d91a60676e416f515b952358d83dfd347e"
    "vits-mimic3-ko_KO-kss_low.tar.bz2|$RELEASES/tts-models/vits-mimic3-ko_KO-kss_low.tar.bz2|f015d1d15a52ed00d6fe22757c5ef4a74283c53daf829c838fa5c22616ed789c"
)

# 앱이 읽는 위치|sha256 (ModelFiles.kt와 같은 구조)
MODEL_FILES=(
    "vad/silero_vad.onnx|9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
    "stt/model.int8.onnx|c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"
    "stt/tokens.txt|f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
    "tts/ko_KO-kss_low.onnx|552af73776a1c2a705d828e1435592419eb42b1de9cc2bcb3bb5bcf6efb90cd9"
    "tts/tokens.txt|d2a7e6696166162a0613812a02f23f2f682a12e05a4cc9864193a3c71c1f3ecb"
)

# ---------------------------------------------------------------- 1. 받기
step "1/4 모델 받기 (${CACHE})"
mkdir -p "$CACHE/downloads"
for entry in "${DOWNLOADS[@]}"; do
    IFS='|' read -r name url sum <<< "$entry"
    file="$CACHE/downloads/$name"
    if [ -f "$file" ] && [ "$(sha256 "$file")" = "$sum" ]; then
        echo "  있음: ${name}"
        continue
    fi
    echo "  받는 중: ${name}"
    curl -fL --retry 3 --progress-bar -o "$file.part" "$url" || fail "${name} 다운로드 실패 (인터넷 연결 확인)"
    [ "$(sha256 "$file.part")" = "$sum" ] || { rm -f "$file.part"; fail "${name} 해시가 다릅니다 (파일이 깨졌거나 바뀜)"; }
    mv "$file.part" "$file"
done

# ---------------------------------------------------------------- 2. 정리
step "2/4 앱 폴더 구조로 정리"
MODELS="$CACHE/models"
WORK="$CACHE/extract"
rm -rf "$MODELS" "$WORK"
mkdir -p "$MODELS/vad" "$MODELS/stt" "$MODELS/tts" "$WORK"
tar -xjf "$CACHE/downloads/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2" -C "$WORK"
tar -xjf "$CACHE/downloads/vits-mimic3-ko_KO-kss_low.tar.bz2" -C "$WORK"
cp "$CACHE/downloads/silero_vad.onnx" "$MODELS/vad/"
cp "$WORK/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx" \
   "$WORK/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt" "$MODELS/stt/"
cp "$WORK/vits-mimic3-ko_KO-kss_low/ko_KO-kss_low.onnx" "$WORK/vits-mimic3-ko_KO-kss_low/tokens.txt" "$MODELS/tts/"
cp -R "$WORK/vits-mimic3-ko_KO-kss_low/espeak-ng-data" "$MODELS/tts/"
rm -rf "$WORK"

for entry in "${MODEL_FILES[@]}"; do
    IFS='|' read -r path sum <<< "$entry"
    [ "$(sha256 "$MODELS/$path")" = "$sum" ] || fail "${path} 해시가 다릅니다"
done
FILE_COUNT=$(find "$MODELS" -type f | wc -l | tr -d ' ')
echo "  ${MODELS} (${FILE_COUNT}개 파일, $(du -sh "$MODELS" | cut -f1))"

if [ "$DOWNLOAD_ONLY" = 1 ]; then
    echo; echo "완료: 정리만 했습니다. 보드에 올리려면 --download-only 없이 다시 실행하세요."
    exit 0
fi

# ---------------------------------------------------------------- 3. 보드에 올리기
step "3/4 보드에 올리기"
ADB="$(command -v adb || true)"
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$ADB" ] && break
    [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ] && ADB="$candidate/platform-tools/adb"
done
[ -n "$ADB" ] || fail "adb를 찾을 수 없습니다 (Android SDK platform-tools 설치 또는 PATH 확인)"

adb() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

[ "$(adb get-state 2>/dev/null || true)" = device ] \
    || fail "기기가 연결되어 있지 않거나 여러 대입니다 (adb devices 확인, 여러 대면 -s <serial>)"
adb shell pm path "$PKG" 2>/dev/null | grep -q package: \
    || fail "앱이 설치되어 있지 않습니다. Android Studio에서 device 앱(debug)을 먼저 한 번 실행하세요"
adb shell run-as "$PKG" true 2>/dev/null \
    || fail "run-as가 안 됩니다. release가 아니라 debug 빌드로 설치하세요"

TMP=/data/local/tmp/senior-assistant-models
adb shell rm -rf "$TMP"
echo "  올리는 중 (무선이면 1~2분)..."
adb push "$MODELS" "$TMP" > /dev/null
adb shell chmod -R a+rX "$TMP" # 앱이 shell 소유 파일을 읽을 수 있게
adb shell "run-as $PKG sh -c 'rm -rf files/models.new && cp -r $TMP files/models.new && rm -rf files/models && mv files/models.new files/models'" \
    || fail "앱 폴더로 복사하지 못했습니다"
adb shell rm -rf "$TMP"

BOARD_COUNT=$(adb shell "run-as $PKG sh -c 'find files/models -type f | wc -l'" | tr -d '[:space:]')
[ "$BOARD_COUNT" = "$FILE_COUNT" ] || fail "보드의 파일 수(${BOARD_COUNT})가 이 컴퓨터(${FILE_COUNT})와 다릅니다"
BOARD_SUM=$(adb shell "run-as $PKG sha256sum files/models/stt/model.int8.onnx" | cut -d' ' -f1)
[ "$BOARD_SUM" = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51" ] || fail "보드의 STT 모델 해시가 다릅니다"
echo "  파일 ${BOARD_COUNT}개, 해시 일치"

# ---------------------------------------------------------------- 4. 확인
step "4/4 앱을 다시 켜서 확인"
adb shell am force-stop "$PKG"
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
for _ in $(seq 1 15); do
    sleep 1
    LOG=$(adb logcat -d -s SeniorAssistantApp 2>/dev/null | grep -E "model files (ready|missing)" || true)
    [ -n "$LOG" ] && break
done
echo "  ${LOG:-(로그 없음)}"
echo "$LOG" | grep -q "model files ready" || fail "앱이 모델을 찾지 못했습니다 (위 로그 확인)"

echo; echo "완료: 모델을 올렸고 앱이 찾았습니다."
