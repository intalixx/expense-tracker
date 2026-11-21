# Build the debug APK, start an emulator if no device is connected, install and launch the app.
# Usage: .\run_app.ps1                 (build + install + launch)
#        .\run_app.ps1 -NoBuild        (install the last built APK + launch)
#        .\run_app.ps1 -Avd My_Device  (use another emulator; default Pixel_2)
param([switch]$NoBuild, [string]$Avd = 'Pixel_2')
$ErrorActionPreference = 'Stop'

# JAVA_HOME must point to the JDK folder, not its \bin.
if ($env:JAVA_HOME -and -not (Test-Path "$env:JAVA_HOME\bin\java.exe") -and (Test-Path "$env:JAVA_HOME\java.exe")) {
    $env:JAVA_HOME = Split-Path $env:JAVA_HOME -Parent
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $adb)) { throw "adb not found at $adb. Install platform-tools or set ANDROID_HOME." }

# Start the emulator unless a device is already connected.
& $adb start-server | Out-Null
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) {
    if (-not (Test-Path $emulator)) { throw 'No device connected and the emulator is not installed.' }
    Write-Host "Starting emulator $Avd..."
    Start-Process $emulator -ArgumentList '-avd', $Avd
}

if (-not $NoBuild) {
    Write-Host 'Building...'
    Push-Location $PSScriptRoot
    try {
        if (Get-Command gradle -ErrorAction SilentlyContinue) { gradle :app:assembleDebug -q }
        else { & (Join-Path $PSScriptRoot 'run_gradle.ps1') :app:assembleDebug -q }
    } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
}

Write-Host 'Waiting for the device to boot...'
& $adb wait-for-device
while ((& $adb shell getprop sys.boot_completed 2>$null) -notmatch '1') { Start-Sleep -Seconds 2 }

Write-Host 'Installing...'
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed.' }
& $adb shell monkey -p com.intalix.expense.tracker -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host 'App launched.'
