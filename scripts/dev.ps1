<#
.SYNOPSIS
  Runs Pathwise locally at http://localhost:8080 (embedded Tomcat + embedded PostgreSQL).

.DESCRIPTION
  JSP, CSS and JavaScript changes apply on browser refresh. Java changes need -Build.

.EXAMPLE
  .\scripts\dev.ps1           # start (builds first if needed)
  .\scripts\dev.ps1 -Build    # rebuild Java code, then start
  .\scripts\dev.ps1 -Port 8081
#>
param(
    [switch]$Build,
    [int]$Port = 0
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$jar = Join-Path $root 'pathwise-app\target\pathwise-app.jar'
if ($Build -or -not (Test-Path $jar)) {
    Write-Host 'Building (tests skipped; run .\mvnw.cmd verify for the full suite)...' -ForegroundColor Cyan
    & "$root\mvnw.cmd" -q install -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$serveArgs = @('-jar', $jar, 'serve')
if ($Port -gt 0) { $serveArgs += @('--port', "$Port") }
& java @serveArgs
