# R01 - Punto de entrada unico en la raiz del proyecto.
#
# Uso:
#   .\run_demo.ps1                # infra + backend
#   .\run_demo.ps1 -SoloInfra     # solo contenedores
#   .\run_demo.ps1 -Puerto 8000   # puerto del backend

param(
    [switch]$SoloInfra,
    [switch]$SinReconstruir,
    [int]$Puerto = 8000
)

$ErrorActionPreference = "Stop"
$destino = Join-Path $PSScriptRoot "r01\scripts\run_demo.ps1"

if (-not (Test-Path $destino)) {
    throw "No se encuentra r01\scripts\run_demo.ps1"
}

& $destino -SoloInfra:$SoloInfra -SinReconstruir:$SinReconstruir -Puerto $Puerto
exit $LASTEXITCODE
