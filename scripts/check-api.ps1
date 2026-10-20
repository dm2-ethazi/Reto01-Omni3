param([string]$BaseUrl = 'http://127.0.0.1:8000')

$ErrorActionPreference = 'Stop'
$url = $BaseUrl.TrimEnd('/')
$schema = Invoke-RestMethod "$url/openapi.json" -TimeoutSec 10
if (-not $schema.paths.'/alumnos') { throw 'OpenAPI no contiene /alumnos.' }
$alumnos = @(Invoke-RestMethod "$url/alumnos" -TimeoutSec 15)
Write-Output "API disponible: $url; alumnos recibidos: $($alumnos.Count)"
