# MirrorCast - Windows Receiver Build Script
param(
    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Building MirrorCast Windows Receiver ($Configuration) " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir
$slnPath = Join-Path $rootDir "windows\MirrorCast.Receiver.sln"

Write-Host "Restoring dependencies..." -ForegroundColor Yellow
dotnet restore $slnPath

Write-Host "Building solution..." -ForegroundColor Yellow
dotnet build $slnPath -c $Configuration --no-restore

Write-Host "Executing unit tests..." -ForegroundColor Yellow
dotnet test $slnPath -c $Configuration --no-build --verbosity normal

Write-Host "`n✅ MirrorCast Windows Receiver build succeeded!" -ForegroundColor Green
Write-Host "Executable output directory: windows\MirrorCast.Receiver\bin\$Configuration\net9.0-windows\" -ForegroundColor Green
