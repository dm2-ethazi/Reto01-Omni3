# Demo ADAT: origen → API → dos centrales → consulta

Ejecuta desde la raíz con la [instalación principal preparada](https://github.com/dm2-ethazi/Reto01-Omni3/wiki/06-Ejecucion-del-Proyecto). Este es el recorrido común enlazado por el README y la wiki. Los datos siguientes proceden de los archivos versionados; no son los cuatro ejemplos antiguos eliminados por el seeder.

| Origen | DNI | Nombre / apellido | Patronus de semilla |
|---|---|---|---|
| Oracle | 10000515T | Alannis | Tawny Owl |
| H2 | 10000303H | 1980s / Hogwarts Gobstones Tournament champion | Polar Bear |
| HSQLDB | 10000718L | Abigail / Pugh | Black Mare |
| Derby | 10000000Z | Aamir / Loonat | Mastiff |

En una instalación usada pueden haberse editado esos registros. El seeder conserva esas ediciones: no reinicies los datos para que coincidan artificialmente con la tabla.

## 1. Leer cada fuente, importar un caso y consultar las copias

Pega en PowerShell desde la raíz. El bloque compara las fichas reales con sus copias centrales y falla si falta una:

```powershell
$base = 'http://127.0.0.1:8000'
$casos = [ordered]@{oracle='10000515T'; h2='10000303H'; hsqldb='10000718L'; derby='10000000Z'}
$estado = Invoke-RestMethod "$base/health"
if ($estado.reto -ne 'R01' -or $estado.status -ne 'ok') { throw 'Dependencias no disponibles.' }
$leidos = @{}
foreach ($origen in $casos.Keys) {
    $filas = Invoke-RestMethod "$base/connectors/$origen/alumnos"
    $fila = @($filas | Where-Object { $_.id -eq $casos[$origen] })
    if ($fila.Count -ne 1) { throw "No se encuentra el caso de $origen." }
    $leidos[$origen] = $fila[0]
}
$cuerpo = @{ids=@($casos.Values)} | ConvertTo-Json
Invoke-RestMethod "$base/sync" -Method Post -ContentType 'application/json' -Body $cuerpo
# Los refrescos pueden tardar; comprobar que las centrales convergen.
$limite = (Get-Date).AddSeconds(30)
do {
    $coherencia = Invoke-RestMethod "$base/admin/coherence"
    if ($coherencia.completo -and $coherencia.coinciden) { break }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $limite)
if (!$coherencia.completo -or !$coherencia.coinciden) { throw 'Revisar cola y divergencias.' }
$copia = Invoke-RestMethod "$base/alumnos?lectura=sqlite"
foreach ($origen in $casos.Keys) {
    $dni = $casos[$origen]
    $central = Invoke-RestMethod "$base/alumnos/$dni"
    $sqlite = @($copia | Where-Object { $_.id -eq $dni })
    if ($sqlite.Count -ne 1 -or $central.nombre -ne $leidos[$origen].nombre -or
        $sqlite[0].nombre -ne $central.nombre -or $sqlite[0].version -ne $central.version -or
        $central.origen -ne $origen -or $sqlite[0].origen -ne $origen) {
        throw "No coincide la ficha $dni de $origen."
    }
    Write-Host "OK $origen $dni : origen, MariaDB y SQLite coinciden"
}
```

Esperado: cuatro líneas `OK`, `GET /health` con `reto=R01`, HTTP 200 para lecturas/importación y coherencia completa con `coinciden=true`. `POST /sync` admite importar solo estos cuatro casos; que el arranque haya importado todos no es un requisito del pliego. La consulta posterior utiliza las centrales, no vuelve a leer la fuente.

## 2. Alta, edición, validación, concurrencia y baja lógica

El mismo script de demo se usa desde el README y la wiki:

```powershell
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
& api/.venv/Scripts/python.exe scripts/verify-adat.py --env-file $local.env_file --report api/data/demo-adat.json
if ($LASTEXITCODE -ne 0) { throw 'La demo no ha terminado correctamente.' }
Get-Content api/data/demo-adat.json
```

Usa **`Otter`** del catálogo, nombre `Demo ADAT` y un DNI generado para evitar colisiones con demos anteriores. Comprueba datos directamente en ambas centrales además de HTTP. Al terminar conserva la baja lógica del alumno de prueba.

| Caso | Resultado esperado |
|---|---|
| Lectura de las cuatro fuentes e importación selectiva | 200 y fichas en las dos centrales. |
| Alta sin Patronus | 422. |
| Alta con `Otter` | 201 y registro persistido. |
| PUT con Patronus inexistente | 422. |
| PUT con versión vigente | 200. |
| PUT con la versión anterior | 409 sin sobrescribir. |
| Baja del nuevo con versión vigente | 204; consulta habitual 404. |
| Inspección central posterior | Registro conservado con `activo=false` y misma versión en MariaDB/SQLite. |

Esperado: informe con `resultado: correcto` y **18 comprobaciones** sin activar las caídas. Si falta `Otter` en `/patronus`, revisar la preparación del catálogo: no sustituirlo por otro nombre inventado.

## 3. Persistencia tras reinicio

Conserva uno de los DNI históricos de la tabla y su ficha. Reinicia los motores manteniendo los volúmenes:

```powershell
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
docker compose --env-file $local.env_file -p $local.compose_project restart
```

Detén únicamente el proceso Uvicorn de esta instalación (si se arrancó con el script, su PID se guarda en `api/data/local-api.pid`; comprueba el proceso antes de detenerlo). Vuelve a iniciar con `start-local.ps1 -NoJava`, espera `/health` y repite el apartado 1. Esperado: mismo DNI, contenido y versión, con ambas centrales coherentes. No borres SQLite ni uses `down -v`. Registrar la salida antes y después; la mera existencia de volúmenes no sustituye esta comprobación.

## 4. Caídas y reintentos: solo instalación de demostración

Este comando **detiene y recupera H2 y MariaDB** y modifica temporalmente un histórico. No ejecutarlo sobre una API compartida en uso:

```powershell
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
& api/.venv/Scripts/python.exe scripts/verify-adat.py --env-file $local.env_file --compose-env-file $local.env_file --compose-project $local.compose_project --report api/data/demo-adat-fallos.json
if ($LASTEXITCODE -ne 0) { throw 'Revisar el informe de caídas.' }
```

Esperado: origen caído rechaza edición con 503 y no la aplica después; MariaDB caída permite lectura SQLite identificada como caché, pero rechaza escrituras de nuevos. Un cambio histórico confirmado conserva el refresco pendiente, agota cinco intentos y llega a `FAILED`; al recuperar MariaDB y relanzarlo, las huellas vuelven a coincidir. El script restaura el nombre histórico y arranca los servicios que haya detenido en su bloque de recuperación.

## 5. Evidencia de API y de clientes

```powershell
& api/.venv/Scripts/python.exe -m pytest api/tests -q -p no:faulthandler
```

Revisión del **12/11/2026**: **55 passed, 8 skipped**. Los ocho casos de integración real requieren `R01_INTEGRATION=1` y configuración efectiva. Seguir el [perfil de integración del README API](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/api/README.md#pruebas-y-demostración), con otra API detenida para no competir con su prueba de saturación. Los casos omitidos no cuentan como aprobados.

En esta revisión se ejecutaron también las **18 comprobaciones HTTP con las cuatro fuentes reales** y acceso directo a ambas centrales. [Informe versionado](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/evidence/wiki-demo-adat-2026-11-12.json). No se repitieron las caídas ni el reinicio; sus instrucciones y expectativas están separadas de la evidencia obtenida hoy. El [informe ADAT histórico](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/adat-validation.md) conserva las comprobaciones anteriores con su contexto.

Para demostrar el conjunto gráfico: consultar Alannis y Aamir en JavaFX, Android y Unity; crear un nuevo con `Otter` en JavaFX, registrar su DNI y buscarlo en los otros clientes; editarlo, refrescar y finalmente darlo de baja. Guardar versión, URL utilizada, dispositivo y resultado en el [registro manual](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/manual-validation.md). Las 103 pruebas JavaFX de la release 2.0.1 no sustituyen esa sesión ni las pruebas de la API.
