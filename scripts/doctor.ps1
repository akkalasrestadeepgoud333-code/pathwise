<#
.SYNOPSIS
  Checks this machine for everything Pathwise needs (required) or can use (optional). Changes nothing.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$jar = Join-Path $root 'pathwise-app\target\pathwise-app.jar'
if (-not (Test-Path $jar)) {
    Write-Host 'Pathwise is not built yet. Run .\scripts\setup.ps1 first.' -ForegroundColor Yellow
    exit 1
}
& java -jar $jar doctor
exit $LASTEXITCODE
