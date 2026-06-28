Write-Host "Starting The Circle E2E run..." -ForegroundColor Cyan

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..") -ErrorAction Stop

function Test-HttpReady($Uri) {
    try {
        Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-ForReady($Name, $Uri, $TimeoutSeconds = 180) {
    Write-Host "Waiting for $Name at $Uri ..." -ForegroundColor Yellow
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpReady $Uri) {
            Write-Host "$Name is ready" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "$Name did not become ready within $TimeoutSeconds seconds"
}

try {
    & (Join-Path $ScriptDir "init_the_circle.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "The Circle stack failed to start"
    }

    Wait-ForReady "Frontend" "http://localhost:5173"
    Wait-ForReady "API Gateway" "http://localhost:8080/actuator/health"
    Wait-ForReady "ms-users" "http://localhost:8081/actuator/health"
    Wait-ForReady "ms-catalog" "http://localhost:8082/actuator/health"
    Wait-ForReady "ms-contracts" "http://localhost:8083/actuator/health"
    Wait-ForReady "ms-gamification" "http://localhost:8084/actuator/health"
    Wait-ForReady "ms-notifications" "http://localhost:8085/actuator/health"

    Push-Location (Join-Path $RepoRoot "e2e")
    try {
        & mvn clean test
        $testExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($testExit -ne 0) {
        exit $testExit
    }
} finally {
    & (Join-Path $ScriptDir "stop_the_circle.ps1") | Out-Host
}

Write-Host "E2E run finished successfully" -ForegroundColor Green