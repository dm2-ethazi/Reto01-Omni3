param([string]$BaseUrl = 'http://127.0.0.1:8000')

$ErrorActionPreference = 'Stop'
$url = $BaseUrl.TrimEnd('/')
$schema = Invoke-RestMethod "$url/openapi.json" -TimeoutSec 10
if (-not $schema.paths.'/alumnos') { throw 'OpenAPI no contiene /alumnos.' }
$response = Invoke-WebRequest "$url/alumnos" -TimeoutSec 15
$alumnos = ConvertFrom-Json -InputObject $response.Content -NoEnumerate
Write-Output "API disponible: $url; alumnos recibidos: $($alumnos.Count)"
