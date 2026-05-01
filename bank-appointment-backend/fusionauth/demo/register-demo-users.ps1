#requires -Version 5.1
param(
  [string]$FusionAuthUrl = "http://localhost:9011",
  [string]$ApiKey = "bf69486b-4733-4470-a592-f1bfce7af580",
  [string]$ApplicationId = "85a03867-dccf-4882-adde-1a79aeec50df",
  [string]$Password = "password123",
  [string]$UsersFile = (Join-Path $PSScriptRoot "demo-users.json")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $UsersFile)) {
  throw "Cannot find users file: $UsersFile"
}

$users = Get-Content $UsersFile -Raw | ConvertFrom-Json
$headers = @{ Authorization = $ApiKey; "Content-Type" = "application/json" }

Write-Host "Registering $($users.Count) demo users at $FusionAuthUrl ..." -ForegroundColor Cyan
$created = 0; $skipped = 0; $failed = 0

foreach ($u in $users) {
  $body = @{
    skipVerification = $true
    skipRegistrationVerification = $true
    sendSetPasswordEmail = $false
    user = @{
      email             = $u.email
      firstName         = $u.firstName
      lastName          = $u.lastName
      password          = $Password
      verified          = $true
      passwordChangeRequired = $false
    }
    registration = @{
      applicationId = $ApplicationId
      roles         = $u.roles
      verified      = $true
    }
  } | ConvertTo-Json -Depth 6

  try {
    $null = Invoke-RestMethod -Method Post `
      -Uri "$FusionAuthUrl/api/user/registration" `
      -Headers $headers -Body $body
    $created++
    Write-Host ("  + {0,-32} {1}" -f $u.email, ($u.roles -join ",")) -ForegroundColor Green
  }
  catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 409 -or $status -eq 400) {
      try {
        $existing = Invoke-RestMethod -Method Get `
          -Uri "$FusionAuthUrl/api/user?email=$([uri]::EscapeDataString($u.email))" `
          -Headers $headers
        $userId = $existing.user.id
        if (-not $userId) { throw "User id not found in lookup response" }

        $patch = @{
          user = @{
            verified = $true
            password = $Password
            passwordChangeRequired = $false
          }
        } | ConvertTo-Json -Depth 5
        $null = Invoke-RestMethod -Method Patch `
          -Uri "$FusionAuthUrl/api/user/$userId" `
          -Headers $headers -Body $patch

        $regBody = @{
          skipVerification = $true
          skipRegistrationVerification = $true
          registration = @{
            applicationId = $ApplicationId
            roles         = $u.roles
            verified      = $true
          }
        } | ConvertTo-Json -Depth 5
        try {
          $null = Invoke-RestMethod -Method Post `
            -Uri "$FusionAuthUrl/api/user/registration/$userId" `
            -Headers $headers -Body $regBody
        }
        catch {
          $regStatus = $_.Exception.Response.StatusCode.value__
          if ($regStatus -ne 409 -and $regStatus -ne 400) { throw }
          $regPatch = @{
            registration = @{
              applicationId = $ApplicationId
              roles         = $u.roles
              verified      = $true
            }
          } | ConvertTo-Json -Depth 5
          $null = Invoke-RestMethod -Method Patch `
            -Uri "$FusionAuthUrl/api/user/registration/$userId/$ApplicationId" `
            -Headers $headers -Body $regPatch
        }

        $skipped++
        Write-Host ("  ~ {0,-32} repaired (verified, password reset)" -f $u.email) -ForegroundColor Yellow
      }
      catch {
        $failed++
        Write-Host ("  ! {0,-32} repair failed: {1}" -f $u.email, $_.Exception.Message) -ForegroundColor Red
      }
    }
    else {
      $failed++
      Write-Host ("  ! {0,-32} HTTP {1}: {2}" -f $u.email, $status, $_.Exception.Message) -ForegroundColor Red
    }
  }
}

Write-Host ""
Write-Host ("Done. created={0} skipped={1} failed={2}" -f $created, $skipped, $failed) `
  -ForegroundColor Cyan
Write-Host ("All passwords: {0}" -f $Password) -ForegroundColor DarkGray
