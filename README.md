# 온디바이스 시니어 어시스턴트 (K2J3)

어르신이 **말로** 일정을 적고, 묻고, 알람을 맞추는 보드용 Android 앱입니다.
음성 인식(STT), 음성 합성(TTS), 언어 모델(LLM)은 모두 **보드 안에서** 돌아갑니다. 인터넷이 필요한 기능은 문서 읽기 하나입니다.

| 기능 | 하는 일 | 처리 위치 |
|---|---|---|
| 말로 부탁하기 | "내일 오후 3시에 병원 가야 해", "병원 언제였지?", "30분 뒤에 약 먹으라고 알람 맞춰 줘" | 보드 (STT → LLM → TTS) |
| 할 일 보기 | 오늘·내일·앞으로의 일정, 직접 적기·고치기·지우기 | 보드 |
| 알람 확인하기 | 맞춘 알람 목록, 알람 추가·시간 고치기·지우기 | 보드 |
| 미리 알림 | 일정 전 "딩동" + 음성 안내, 알람은 알람음 반복 | 보드 |
| 문서 읽기 | 웹캠으로 찍은 안내문에서 날짜·시간·장소를 뽑아 일정으로 | OpenAI API (인터넷 필요) |

## 모듈

| 모듈 | 내용 |
|---|---|
| `device` | 보드 앱 (이 README의 대상) |
| `companion` | 보호자용 휴대폰 앱 (준비 중, 아직 기본 화면만 있음) |
| `api` | 두 앱이 함께 쓸 공용 코드 자리 (준비 중) |

## 준비물

**컴퓨터**
- Android Studio (최신 안정판), Android SDK platform-tools(`adb`)
- NDK `27.0.12077973`, CMake `3.22.1` (Android Studio SDK Manager → SDK Tools에서 설치. LLM을 잇는 JNI 코드 빌드에 필요)
- Mac/Linux는 `bash`, `curl`, `shasum`(또는 `sha256sum`). Windows는 PowerShell과 Windows 10 이상의 `tar.exe`

**보드**
- Qualcomm QCS6490 보드 (Android 13, NPU: Hexagon v68)
- USB 스피커·마이크 (GeneralPlus USB Audio Device)
- USB 웹캠 Logitech C920 (문서 읽기용, 항상 꽂아 둠)
- 마우스·키보드 (선택)

## 1. 받아야 하는 것

| 무엇 | 어디서 | git에 있나 |
|---|---|---|
| 소스 코드 | 이 레포 | 있음 |
| 음성 모델 (VAD·STT·TTS, 풀면 약 300 MB) | `setup-models` 스크립트가 sherpa-onnx GitHub 릴리스에서 **자동으로** 받음 | 없음 |
| LLM 번들 (모델 `.bin` + Qualcomm 라이브러리 `.so`, 약 1.5 GB) | **학습팀에게 공유받기** (예: `team8_v4_alarm_repaired` 폴더) | 없음 (Qualcomm 라이브러리는 재배포 불가) |
| OpenAI API 키 | 팀에서 따로 전달 | **절대 올리지 않음** |

LLM 번들 폴더에는 아래 파일이 있어야 합니다. 스크립트가 하나라도 없으면 멈춥니다.

```
genie_config_npu.json  runtime_prompt_template.txt  tokenizer.json  llama32_hf_v68.bin  htp_backend_ext_config.json
libGenie.so  libQnnHtp.so  libQnnSystem.so  libQnnHtpV68Stub.so  libQnnHtpV68Skel.so  libQnnHtpNetRunExtensions.so  libc++_shared.so
```

## 2. OpenAI 키 넣기 (문서 읽기를 쓸 때만)

프로젝트 루트의 `local.properties`에 한 줄 추가합니다. 이 파일은 `.gitignore`에 들어 있어 git에 올라가지 않습니다.

```properties
openai.api.key=sk-...
```

- 키는 빌드할 때 앱 안(`BuildConfig.OPENAI_API_KEY`)에 들어갑니다. 키를 넣거나 바꾼 뒤에는 **앱을 다시 빌드·설치**해야 합니다.
- 키가 없어도 앱은 빌드되고, 문서 읽기만 "지금은 문서를 읽을 수 없어요"라고 답합니다. 나머지 기능은 모두 됩니다.
- 키가 들어간 APK는 다른 사람에게 주지 마세요.

## 3. 보드에 설치하고 실행하기

### 3-1. 보드 연결

```bash
adb connect 192.168.0.61:5555   # 보드 IP는 환경에 맞게
adb devices                     # 192.168.0.61:5555  device 가 보이면 OK
```

무선 adb는 자주 끊깁니다. 끊기면 `adb connect`를 다시 하면 됩니다.

### 3-2. 모델 올리기 + 앱 설치 (처음 한 번, 또는 모델을 바꿀 때)

Android Studio에서 `device` 앱을 **debug**로 한 번 실행해 설치한 뒤(스크립트가 `run-as`를 쓰므로 debug만 됨), 프로젝트 폴더에서:

**Mac / Linux**
```bash
scripts/setup-models.sh -s 192.168.0.61:5555 --llm-bundle ~/Downloads/team8_v4_alarm_repaired
```

**Windows (PowerShell)**
```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1 -Serial 192.168.0.61:5555 -LlmBundle C:\models\team8_v4_alarm_repaired
```

스크립트가 하는 일:
1. 음성 모델을 받고 해시를 확인합니다 (받은 파일은 `~/.cache/senior-assistant-models`, Windows는 `%LOCALAPPDATA%\senior-assistant-models`에 남아 다음부터는 다시 받지 않음).
2. LLM 번들의 Qualcomm 라이브러리를 `device/src/main/jniLibs/arm64-v8a/`(git 제외)로 복사하고 debug APK를 다시 설치합니다.
3. 모델을 앱 폴더(`files/models/{vad,stt,tts,llm}`)에 올리고 보드에서 해시를 다시 확인합니다.
4. 앱을 켜서 `model files ready` 로그를 확인합니다.

| 옵션 | 뜻 |
|---|---|
| `-s` / `-Serial` | 보드가 여러 대이거나 무선일 때 대상 지정 |
| `--llm-bundle` / `-LlmBundle` | LLM 번들 폴더. **빼면 음성 모델만** 올리고 보드에 있던 LLM은 그대로 둠 |
| `--download-only` / `-DownloadOnly` | 보드 없이 받아서 정리만 |

> Windows용 `setup-models.ps1`은 Mac용과 같은 일을 하지만 아직 실제 보드에서 충분히 돌려 보지 못했습니다. 문제가 있으면 알려 주세요.

### 3-3. 평소 실행

모델이 한 번 올라가 있으면, 이후에는 Android Studio에서 `device`를 실행(▶)하거나 `./gradlew :device:installDebug`만 하면 됩니다. **앱을 다시 설치해도 모델은 남아 있습니다** (앱을 *지우면* 같이 지워짐).

- 앱을 켜면 모델을 불러오는 데 약 15초 걸립니다 (LLM 약 11초, STT 약 3초, TTS 약 1.4초).
- 보드에 올라간 뒤에는 Android Studio와 연결을 끊어도 그대로 동작합니다.
- LLM 응답은 한 번에 약 7~8초 걸립니다.

## 4. 테스트

```bash
# 단위 테스트 (컴퓨터에서)
./gradlew :device:testDebugUnitTest

# 보드에서 도는 테스트: 설치 후 am instrument로 실행
./gradlew :device:installDebug :device:installDebugAndroidTest
adb -s 192.168.0.61:5555 shell am instrument -w \
  -e class com.k2j3.seniorassistant.device.server.BoardApiServerTest \
  com.k2j3.seniorassistant.device.test/androidx.test.runner.AndroidJUnitRunner
```

> ⚠️ **`connectedAndroidTest`(Android Studio의 androidTest 실행 버튼 포함)는 쓰지 마세요.**
> 테스트가 끝나면 앱을 **삭제**하므로 보드에 올린 모델(약 1.7 GB)이 같이 지워집니다. 그러면 앱이 가짜 음성으로 바뀌어 "삐" 소리만 납니다.
> 실수로 돌렸다면 `setup-models` 스크립트를 다시 실행하세요.

## 5. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| 대답 대신 **"삐" 소리**만 남 | 보드에 모델이 없음 (앱 삭제, `connectedAndroidTest` 등). 앱은 모델이 없으면 가짜 STT/TTS/LLM으로 켜짐 | `setup-models` 스크립트 다시 실행 후 앱 재시작 |
| **소리가 안 남** 또는 **마이크가 아무것도 못 들음** | 이 보드의 Android 13은 USB 오디오 장치를 **마지막에 꽂은 하나만** 씀. 웹캠(C920)이 그 자리를 차지하면 소리는 연결 안 된 내장 스피커로, 마이크는 웹캠 마이크로 감 | 웹캠은 꽂아 둔 채 **USB 스피커를 뽑았다가 마지막에 다시 꽂기** |
| 소리가 너무 작음 | USB 스피커 미디어 음량이 낮음 | 보드 볼륨 버튼, 또는 `adb shell cmd media_session volume --stream 3 --set 15` |
| 앱이 켜지다가 꺼짐 | 앱을 끄자마자 다시 켜면 NPU 메모리가 덜 풀려 로딩 중 메모리 부족으로 종료됨 | 앱을 끈 뒤 **10초쯤 기다렸다가** 켜기 |
| 문서 읽기가 "지금은 문서를 읽을 수 없어요" | OpenAI 키가 없거나, 인터넷 안 됨, 또는 시간 초과(20초 × 2회) | `local.properties` 키 확인 후 다시 빌드, 보드 인터넷 확인 |
| 카메라를 열지 못함 | 웹캠이 빠졌거나 권한 거부 | 웹캠 연결 확인, 앱 설정에서 카메라 권한 허용 |
| `run-as가 안 됩니다` | release 빌드가 설치됨 | debug 빌드로 설치 |
| `adb: device offline` / 연결 끊김 | 무선 adb 끊김 | `adb connect 192.168.0.61:5555` 다시 |

모델 상태를 직접 확인하려면:
```bash
adb -s 192.168.0.61:5555 shell run-as com.k2j3.seniorassistant.device ls files/models   # llm stt tts vad
adb -s 192.168.0.61:5555 logcat -s SeniorAssistantApp                                   # model files ready / missing
```

## 6. 시연용 예시 문장

보드 평가에서 모델이 정답을 낸 문장들입니다. **등록 → 조회** 순서로 하면 조회 결과가 바로 나옵니다.

**일정 등록**
- 모레 오후 두 시 반 치과 검진이라고 기록해 놔
- 다음 주 월요일 오후 두 시 주민센터 방문을 적자
- 내일 오후 세 시에 수도 점검 온다니까 일정에 적어 놔
- 5월 5일 손자가 온대. 시간은 아직 모르니까 달력에 날짜만 넣어

**일정 조회**
- 내일 내 약속을 다시 들을 수 있겠니?
- 이번 금요일 달력에 무슨 약속이 있니?
- 다음 일정이 뭐야?
- 병원 가는 거 언제야?

**알람** (말로 부탁하기, 또는 알람 확인하기 → 알람 추가 → 말로 맞추기)
- 내일 오전 일곱 시 반에 소리로 깨워 주겠니?
- 오늘 오후 세 시 반에 병원 출발하라고 울려 줘
- 30분 뒤에 약 먹으라고 알람 맞춰 줘 (울리는 것까지 보려면 1~2분 뒤로)

**팁**
- **오전/오후를 붙여** 말하세요. 빼면 "오전 세 시요, 오후 세 시요?" 하고 되묻습니다.
- "내일모레", "알람도 같이 해 줘"처럼 일정·알람을 섞은 말은 아직 "다시 말씀해 주세요"로 빠질 수 있습니다.
- 낯선 가게·병원 이름은 음성 인식이 틀리기 쉽습니다.
