# MirrorCast - Android Sender Build Script
param(
    [string]$Task = "assembleDebug"
)

$ErrorActionPreference = "Stop"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Building MirrorCast Android Sender ($Task) " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$androidDir = Join-Path $rootDir "android"

# Detect JDK 21 / 17
$jdkCandidates = @(
    "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot",
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Java\jdk-17",
    $env:JAVA_HOME
)

$jdkPath = $null
foreach ($c in $jdkCandidates) {
    if (![string]::IsNullOrEmpty($c) -and (Test-Path (Join-Path $c "bin\java.exe"))) {
        $jdkPath = $c
        break
    }
}

if ($null -eq $jdkPath) {
    Write-Error "Could not locate a compatible JDK (17 or 21). Please install JDK 17 or 21."
    exit 1
}

$env:JAVA_HOME = $jdkPath
Write-Host "Using JDK: $env:JAVA_HOME" -ForegroundColor Green

# Detect Android SDK
$sdkCandidates = @(
    "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk",
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT
)

$sdkPath = $null
foreach ($s in $sdkCandidates) {
    if (![string]::IsNullOrEmpty($s) -and (Test-Path $s)) {
        $sdkPath = $s
        break
    }
}

if ($null -ne $sdkPath) {
    $env:ANDROID_HOME = $sdkPath
    Write-Host "Using Android SDK: $env:ANDROID_HOME" -ForegroundColor Green
}

# Locate Gradle distribution or wrapper
$gradleBin = "C:\Users\$env:USERNAME\.gradle\wrapper\dists\gradle-9.2.0-bin\11i5gvueggl8a5cioxuftxrik\gradle-9.2.0\bin\gradle.bat"
if (!(Test-Path $gradleBin)) {
    $gradleBin = "gradle"
}

Push-Location $androidDir
try {
    Write-Host "Running Gradle: $Task" -ForegroundColor Yellow
    & $gradleBin $Task
    Write-Host "`n✅ Android build succeeded!" -ForegroundColor Green
    Write-Host "APK location: android\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
}
finally {
    Pop-Location
}
