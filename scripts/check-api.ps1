param([string]$BaseUrl = 'http://127.0.0.1:8000')

$ErrorActionPreference = 'Stop'
$url = $BaseUrl.TrimEnd('/')
$schema = Invoke-RestMethod "$url/openapi.json" -TimeoutSec 10
if (-not $schema.paths.'/alumnos') { throw 'OpenAPI no contiene /alumnos.' }
$salud = Invoke-RestMethod "$url/health" -TimeoutSec 45
if ($salud.reto -ne 'R01') { throw 'La API no se identifica como R01.' }
$response = Invoke-WebRequest "$url/alumnos" -TimeoutSec 15
$alumnos = ConvertFrom-Json -InputObject $response.Content -NoEnumerate
Write-Output "API R01 disponible: $url; estado: $($salud.status); alumnos centrales: $($alumnos.Count); pendientes: $($salud.pendientes_reintento)"
