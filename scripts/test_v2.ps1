<#
.SYNOPSIS
    Pruebas manuales de los 6 requisitos del Reto R01 contra la API en marcha.

.DESCRIPTION
    Requiere:
      - MariaDB de Docker Compose levantada:  docker compose up -d mariadb
      - La API arrancada (local o en Docker): uv run uvicorn proyecto_reto1.main:app
                                             o docker compose --profile api up -d api
    Usa `curl.exe` (incluido en Windows 10+) para funcionar también en
    Windows PowerShell 5.1. Demuestra, en orden:
      1. GET /status con la referencia "R01"
      2. Rechazo de altas sin Patronus (422)
      3. Baja lógica que conserva el registro (verificado con SQL directo)
      4. Control de concurrencia optimista (428 sin versión, 409 con versión vieja)
      5. Lecturas desde el respaldo SQLite con MariaDB caído (X-Data-Source)
      6. Cola pending_sync + reintento tras recuperar MariaDB

.NOTES
    Las pruebas 5 y 6 paran y arrancan el contenedor r01-mariadb.
#>

param(
    [string]$Api = "http://127.0.0.1:8000",
    [string]$Contenedor = "r01-mariadb",
    [string]$MariaUser = "example",
    [string]$MariaPass = "example"
)

$ErrorActionPreference = "Continue"
$paso = 0

function Titulo([string]$texto) {
    $script:paso++
    Write-Host "`n=== [$script:paso] $texto ===" -ForegroundColor Cyan
}
function Ok([string]$texto)   { Write-Host "  OK    $texto" -ForegroundColor Green }
function Mal([string]$texto) { Write-Host "  FALLO $texto" -ForegroundColor Red }

function Llama([string]$Metodo, [string]$Url, [string]$Cuerpo, [hashtable]$Cabeceras) {
    <# Devuelve @{ Codigo; Cuerpo; Cabeceras } compatible con PowerShell 5.1. #>
    $peticion = @{ Uri = $Url; Method = $Metodo; UseBasicParsing = $true }
    if ($Cuerpo) {
        $peticion.Body = $Cuerpo
        $peticion.ContentType = "application/json"
    }
    if ($Cabeceras) { $peticion.Headers = $Cabeceras }
    try {
        $respuesta = Invoke-WebRequest @peticion -ErrorAction Stop
        $cab = ($respuesta.Headers.GetEnumerator() |
            ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
        return @{ Codigo = [int]$respuesta.StatusCode; Cuerpo = $respuesta.Content; Cabeceras = $cab }
    } catch {
        $respuesta = $_.Exception.Response
        if ($null -eq $respuesta) {
            return @{ Codigo = 0; Cuerpo = $_.Exception.Message; Cabeceras = "" }
        }
        $texto = ""
        try {
            $lector = New-Object System.IO.StreamReader($respuesta.GetResponseStream())
            $texto = $lector.ReadToEnd()
            $lector.Close()
        } catch {
            $texto = ""
        }
        if (-not $texto -and $null -ne $_.ErrorDetails) { $texto = $_.ErrorDetails.Message }
        $lineas = @()
        foreach ($clave in $respuesta.Headers.AllKeys) {
            $lineas += "${clave}: $($respuesta.Headers[$clave])"
        }
        return @{ Codigo = [int]$respuesta.StatusCode; Cuerpo = $texto; Cabeceras = ($lineas -join "`n") }
    }
}

function SqlMaria([string]$Consulta) {
    $salida = docker exec $Contenedor mariadb -B -N "-u$MariaUser" "-p$MariaPass" r01_central -e $Consulta 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Host "  (aviso SQL: $salida)" -ForegroundColor DarkYellow }
    $salida
}

function Limpia([string]$dni) { SqlMaria "DELETE FROM ALUMNOS WHERE ID = '$dni'" | Out-Null }

# DNIS únicos por ejecución: evitan colisiones con restos de pruebas anteriores.
$Base = "R01T" + (Get-Random -Maximum 10000).ToString("0000")
$Dni = $Base + "1"
$DniBis = $Base + "2"
$DniCola = $Base + "3"

# Limpieza inicial
Limpia $Dni; Limpia $DniBis; Limpia $DniCola

# --------------------------------------------------------------------------- #
Titulo "GET /status devuelve la referencia R01"
$r = Llama GET "$Api/status"
$status = $r.Cuerpo | ConvertFrom-Json
if ($status.reto -eq "R01") { Ok "reto = R01 (mariadb=$($status.mariadb), sqlite=$($status.sqlite), estado=$($status.estado))" }
else { Mal "reto = $($status.reto)" }

# --------------------------------------------------------------------------- #
Titulo "Alta sin Patronus rechazada (422)"
$r = Llama POST "$Api/alumnos" ('{"id":"' + $Dni + '","nombre":"SinPatronus","patronus":""}')
if ($r.Codigo -eq 422 -and $r.Cuerpo -match "patronus") { Ok "422 con mensaje: $($r.Cuerpo)" }
else { Mal "Código $($r.Codigo): $($r.Cuerpo)" }

# --------------------------------------------------------------------------- #
Titulo "Alta correcta con Patronus (201) y baja logica (204)"
$r = Llama POST "$Api/alumnos" ('{"id":"' + $Dni + '","nombre":"Test","apellido":"R01","sexo":"H","patronus":"Stag"}')
$alumno = $r.Cuerpo | ConvertFrom-Json
if ($r.Codigo -eq 201) { Ok "Creado con version=$($alumno.version), activo=$($alumno.activo)" }
else { Mal "Código $($r.Codigo): $($r.Cuerpo)" }

$r = Llama DELETE "$Api/alumnos/$Dni" $null @{ "If-Match" = "$($alumno.version)" }
if ($r.Codigo -eq 204) { Ok "DELETE con If-Match → 204" } else { Mal "DELETE devolvió $($r.Codigo): $($r.Cuerpo)" }

$r = Llama GET "$Api/alumnos/$Dni`?incluir_inactivos=true"
$trasBaja = $r.Cuerpo | ConvertFrom-Json
if ($r.Codigo -eq 200 -and $trasBaja.activo -eq $false -and $trasBaja.fecha_baja) {
    Ok "Borrado logico: activo=false, fecha_baja=$($trasBaja.fecha_baja), version=$($trasBaja.version)"
} else { Mal "No quedo marcado como inactivo: $($r.Cuerpo)" }

$sql = ((SqlMaria "SELECT ACTIVO, VERSION FROM ALUMNOS WHERE ID = '$Dni'" | Out-String) -replace "\s+", " ").Trim()
if ($sql -match "^0 ") { Ok "El registro SIGUE en la tabla (SQL directo): $sql" }
else { Mal "El registro desaparecio de la base: '$sql'" }

$r = Llama GET "$Api/alumnos/$Dni"
if ($r.Codigo -eq 404) { Ok "Sin incluir_inactivos no aparece (404)" } else { Mal "Devolvio $($r.Codigo)" }

# --------------------------------------------------------------------------- #
Titulo "Control de concurrencia: 428 sin version y 409 con version vieja"
$r = Llama POST "$Api/alumnos" ('{"id":"' + $DniBis + '","nombre":"Concurrencia","patronus":"Otter"}')
$ver = ($r.Cuerpo | ConvertFrom-Json).version

$r = Llama PUT "$Api/alumnos/$DniBis" '{"nombre":"SinVersion"}'
if ($r.Codigo -eq 428) { Ok "PUT sin version → 428 Precondition Required" } else { Mal "PUT sin version devolvio $($r.Codigo): $($r.Cuerpo)" }

$r = Llama PUT "$Api/alumnos/$DniBis" ('{"nombre":"Vieja","version":' + ($ver + 99) + '}')
if ($r.Codigo -eq 409) { Ok "PUT con version vieja → 409: $($r.Cuerpo)" } else { Mal "Devolvio $($r.Codigo): $($r.Cuerpo)" }

$r = Llama PUT "$Api/alumnos/$DniBis" ('{"nombre":"Nueva","version":' + $ver + '}')
$nuevo = $r.Cuerpo | ConvertFrom-Json
if ($r.Codigo -eq 200 -and $nuevo.version -eq ($ver + 1)) { Ok "PUT con version correcta → 200 y version=$($nuevo.version)" }
else { Mal "Version no avanzo: código $($r.Codigo), version=$($nuevo.version)" }

# --------------------------------------------------------------------------- #
Titulo "MariaDB caido: lecturas desde SQLite (X-Data-Source: sqlite)"
docker stop $Contenedor | Out-Null
Start-Sleep -Seconds 3

$r = Llama GET "$Api/alumnos/$DniBis`?incluir_inactivos=true"
if ($r.Cabeceras -match "X-Data-Source: sqlite") { Ok "Cabecera X-Data-Source: sqlite presente" }
else { Mal "No se marco el origen de respaldo: $($r.Cabeceras)" }
if ($r.Codigo -eq 200) { Ok "Alumno leido del respaldo con MariaDB caido" } else { Mal "Lectura devolvio $($r.Codigo)" }

$r = Llama GET "$Api/status"
$statusCaido = $r.Cuerpo | ConvertFrom-Json
if ($statusCaido.mariadb -eq "down" -and $statusCaido.sqlite -eq "up") {
    Ok "status: mariadb=down, sqlite=up, estado=$($statusCaido.estado), pendientes=$($statusCaido.operaciones_pendientes)"
} else { Mal "status inesperado: $($r.Cuerpo)" }

# --------------------------------------------------------------------------- #
Titulo "MariaDB caido: escritura encolada y sincronizada al recuperarse"
$r = Llama POST "$Api/alumnos" ('{"id":"' + $DniCola + '","nombre":"EnCola","patronus":"Wolf"}')
if ($r.Codigo -eq 201 -and $r.Cabeceras -match "X-Pending-Sync: true") {
    Ok "Alta 201 con X-Pending-Sync: true"
} else { Mal "Alta devolvio $($r.Codigo): $($r.Cuerpo) / $($r.Cabeceras)" }

$r = Llama GET "$Api/status"
$statusCola = $r.Cuerpo | ConvertFrom-Json
if ($statusCola.operaciones_pendientes -ge 1) { Ok "Hay $($statusCola.operaciones_pendientes) operaciones pendientes" }
else { Mal "No se reflejo la operacion en pending_sync" }

docker start $Contenedor | Out-Null
Write-Host "  ... esperando la recuperacion y los reintentos del worker ..."
$aplicado = $false
for ($i = 0; $i -lt 45 -and -not $aplicado; $i++) {
    Start-Sleep -Seconds 2
    $salida = (SqlMaria "SELECT ACTIVO FROM ALUMNOS WHERE ID = '$DniCola'" | Out-String).Trim()
    if ($salida -eq "1") { $aplicado = $true }
}
if ($aplicado) { Ok "La operacion encolada se aplico en MariaDB tras la recuperacion" }
else { Mal "La operacion pendiente no llego a aplicarse" }

for ($i = 0; $i -lt 30; $i++) {
    $r = Llama GET "$Api/status"
    $st = $r.Cuerpo | ConvertFrom-Json
    if ($st.operaciones_pendientes -eq 0) { break }
    Start-Sleep -Seconds 2
}
Ok "status final: pendientes=$($st.operaciones_pendientes), fallidas=$($st.operaciones_fallidas), estado=$($st.estado)"

# Limpieza final
Limpia $Dni; Limpia $DniBis; Limpia $DniCola
Write-Host "`n=== Pruebas completadas ===" -ForegroundColor Cyan
