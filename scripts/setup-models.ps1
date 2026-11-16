# 온디바이스 음성 모델(VAD, STT, TTS)을 받아서 보드의 앱 폴더에 올린다. (Windows)
# Mac / Linux는 setup-models.sh를 쓴다. 하는 일은 같다.
#
# 사용법 (PowerShell, 프로젝트 폴더에서):
#   powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1 -Serial 192.168.0.61:5555
#   powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1 -DownloadOnly
#
# 먼저 앱을 debug 빌드로 보드에 한 번 설치해 둔다 (run-as는 debug 빌드에서만 된다).
# 받은 파일은 %LOCALAPPDATA%\senior-assistant-models 에 남겨서 다음에는 다시 받지 않는다.
# 압축 풀기에 Windows 10 이상에 들어 있는 tar.exe를 쓴다.
param(
    [string]$Serial = "",
    [switch]$DownloadOnly
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue" # 진행 막대가 있으면 Invoke-WebRequest가 매우 느려진다
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Pkg = "com.k2j3.seniorassistant.device"
$Releases = "https://github.com/k2-fsa/sherpa-onnx/releases/download"
$Cache = if ($env:SENIOR_MODELS_CACHE) { $env:SENIOR_MODELS_CACHE } else { Join-Path $env:LOCALAPPDATA "senior-assistant-models" }
$Tar = Join-Path $env:SystemRoot "System32\tar.exe"
$SttDir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
$TtsDir = "vits-mimic3-ko_KO-kss_low"

function Step($text) { Write-Host ""; Write-Host "==> $text" }
function Fail($text) { Write-Host ""; Write-Host "[실패] $text" -ForegroundColor Red; exit 1 }
function Sha256($path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLower() }

# 받는 파일
$Downloads = @(
    @{ Name = "silero_vad.onnx"; Url = "$Releases/asr-models/silero_vad.onnx"
       Sha = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6" },
    # 2024-07-17 원본을 쓴다. 2025-09-09 판은 광둥어로 다시 학습한 모델이라 한국어를 광둥어로 알아듣는다
    @{ Name = "$SttDir.tar.bz2"; Url = "$Releases/asr-models/$SttDir.tar.bz2"
       Sha = "7d1efa2138a65b0b488df37f8b89e3d91a60676e416f515b952358d83dfd347e" },
    @{ Name = "$TtsDir.tar.bz2"; Url = "$Releases/tts-models/$TtsDir.tar.bz2"
       Sha = "f015d1d15a52ed00d6fe22757c5ef4a74283c53daf829c838fa5c22616ed789c" }
)

# 앱이 읽는 위치 (ModelFiles.kt와 같은 구조)
$ModelFiles = @(
    @{ Path = "vad/silero_vad.onnx"; Sha = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6" },
    @{ Path = "stt/model.int8.onnx"; Sha = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51" },
    @{ Path = "stt/tokens.txt"; Sha = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc" },
    @{ Path = "tts/ko_KO-kss_low.onnx"; Sha = "552af73776a1c2a705d828e1435592419eb42b1de9cc2bcb3bb5bcf6efb90cd9" },
    @{ Path = "tts/tokens.txt"; Sha = "d2a7e6696166162a0613812a02f23f2f682a12e05a4cc9864193a3c71c1f3ecb" }
)
$SttSha = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"

# ---------------------------------------------------------------- 1. 받기
Step "1/4 모델 받기 ($Cache)"
$DownloadDir = Join-Path $Cache "downloads"
New-Item -ItemType Directory -Force -Path $DownloadDir | Out-Null
foreach ($d in $Downloads) {
    $file = Join-Path $DownloadDir $d.Name
    if ((Test-Path -LiteralPath $file) -and ((Sha256 $file) -eq $d.Sha)) {
        Write-Host "  있음: $($d.Name)"
        continue
    }
    Write-Host "  받는 중: $($d.Name)"
    $part = "$file.part"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $d.Url -OutFile $part
    } catch {
        Fail "$($d.Name) 다운로드 실패 (인터넷 연결 확인): $($_.Exception.Message)"
    }
    if ((Sha256 $part) -ne $d.Sha) {
        Remove-Item -Force -LiteralPath $part
        Fail "$($d.Name) 해시가 다릅니다 (파일이 깨졌거나 바뀜)"
    }
    Move-Item -Force -LiteralPath $part -Destination $file
}

# ---------------------------------------------------------------- 2. 정리
Step "2/4 앱 폴더 구조로 정리"
if (-not (Test-Path -LiteralPath $Tar)) { Fail "tar.exe가 없습니다 (Windows 10 1803 이상 필요)" }
$Models = Join-Path $Cache "models"
$Work = Join-Path $Cache "extract"
foreach ($dir in @($Models, $Work)) {
    if (Test-Path -LiteralPath $dir) { Remove-Item -Recurse -Force -LiteralPath $dir }
}
foreach ($sub in @("vad", "stt", "tts")) { New-Item -ItemType Directory -Force -Path (Join-Path $Models $sub) | Out-Null }
New-Item -ItemType Directory -Force -Path $Work | Out-Null

foreach ($archive in @("$SttDir.tar.bz2", "$TtsDir.tar.bz2")) {
    & $Tar -xjf (Join-Path $DownloadDir $archive) -C $Work
    if ($LASTEXITCODE -ne 0) { Fail "$archive 압축을 풀지 못했습니다" }
}
Copy-Item -LiteralPath (Join-Path $DownloadDir "silero_vad.onnx") -Destination (Join-Path $Models "vad")
Copy-Item -LiteralPath (Join-Path $Work "$SttDir\model.int8.onnx") -Destination (Join-Path $Models "stt")
Copy-Item -LiteralPath (Join-Path $Work "$SttDir\tokens.txt") -Destination (Join-Path $Models "stt")
Copy-Item -LiteralPath (Join-Path $Work "$TtsDir\ko_KO-kss_low.onnx") -Destination (Join-Path $Models "tts")
Copy-Item -LiteralPath (Join-Path $Work "$TtsDir\tokens.txt") -Destination (Join-Path $Models "tts")
Copy-Item -Recurse -LiteralPath (Join-Path $Work "$TtsDir\espeak-ng-data") -Destination (Join-Path $Models "tts")
Remove-Item -Recurse -Force -LiteralPath $Work

foreach ($m in $ModelFiles) {
    if ((Sha256 (Join-Path $Models $m.Path)) -ne $m.Sha) { Fail "$($m.Path) 해시가 다릅니다" }
}
$FileCount = (Get-ChildItem -Recurse -File -LiteralPath $Models).Count
$SizeMb = [math]::Round(((Get-ChildItem -Recurse -File -LiteralPath $Models | Measure-Object -Sum Length).Sum / 1MB))
Write-Host "  $Models ($FileCount 개 파일, $SizeMb MB)"

if ($DownloadOnly) {
    Write-Host ""
    Write-Host "완료: 정리만 했습니다. 보드에 올리려면 -DownloadOnly 없이 다시 실행하세요."
    exit 0
}

# ---------------------------------------------------------------- 3. 보드에 올리기
Step "3/4 보드에 올리기"
$Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $Adb) {
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if ($sdk -and (Test-Path (Join-Path $sdk "platform-tools\adb.exe"))) { $Adb = Join-Path $sdk "platform-tools\adb.exe"; break }
    }
}
if (-not $Adb) { Fail "adb를 찾을 수 없습니다 (Android SDK platform-tools 설치 또는 Path 확인)" }

$SerialArgs = if ($Serial) { @("-s", $Serial) } else { @() }
function Adb { & $Adb @SerialArgs @args }

$state = (Adb get-state 2>$null | Out-String).Trim()
if ($state -ne "device") { Fail "기기가 연결되어 있지 않거나 여러 대입니다 (adb devices 확인, 여러 대면 -Serial <serial>)" }
if (-not ((Adb shell pm path $Pkg 2>$null | Out-String) -match "package:")) {
    Fail "앱이 설치되어 있지 않습니다. Android Studio에서 device 앱(debug)을 먼저 한 번 실행하세요"
}
Adb shell run-as $Pkg true 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "run-as가 안 됩니다. release가 아니라 debug 빌드로 설치하세요" }

$Tmp = "/data/local/tmp/senior-assistant-models"
Adb shell rm -rf $Tmp | Out-Null
Write-Host "  올리는 중 (무선이면 1~2분)..."
Adb push $Models $Tmp | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "보드로 올리지 못했습니다" }
Adb shell chmod -R a+rX $Tmp | Out-Null # 앱이 shell 소유 파일을 읽을 수 있게
Adb shell "run-as $Pkg sh -c 'rm -rf files/models.new && cp -r $Tmp files/models.new && rm -rf files/models && mv files/models.new files/models'"
if ($LASTEXITCODE -ne 0) { Fail "앱 폴더로 복사하지 못했습니다" }
Adb shell rm -rf $Tmp | Out-Null

$boardCount = (Adb shell "run-as $Pkg sh -c 'find files/models -type f | wc -l'" | Out-String).Trim()
if ($boardCount -ne "$FileCount") { Fail "보드의 파일 수($boardCount)가 이 컴퓨터($FileCount)와 다릅니다" }
$boardSha = ((Adb shell "run-as $Pkg sha256sum files/models/stt/model.int8.onnx" | Out-String).Trim() -split "\s+")[0]
if ($boardSha -ne $SttSha) { Fail "보드의 STT 모델 해시가 다릅니다" }
Write-Host "  파일 $boardCount 개, 해시 일치"

# ---------------------------------------------------------------- 4. 확인
Step "4/4 앱을 다시 켜서 확인"
Adb shell am force-stop $Pkg | Out-Null
Adb logcat -c | Out-Null
Adb shell monkey -p $Pkg -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
$log = ""
for ($i = 0; $i -lt 15 -and -not $log; $i++) {
    Start-Sleep -Seconds 1
    $log = (Adb logcat -d -s SeniorAssistantApp 2>$null | Select-String -Pattern "model files (ready|missing)" | Out-String).Trim()
}
Write-Host "  $(if ($log) { $log } else { '(로그 없음)' })"
if ($log -notmatch "model files ready") { Fail "앱이 모델을 찾지 못했습니다 (위 로그 확인)" }

Write-Host ""
Write-Host "완료: 모델을 올렸고 앱이 찾았습니다."
