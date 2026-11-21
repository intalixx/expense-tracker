# PowerShell equivalent of run_gradle.sh (Windows). Usage: .\run_gradle.ps1 :app:assembleDebug --offline -q
$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$gradle = Get-ChildItem -Path (Join-Path $HOME '.gradle\wrapper\dists\gradle-9.3.1-bin\*\gradle-9.3.1\bin\gradle.bat') |
    Select-Object -First 1
if (-not $gradle) { throw 'Gradle 9.3.1 not found under ~/.gradle/wrapper/dists. Install Gradle or open the project once in Android Studio.' }

& $gradle.FullName @args
exit $LASTEXITCODE
