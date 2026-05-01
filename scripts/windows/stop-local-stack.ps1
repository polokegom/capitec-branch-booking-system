$ErrorActionPreference = 'Stop'

$scriptPath = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$rootPath = Split-Path -Parent (Split-Path -Parent $scriptPath)
$backendPath = Join-Path $rootPath 'bank-appointment-backend'

if (-not (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
  throw "Required command 'docker' was not found on PATH."
}

Push-Location $backendPath
try {
  Write-Host ""
  Write-Host "==> Stopping the local stack" -ForegroundColor Cyan
  docker compose down
  if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed with exit code $LASTEXITCODE."
  }
} finally {
  Pop-Location
}

Write-Host ""
Write-Host 'Local stack has been stopped.' -ForegroundColor Green
