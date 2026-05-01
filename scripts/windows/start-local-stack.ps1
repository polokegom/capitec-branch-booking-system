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
  Write-Host "==> Building images and starting the local stack" -ForegroundColor Cyan
  docker compose up -d --build
  if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
  }

  Write-Host ""
  docker compose ps
} finally {
  Pop-Location
}

Write-Host ""
Write-Host 'Local stack is running.' -ForegroundColor Green
Write-Host 'Frontend:   http://localhost:4200'
Write-Host 'Backend:    http://localhost:8080/q/health'
Write-Host 'FusionAuth: http://localhost:9011'
Write-Host 'MailHog:    http://localhost:8025'
