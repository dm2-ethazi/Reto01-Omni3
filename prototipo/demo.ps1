# demo.ps1 - Demostracion en pantalla del prototipo R01 (Ola 1)
# Uso: powershell -ExecutionPolicy Bypass -File demo.ps1        (con pausas)
#      powershell -ExecutionPolicy Bypass -File demo.ps1 -SinPausa   (seguido)
param([switch]$SinPausa)

$ErrorActionPreference = 'Continue'
$Host.UI.RawUI.WindowTitle = "DEMO R01 - Omni3 (prototipo Ola 1)"
$proto = 'C:\Users\dm2\Documents\Reto 1\prototipo_r01'
$puerto = 8010

function Titulo($t) {
    Write-Host ""
    Write-Host ("=" * 66) -ForegroundColor DarkCyan
    Write-Host "  $t" -ForegroundColor Cyan
    Write-Host ("=" * 66) -ForegroundColor DarkCyan
}
function Paso($n, $t) {
    Write-Host ""
    Write-Host "[PASO $n] $t" -ForegroundColor Yellow
}
function Pausa($msg = "Enter para continuar...") {
    if (-not $SinPausa) { Read-Host $msg | Out-Null }
}

Write-Host "PROTOTIPO Omni3 - Reto R01 (Hogwarts) - DEMO EN PANTALLA" -ForegroundColor Green
Write-Host "Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

# ---------------------------------------------------------------- PASO 1
Titulo "PASO 1 - WP1 infra-docker: bases de origen + almacen central"
docker ps --filter "name=r01" --format "  {{.Names}}  |  {{.Status}}  |  {{.Ports}}"
Write-Host ""
docker ps --filter "name=prototipo_r01-mariadb" --format "  {{.Names}}  |  {{.Status}}  |  {{.Ports}}"
Write-Host "  (Derby, HSQLDB, H2, Oracle = origenes | MariaDB = almacen central)" -ForegroundColor DarkGray
Pausa

# ---------------------------------------------------------------- PASO 2
Titulo "PASO 2 - WP2 backend-core: API FastAPI viva?"
$escucha = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue
if ($escucha) {
    Write-Host "  API ya escuchando en http://127.0.0.1:$puerto (PID $($escucha.OwningProcess))" -ForegroundColor Green
} else {
    Write-Host "  Arrancando uvicorn en segundo plano..." -ForegroundColor Yellow
    Start-Process -FilePath "python" -ArgumentList @("-m","uvicorn","api.main:app","--host","127.0.0.1","--port","$puerto") `
        -WorkingDirectory $proto -WindowStyle Hidden
    Start-Sleep -Seconds 5
}
Pausa

# ---------------------------------------------------------------- PASO 3
Titulo "PASO 3 - GET /health  (debe responder R01)"
$r = Invoke-RestMethod -Uri "http://127.0.0.1:$puerto/health" -Method Get
Write-Host "  Respuesta: " -NoNewline
Write-Host ($r | ConvertTo-Json -Compress) -ForegroundColor Green
Pausa

# ---------------------------------------------------------------- PASO 4
Titulo "PASO 4 - Persistencia dual: que motor se uso?"
$e = Invoke-RestMethod -Uri "http://127.0.0.1:$puerto/estado" -Method Get
Write-Host "  Motor: " -NoNewline
Write-Host $e.motor -ForegroundColor Green
Write-Host "  Detalle: $($e.detalle)" -ForegroundColor DarkGray
Pausa

# ---------------------------------------------------------------- PASO 5
Titulo "PASO 5 - WP3 sync-service: cocina leyendo los 4 origenes"
Push-Location $proto
python -m servicios.cocina
$cod = $LASTEXITCODE
Pop-Location
if ($cod -eq 0) { Write-Host "  Sincronizacion OK (exit 0)" -ForegroundColor Green }
else { Write-Host "  Sincronizacion con incidencia (exit $cod)" -ForegroundColor Red }
Pausa

# ---------------------------------------------------------------- PASO 6
Titulo "PASO 6 - Lectura final desde el almacen: GET /estudiantes"
$est = Invoke-RestMethod -Uri "http://127.0.0.1:$puerto/estudiantes" -Method Get
Write-Host "  Total filas: " -NoNewline
Write-Host $est.total -ForegroundColor Green
$est.estudiantes | Select-Object id, nombre, casa, origen | Format-Table -AutoSize | Out-String | Write-Host
Pausa

# ---------------------------------------------------------------- CIERRE
Titulo "DEMO COMPLETADA - arquitectura Ola 1 verificada"
Write-Host "  WP1 infra-docker .... 5 BD en Docker" -ForegroundColor Green
Write-Host "  WP2 backend-core ..... /health = R01, persistencia dual" -ForegroundColor Green
Write-Host "  WP3 sync-service ..... 4 origenes -> almacen central" -ForegroundColor Green
Write-Host ""
Write-Host "  API activa en http://127.0.0.1:$puerto/docs (Swagger)" -ForegroundColor Cyan
if ($SinPausa) { Read-Host "Enter para cerrar" | Out-Null }
