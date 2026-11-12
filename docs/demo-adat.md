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
$sync = Invoke-RestMethod "$base/sync" -Method Post -ContentType 'application/json' -Body $cuerpo
foreach ($origen in $casos.Keys) {
    $fuente = $sync.fuentes.$origen
    if ($fuente.estado -ne 'ok' -or $fuente.conflictos -ne 0) {
        throw "Importación incompleta o con conflictos en $origen."
    }
}
# Los refrescos pueden tardar; comprobar que las centrales convergen.
$limite = (Get-Date).AddSeconds(30)
do {
    $coherencia = Invoke-RestMethod "$base/admin/coherence"
    $cola = (Invoke-RestMethod "$base/health").cola
    if ($cola.fallidas -gt 0) { throw 'Hay refrescos fallidos.' }
    if ($cola.activas -eq 0 -and $coherencia.completo -and $coherencia.coinciden) { break }
    Start-Sleep -Seconds 1
} while ((Get-Date) -lt $limite)
if ($cola.activas -ne 0 -or !$coherencia.completo -or !$coherencia.coinciden) { throw 'Revisar cola y divergencias.' }
$copia = Invoke-RestMethod "$base/alumnos?lectura=sqlite"
foreach ($origen in $casos.Keys) {
    $dni = $casos[$origen]
    $central = Invoke-RestMethod "$base/alumnos/$dni"
    $sqlite = @($copia | Where-Object { $_.id -eq $dni })
    if ($sqlite.Count -ne 1 -or $central.desde_cache -ne $false -or $central.nombre -ne $leidos[$origen].nombre -or
        $sqlite[0].nombre -ne $central.nombre -or $sqlite[0].version -ne $central.version -or
        $central.origen -ne $origen -or $sqlite[0].origen -ne $origen) {
        throw "No coincide la ficha $dni de $origen."
    }
    Write-Host "OK $origen $dni : origen, MariaDB y SQLite coinciden"
}
```

Si el arranque ya importó los cuatro registros, `importados=0` es válido. Cada origen debe responder `estado=ok` y `conflictos=0`.

Esperado: cuatro líneas `OK`, `GET /health` con `reto=R01`, HTTP 200 para lecturas/importación y coherencia completa con `coinciden=true`. `POST /sync` admite importar solo estos cuatro casos; que el arranque haya importado todos no es un requisito del pliego. La consulta posterior utiliza las centrales, no vuelve a leer la fuente.

## 2. Alta, edición, validación, concurrencia y baja lógica

El bloque anterior comprueba los cuatro DNI de la tabla. El script siguiente selecciona la primera ficha que devuelve cada conector para verificar la importación y después prueba el alta, la edición y la baja. El README y la wiki utilizan el mismo script.

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

En una instalación de demo sin escrituras en curso, conserva abierta una terminal PowerShell en la raíz. Ejecuta este bloque para guardar la versión de Aamir y la huella de ambas centrales antes del reinicio.

```powershell
$base = 'http://127.0.0.1:8000'
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
function Leer-Persistencia {
    $salud = Invoke-RestMethod "$base/health" -ErrorAction Stop
    if ($salud.instalacion -ne $local.installation_id -or $salud.status -ne 'ok' -or
        $salud.cola.activas -ne 0 -or $salud.cola.fallidas -ne 0) {
        throw 'Comprueba la instalación, los motores y la cola antes de comparar.'
    }
    $ficha = Invoke-RestMethod "$base/alumnos/10000000Z" -ErrorAction Stop
    $c = Invoke-RestMethod "$base/admin/coherence" -ErrorAction Stop
    if (!$c.completo -or !$c.coinciden) { throw 'Las centrales no coinciden.' }
    [pscustomobject]@{ DNI=$ficha.id; Version=$ficha.version; MariaDB=$c.centrales.mariadb.huella; SQLite=$c.centrales.sqlite.huella }
}
$antes = Leer-Persistencia
$antes | Format-List
```

Detén el proceso Uvicorn de esta instalación. Si lo abrió el script, comprueba que el PID de `api/data/local-api.pid` corresponde a ese proceso antes de detenerlo. En una **segunda terminal** en la raíz, reinicia los contenedores con sus volúmenes y lanza únicamente la API con su configuración existente.

```powershell
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
docker compose --env-file $local.env_file -p $local.compose_project restart
if ($LASTEXITCODE -ne 0) { throw 'Falló el reinicio de los motores.' }
& ./api/.venv/Scripts/python.exe -m uvicorn proyecto_reto1.main:app --env-file $local.env_file --host 127.0.0.1 --port 8000
```

Deja Uvicorn abierto. Cuando `/health` vuelva a indicar `ok`, ejecuta lo siguiente en la **primera terminal**, antes de sembrar o importar. Guarda las dos salidas como evidencia.

```powershell
$despues = Leer-Persistencia
$despues | Format-List
if (Compare-Object $antes $despues -Property DNI,Version,MariaDB,SQLite) {
    throw 'Han cambiado la ficha o las huellas centrales tras el reinicio.'
}
Write-Host 'OK: mismo DNI, versión y huellas en MariaDB y SQLite tras el reinicio.'
```

## 4. Caídas y reintentos: solo instalación de demostración

Este comando **detiene y recupera H2 y MariaDB** y modifica temporalmente un histórico. No ejecutarlo sobre una API compartida en uso:

```powershell
$local = Get-Content api/data/local-environment.json -Raw | ConvertFrom-Json
& api/.venv/Scripts/python.exe scripts/verify-adat.py --env-file $local.env_file --compose-env-file $local.env_file --compose-project $local.compose_project --report api/data/demo-adat-fallos.json
if ($LASTEXITCODE -ne 0) { throw 'Revisar el informe de caídas.' }
```

Esperado: origen caído rechaza edición con 503 y no la aplica después; MariaDB caída permite lectura SQLite identificada como caché, pero rechaza escrituras de nuevos. Un cambio histórico confirmado conserva el refresco pendiente, agota cinco intentos y llega a `FAILED`; al recuperar MariaDB y relanzarlo, las huellas vuelven a coincidir. Al completar el recorrido, el script restaura el nombre original del histórico. Si la prueba falla después de editarlo y antes de restaurarlo, el nombre de prueba puede quedar guardado. En ambos casos, el bloque final intenta arrancar los servicios que aún estén detenidos.

## 5. Evidencia de API y de clientes

```powershell
& api/.venv/Scripts/python.exe -m pytest api/tests -q -p no:faulthandler
```

Revisión del **12/11/2026**: **55 passed, 8 skipped**. Los ocho casos de integración real requieren `R01_INTEGRATION=1` y configuración efectiva. Seguir el [perfil de integración del README API](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/api/README.md#pruebas-y-demostración), con otra API detenida para no competir con su prueba de saturación. Los casos omitidos no cuentan como aprobados.

En esta revisión se ejecutaron también las **18 comprobaciones HTTP con las cuatro fuentes reales** y acceso directo a ambas centrales. [Informe versionado](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/evidence/wiki-demo-adat-2026-11-12.json). No se repitieron las caídas ni el reinicio; sus instrucciones y expectativas están separadas de la evidencia obtenida hoy. El [informe ADAT histórico](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/adat-validation.md) conserva las comprobaciones anteriores con su contexto.

El equipo confirma las pruebas manuales de JavaFX, Android y Unity. El [registro manual](https://github.com/dm2-ethazi/Reto01-Omni3/blob/main/docs/manual-validation.md) y la [galería de la wiki](https://github.com/dm2-ethazi/Reto01-Omni3/wiki/Capturas-de-validacion) muestran la preparación de los cuatro orígenes, el estado R01 en JavaFX, a Aamir Loonat (`10000000Z`) en JavaFX y Android y la ficha de Luna con el Patronus Hare en Unity. La release 2.0.1 de JavaFX cuenta además con 103 pruebas automáticas del cliente.

Para repetir el recorrido entre clientes, crea un alumno con `Otter` en JavaFX y anota su DNI y nombre. Actualiza la lista en Android y busca por DNI. En Unity busca por nombre y selecciona la ficha. Cambia el nombre en JavaFX, vuelve a consultar el mismo DNI en Android y busca el nombre actualizado en Unity. Tras la baja, actualiza ambos clientes y comprueba que el alumno desaparece de los resultados.
