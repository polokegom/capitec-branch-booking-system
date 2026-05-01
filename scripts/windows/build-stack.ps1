param(
  [switch]$Install,
  [switch]$SkipTests,
  [switch]$Docker,
  [switch]$Start
)

$ErrorActionPreference = 'Stop'

$scriptPath = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$rootPath = Split-Path -Parent (Split-Path -Parent $scriptPath)
$frontendPath = Join-Path $rootPath 'bank-appointment'
$backendPath = Join-Path $rootPath 'bank-appointment-backend'

function Invoke-Step {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Name,
    [Parameter(Mandatory = $true)]
    [scriptblock]$Command
  )

  Write-Host ""
  Write-Host "==> $Name" -ForegroundColor Cyan
  & $Command
}

function Invoke-CommandInPath {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [Parameter(Mandatory = $true)]
    [string]$Command,
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  Push-Location $Path
  try {
    & $Command @Arguments
    $exitCode = $LASTEXITCODE
    if ($null -ne $exitCode -and $exitCode -ne 0) {
      throw "'$Command $($Arguments -join ' ')' failed with exit code $exitCode."
    }
  } finally {
    Pop-Location
  }
}

function Test-CommandAvailable {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Command
  )

  if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
    throw "Required command '$Command' was not found on PATH."
  }
}

Test-CommandAvailable 'npm.cmd'
Test-CommandAvailable 'mvn.cmd'

if ($Docker -or $Start) {
  Test-CommandAvailable 'docker'
}

if ($Install) {
  Invoke-Step 'Install frontend dependencies' {
    Invoke-CommandInPath $frontendPath 'npm.cmd' @('ci')
  }
}

if (-not $SkipTests) {
  Invoke-Step 'Run frontend Jest tests' {
    Invoke-CommandInPath $frontendPath 'npm.cmd' @('test')
  }
}

Invoke-Step 'Build frontend' {
  Invoke-CommandInPath $frontendPath 'npm.cmd' @('run', 'build')
}

$backendBuildArguments = if ($SkipTests) {
  @('-B', 'clean', 'package', '-DskipTests')
} else {
  @('-B', 'clean', 'verify')
}

Invoke-Step 'Build backend' {
  Invoke-CommandInPath $backendPath 'mvn.cmd' $backendBuildArguments
}

if ($Start) {
  Invoke-Step 'Build and start Docker stack' {
    Invoke-CommandInPath $backendPath 'docker' @('compose', 'up', '-d', '--build')
  }
} elseif ($Docker) {
  Invoke-Step 'Build Docker images' {
    Invoke-CommandInPath $backendPath 'docker' @('compose', 'build')
  }
}

Write-Host ""
Write-Host 'Stack build completed successfully.' -ForegroundColor Green
