# Builds the Pathwise Android app (debug APK) and runs its unit tests.
# Gradle 8.7 can't run on the system JDK 25, so this uses Android Studio's bundled JDK for this process only.
#   .\scripts\build-apk.ps1            build
#   .\scripts\build-apk.ps1 -Install   build and install on the running emulator/device (adb)
param([switch]$Install)
$ErrorActionPreference = 'Stop'

$jbr = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path "$jbr\bin\java.exe")) { throw "Android Studio's JDK not found at $jbr. Install Android Studio (free)." }
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
if (-not (Test-Path "$sdk\platforms\android-34")) { throw "Android SDK platform 34 not found under $sdk. Install it from Android Studio's SDK Manager." }

$env:JAVA_HOME = $jbr
$android = Join-Path $PSScriptRoot '..\android'
# local.properties is machine-specific and not committed.
"sdk.dir=$($sdk -replace '\\', '\\\\' -replace ':', '\:')" | Set-Content -Encoding ASCII (Join-Path $android 'local.properties')

Push-Location $android
try {
    .\gradlew.bat assembleDebug testDebugUnitTest --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $apk = Resolve-Path 'app\build\outputs\apk\debug\app-debug.apk'
    Write-Host "`nAPK: $apk"
    if ($Install) {
        & "$sdk\platform-tools\adb.exe" install -r $apk
    }
} finally {
    Pop-Location
}
