param(
    [string]$Python = 'python',
    [string]$Platform = 'android-35',
    [string]$BuildTools = '36.0.0'
)
$ErrorActionPreference = 'Stop'
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to your JDK 17 or newer directory.' }
$env:ANDROID_PLATFORM = $Platform
$env:ANDROID_BUILD_TOOLS = $BuildTools
Push-Location $PSScriptRoot
try {
    & $Python tests/run_java.py
    if ($LASTEXITCODE -ne 0) { throw 'Java tests failed' }
    & $Python build.py
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
} finally { Pop-Location }
