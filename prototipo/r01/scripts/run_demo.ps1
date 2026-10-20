# R01 - Arranque completo en Windows (Docker + backend FastAPI)
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\run_demo.ps1           # infra + backend
#   powershell -ExecutionPolicy Bypass -File scripts\run_demo.ps1 -SoloInfra # sólo contenedores
#
# Requisitos: Docker Desktop en ejecución, Python 3.11+ con el entorno
# virtual de backend/ ya creado (python -m venv .venv && pip install -r requirements.txt).

param(
    [switch]$SoloInfra,
    [switch]$SinReconstruir,
    [int]$Puerto = 8000
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$infra = Join-Path $raiz "infra"
$backend = Join-Path $raiz "backend"

function Esperar-Salud {
    param([string]$Url, [int]$Intentos = 60)
    for ($i = 1; $i -le $Intentos; $i++) {
        try {
            $respuesta = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($respuesta.StatusCode -eq 200) { return $true }
        } catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

Write-Host "== R01: levantando la infraestructura (MariaDB, SQLite, Derby, HSQLDB, H2, Oracle) =="
docker compose -f (Join-Path $infra "docker-compose.yml") up -d $(if ($SinReconstruir) { @() } else { @("--build") })
if ($LASTEXITCODE -ne 0) { throw "docker compose ha fallado" }

$contenedores = @("mariadb", "derby", "hsqldb", "h2", "oracle")
foreach ($servicio in $contenedores) {
    # Derby/HSQLDB/H2 no definen healthcheck: la plantilla debe tolerarlo
    # (si no, .State.Health no existe y PowerShell corta el script).
    try {
        $estado = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}sin-healthcheck{{end}}' "r01-$servicio" 2>$null
    } catch {
        $estado = "no-creado"
    }
    if (-not $estado) { $estado = "no-creado" }
    Write-Host ("  {0,-8} {1}" -f $servicio, $estado)
}

if ($SoloInfra) { Write-Host "== Sólo infraestructura solicitada =="; exit 0 }

if (-not (Test-Path (Join-Path $backend ".env"))) {
    Write-Host "== Creando backend\.env a partir del ejemplo =="
    Copy-Item (Join-Path $backend ".env.example") (Join-Path $backend ".env")
}

$python = Join-Path $backend ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { $python = "python" }

$logSalida = Join-Path $env:TEMP "r01-backend.log"
$logError = Join-Path $env:TEMP "r01-backend.err.log"
$enMarcha = Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($enMarcha) {
    Write-Host "== R01: el puerto $Puerto ya esta en uso; se reutiliza el backend en marcha =="
} else {
    Write-Host "== R01: arrancando el backend en el puerto $Puerto =="
    Start-Process -FilePath $python -ArgumentList "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", $Puerto `
        -WorkingDirectory $backend -RedirectStandardOutput $logSalida -RedirectStandardError $logError -WindowStyle Hidden
}

if (-not (Esperar-Salud -Url "http://127.0.0.1:$Puerto/health")) {
    Write-Host "== El backend no ha respondido; últimas líneas del log =="
    Get-Content $logError -Tail 30 -ErrorAction SilentlyContinue
    exit 1
}

$salud = (Invoke-WebRequest -Uri "http://127.0.0.1:$Puerto/health" -UseBasicParsing).Content | ConvertFrom-Json
Write-Host ("== Estado: {0} | conectores caídos: {1} ==" -f $salud.status, (($salud.caidos) -join ", "))
Write-Host "== API:  http://127.0.0.1:$Puerto/docs =="
Write-Host "== Log:  $logSalida =="
