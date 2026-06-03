Write-Host "Stopping The Circle services (excluding Docker)..." -ForegroundColor Cyan

# Resolve paths relative to this script so it works from any directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..") -ErrorAction Stop

# 1. Close PowerShell windows spawned by the start script
Write-Host "Closing service terminal windows..." -ForegroundColor Yellow

# Buscamos procesos de PowerShell que estén ejecutando maven o npm
$psProcesses = Get-CimInstance Win32_Process -Filter "name='powershell.exe'" |
    Where-Object { $_.CommandLine -match "mvn spring-boot:run" -or $_.CommandLine -match "npm run dev" }

foreach ($p in $psProcesses) {
    try {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
        Write-Host "Closed terminal for process $($p.ProcessId)" -ForegroundColor DarkGray
    } catch {
        # Ignorar si ya se había cerrado manualmente
    }
}

# 2. Clean up orphaned processes (Java and Node) bound to this repository
Write-Host "Cleaning up lingering Java and Node processes..." -ForegroundColor Yellow

# Escapamos la ruta del repo para usarla en un Regex y evitar falsos positivos con otros proyectos
$repoRootEscaped = [Regex]::Escape($RepoRoot).Replace('\', '\\')

$lingeringProcesses = Get-CimInstance Win32_Process |
    Where-Object {
        ($_.Name -match "^java\.exe$" -or $_.Name -match "^node\.exe$") -and
        $_.CommandLine -match $repoRootEscaped
    }

foreach ($lp in $lingeringProcesses) {
    try {
        Stop-Process -Id $lp.ProcessId -Force -ErrorAction Stop
        Write-Host "Killed orphaned $($lp.Name) (PID: $($lp.ProcessId))" -ForegroundColor DarkGray
    } catch {
        # Ignorar errores menores
    }
}

Write-Host "Services stopped! (Docker containers are still running)" -ForegroundColor Green