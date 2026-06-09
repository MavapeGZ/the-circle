Write-Host "Starting The Circle environment..." -ForegroundColor Cyan

# Resolve paths relative to this script so it works from any directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..") -ErrorAction Stop

# Load .env variables from repository rootd
$EnvFile = Join-Path $RepoRoot ".env"
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match "^\s*([^#][^=]*)=(.*)$") {
            [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
        }
    }
    Write-Host ".env loaded successfully" -ForegroundColor Green
} else {
    Write-Host ".env not found in $RepoRoot" -ForegroundColor Red
}

# 2. Start Docker infrastructure (from repo root)
Write-Host "Starting Docker containers..." -ForegroundColor Yellow

docker-compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error starting Docker" -ForegroundColor Red
    exit 1
}

do {
    Start-Sleep -Seconds 5

    try {
        $response = Invoke-WebRequest `
            -Uri "http://localhost:9200" `
            -UseBasicParsing `
            -ErrorAction Stop

        $ready = $true
    }
    catch {
        $ready = $false
    }

} while (-not $ready)

# Helper to start a service in a new PowerShell window
function Start-ServiceWindow($workDir, $command, $title) {
    Write-Host "Starting $title..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit","-Command","$command" -WorkingDirectory $workDir -WindowStyle Normal
}

# API Gateway
Start-ServiceWindow (Join-Path $RepoRoot "backend/api-gateway") "mvn spring-boot:run" "API Gateway"

# ms-users
Start-ServiceWindow (Join-Path $RepoRoot "backend/ms-users") "mvn spring-boot:run" "ms-users"

# ms-catalog
Start-ServiceWindow (Join-Path $RepoRoot "backend/ms-catalog") "mvn spring-boot:run" "ms-catalog"

# ms-contracts
Start-ServiceWindow (Join-Path $RepoRoot "backend/ms-contracts") "mvn spring-boot:run" "ms-contracts"

# ms-gamification
Start-ServiceWindow (Join-Path $RepoRoot "backend/ms-gamification") "mvn spring-boot:run" "ms-gamification"

# ms-notifications
Start-ServiceWindow (Join-Path $RepoRoot "backend/ms-notifications") "mvn spring-boot:run" "ms-notifications"

# Frontend
Start-ServiceWindow (Join-Path $RepoRoot "frontend") "npm run dev" "Frontend (Vite)"

Write-Host "Everything is up and running" -ForegroundColor Green