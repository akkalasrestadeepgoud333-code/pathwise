<#
.SYNOPSIS
  One-time setup for Pathwise on Windows. Safe to re-run.

.DESCRIPTION
  1. Checks for a JDK 21+ (required) and Git (recommended).
  2. Builds every module with the Maven Wrapper (Maven itself is downloaded automatically, free).
     The first build also downloads the embedded PostgreSQL binaries (~30 MB) from Maven Central.
  3. Creates config\pathwise.local.properties with a random secret (only if missing).
  4. Starts the embedded database once and applies all migrations.
  5. Optionally creates demo accounts (-Demo), with random passwords written to config\demo-accounts.txt.

.EXAMPLE
  .\scripts\setup.ps1          # build + database
  .\scripts\setup.ps1 -Demo    # also create demo accounts
  .\scripts\setup.ps1 -RunTests
#>
param(
    [switch]$Demo,
    [switch]$RunTests
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }

Step 'Checking Java'
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host 'No Java found. Install a free JDK 21 or newer, e.g.: winget install EclipseAdoptium.Temurin.21.JDK' -ForegroundColor Red
    exit 1
}
# java prints its version on stderr; under 'Stop' Windows PowerShell would treat that as an error.
$ErrorActionPreference = 'Continue'
$versionLine = (& java -version 2>&1 | Select-Object -First 1 | ForEach-Object { "$_" })
$ErrorActionPreference = 'Stop'
if ($versionLine -match '"(\d+)') { $major = [int]$Matches[1] } else { $major = 0 }
if ($major -lt 21) {
    Write-Host "Java $major found; Pathwise needs 21 or newer." -ForegroundColor Red
    exit 1
}
Write-Host "Java $major OK"

Step 'Checking Git'
if (Get-Command git -ErrorAction SilentlyContinue) { Write-Host 'Git OK' }
elseif (Test-Path 'C:\Program Files\Git\cmd\git.exe') { Write-Host 'Git is installed (open a new terminal to put it on PATH).' }
else { Write-Host 'Git not found (recommended): winget install Git.Git' -ForegroundColor Yellow }

Step 'Building Pathwise (first run downloads dependencies; this can take a few minutes)'
$mvnArgs = @('-q', 'install')
if (-not $RunTests) { $mvnArgs += '-DskipTests' }
& "$root\mvnw.cmd" @mvnArgs
if ($LASTEXITCODE -ne 0) { Write-Host 'Build failed. See the messages above.' -ForegroundColor Red; exit $LASTEXITCODE }

$jar = Join-Path $root 'pathwise-app\target\pathwise-app.jar'

Step 'Preparing configuration and database'
& java -jar $jar migrate
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($Demo) {
    Step 'Creating demo accounts'
    & java -jar $jar seed-demo
}

Step 'Environment report'
& java -jar $jar doctor

Write-Host "`nSetup complete. Start Pathwise with:  .\scripts\dev.ps1" -ForegroundColor Green
Write-Host 'Create your administrator with:       java -jar pathwise-app\target\pathwise-app.jar create-admin'
