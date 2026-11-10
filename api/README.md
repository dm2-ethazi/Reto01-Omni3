# API Alumnos R01 · propuesta v6

La referencia funcional es la propuesta **v6**, confirmada por el equipo como la
versión aceptada. La aplicación principal es `proyecto_reto1.main:app`; el prototipo
no interviene. MariaDB conserva los alumnos nuevos y una vista de los históricos.
SQLite central es la réplica de lectura y aloja la cola persistente de refrescos;
es independiente de Room Android.

## Preparación reproducible

Requiere Python **3.11 o superior**, Docker Compose y Java 21+ para el puente JDBC
con Derby 10.17. JavaFX mantiene su requisito propio de Java 17+ y JavaFX 21.
Se han comprobado el backend con Python 3.11 y 3.14 y los cinco motores reales.

Desde la raíz, en PowerShell:

```powershell
Copy-Item .env.example .env
Copy-Item api/.env.example api/.env
# Cambiar contraseñas de ejemplo e igualarlas en ambos archivos.
# ORACLE_PASSWORD en api/.env corresponde a ORACLE_USER_PASSWORD en .env raíz.
docker compose up -d
py -3.14 -m venv api/.venv  # También sirve py -3.11.
& api/.venv/Scripts/python.exe -m pip install --upgrade pip
& api/.venv/Scripts/python.exe -m pip install -e ./api --group ./api/pyproject.toml:dev
& api/.venv/Scripts/python.exe scripts/seed-origins.py
& api/.venv/Scripts/python.exe -m uvicorn proyecto_reto1.main:app --host 127.0.0.1 --port 8000 --log-level info
```

En Linux se pueden usar `cp`, `python3.11 -m venv` y `api/.venv/bin/python`.
Las semillas `database/origins/*.sql` crean tablas compatibles con esta API y
añaden un alumno por fuente sin sobrescribir datos existentes. El script espera
hasta 180 segundos por conexión. MariaDB carga su esquema y catálogo desde
`database/mariadb/01_schema.sql` en volúmenes nuevos. Si el volumen es anterior,
aplicar las entradas del catálogo que falten; las columnas centrales se migran
automáticamente sin borrar alumnado.

El archivo `SQLITE_PATH` se resuelve respecto a `api/`, por defecto
`api/data/central.sqlite3`. Hay que conservarlo junto con los volúmenes Docker.
Todos los procesos de la misma API deben compartir este archivo. `.env`, los JAR
y SQLite se resuelven independientemente del directorio de ejecución.

Los puertos Compose son configurables con `MARIADB_PORT`, `ORACLE_PORT`, `H2_PORT`,
`H2_WEB_PORT`, `HSQLDB_PORT` y `DERBY_PORT`; ajustar también las URL de `api/.env`.
`api/.env.local.example` permite una prueba reducida de MariaDB, que no acredita
la lectura de las cuatro fuentes.

### Arranque conjunto de demo en Windows

Tras instalar `api/.venv` y compilar el JAR JavaFX para Windows, el script
`scripts/start-local.ps1` inicia las bases del proyecto `r01-adat-validation`,
prepara sus tablas/semillas, inicia Uvicorn, importa los datos y abre el cliente.
La [guía de la raíz](../README.md#demo-local-con-backend-y-javafx) incluye los comandos.
La plantilla `api/.env.demo.example` se copia a `api/data/validation.env` solo la
primera vez; este archivo privado reúne variables de Compose y de la API.
Se carga como variables de entorno, no se copia íntegro a `api/.env`, porque
contiene también claves exclusivas de Compose.

Los puertos de demo son MariaDB **33306**, Oracle **31521**, H2 **39092**
(web **38082**), HSQLDB **39001** y Derby **31527**. La SQLite usada es
`api/data/validation-final.sqlite3`. El script admite `-NoJava` para iniciar solo
las bases y el backend; `-Port` cambia el puerto HTTP y `-EnvFile` selecciona otra
configuración compartida. Los argumentos del script se pasan después de
`-File scripts/start-local.ps1`.

## Fuentes de verdad y flujo

- **Nuevo:** se inserta/edita en MariaDB, con Patronus obligatorio, DNI único y
  casa nula. La baja es lógica. Con MariaDB caída, sus escrituras responden 503.
- **Histórico:** se edita/baja síncronamente en Oracle, H2, HSQLDB o Derby. Se
  compara el estado previo completo para evitar sobrescribir cambios concurrentes.
  Si el origen está caído, se rechaza la escritura y no se programa para después.
- **Consulta:** usa MariaDB. Si falla, usa SQLite con `desde_cache=true` y
  `X-Data-Source: sqlite-cache`. Un fallo de origen no bloquea las consultas de su
  copia central disponible ni operaciones dirigidas a otros orígenes.
- **Importación:** `POST /sync` lee fuentes y registra refrescos de sus snapshots.
  `origenes` e `ids` permiten importar solo un caso. No hace falta una réplica total.

Antes de tocar una fuente, la API confirma en SQLite una reserva `PREPARED` con
capacidad disponible. Solo entonces ejecuta la escritura y relee su resultado de
la fuente dentro de la transacción. Si se confirma, la reserva pasa a `PENDING` y
se propaga a las copias. Si se rechaza, la reserva se cancela. La respuesta de
éxito contiene la ficha releída, incluidos cambios producidos por triggers.

El worker **nunca inserta, modifica ni elimina en las fuentes autoritativas**.
Solo replica escrituras confirmadas o datos leídos durante una sincronización.
Si se corta el proceso entre el commit remoto y la confirmación local, una reserva
abandonada se recupera leyendo su fuente: se propaga lo realmente confirmado o se
cancela si no ocurrió. Un commit de resultado desconocido devuelve 503 con un
mensaje específico; hay que consultar su estado antes de repetir.

## Cola, fallos y administración

- `REFRESH_QUEUE_MAX=5000`: máximo de tareas activas (`PREPARED` + `PENDING`).
  Al 80 % `/health` es degradado. Si está llena o no puede persistirse la reserva,
  la escritura se rechaza con 503 **antes de modificar la fuente**.
- Cada refresco tiene **cinco intentos automáticos** con espera progresiva. El
  quinto fallo lo deja en `FAILED`, almacenado y visible, sin borrado automático.
- `POST /admin/refresh-queue/{seq}/retry` relanza una tarea fallida y reinicia su
  contador. `POST /admin/retry` relanza las fallidas compatibles y fuerza las
  pendientes. El worker ordinario no resetea tareas `FAILED`.
- La consulta de cola no muestra payloads ni DNI. El seguimiento individual se
  expone en `GET /alumnos/{dni}/sync` y en las fichas: `sincronizacion` y
  `sincronizado_en` (UTC). Una ficha con refresco pendiente/fallido no admite otra
  escritura hasta completar o revisar ese refresco.
- `GET /admin/coherence` compara conteos y SHA-256 de los datos centrales,
  incluidos inactivos. Informa IDs ausentes o diferentes. Si alguna central cae,
  devuelve 503 e indica que no se pudo completar la comparación.

No hay transacción distribuida: pueden existir diferencias temporales entre
copias. Un histórico puede confirmarse en su origen con MariaDB caída, quedar
legible en SQLite y conservar pendiente su refresco a MariaDB. Un nuevo nunca se
crea solo en SQLite. Los refrescos son repetibles y no duplican registros.

La migración conserva las tareas de la implementación anterior sin ejecutarlas:
quedan en `FAILED` con `LAST_ERROR=LEGACY_REVIEW`. Eran órdenes de escritura y no
se pueden reinterpretar como refrescos confirmados; requieren revisión manual.
No deben borrarse bases o colas para ocultar estas diferencias.

## API y validación

OpenAPI `/openapi.json`, Swagger `/docs` y ReDoc `/redoc`.
La familia `/students` es alias de `/alumnos`, compatible con los clientes actuales.
El parámetro `lectura=sqlite` permite comprobar la copia aun con MariaDB disponible.
Conserva `origen` y devuelve `desde_cache=true`; no convierte el origen del alumno
en SQLite. Solo afecta a la consulta del listado, no cambia las reglas de escritura.

| Método | Ruta | Función |
|---|---|---|
| GET | `/health` | R01, estado de cada base, latencias, capacidad/ocupación y fallidas |
| GET | `/alumnos?nombre=Ce` | Activos y búsqueda por prefijo; MariaDB con fallback SQLite |
| GET | `/alumnos?lectura=sqlite` | Activos leídos de SQLite, con origen conservado y `X-Data-Source: sqlite-cache` |
| GET | `/alumnos/{dni}` | Ficha, metadatos de sincronización y ETag |
| POST | `/alumnos` | Alta en MariaDB; DNI, nombre y Patronus obligatorios |
| PUT | `/alumnos/{dni}` | Cambio parcial con `version` o `If-Match` |
| DELETE | `/alumnos/{dni}` | Baja con `If-Match` o `?version=` |
| GET | `/alumnos/{dni}/sync` | Estado individual de sincronización |
| GET | `/patronus` | Catálogo de MariaDB con caché persistente |
| GET | `/connectors` | Estado y cantidad de registros legibles de cada origen |
| GET | `/connectors/{origen}/alumnos` | Lectura de una fuente a través de la API |
| POST | `/sync` | Importación por lotes, selectiva o completa |
| GET | `/admin/refresh-queue` | Tareas activas y fallidas |
| POST | `/admin/refresh-queue/{seq}/retry` | Reintento administrativo individual |
| POST | `/admin/retry` | Reintento administrativo global |
| GET | `/admin/coherence` | Conteos, huellas y divergencias entre centrales |

Alta: DNI `^[0-9]{8}[A-Z]$`, normalizado a mayúsculas; nombre de 2–80 caracteres
tras recortar espacios; sexo H/M/S o nulo. Patronus de 2–60 caracteres, obligatorio
en nuevos y perteneciente al catálogo inglés tanto en POST como en PUT. Un
histórico puede no tener Patronus. PUT no permite modificar ID, casa, origen,
tipo, activo ni metadatos de sincronización.

Edición de ejemplo: `{"nombre":"Hermione","version":1}` o `If-Match: "1"`
con `{"nombre":"Hermione"}`. Una segunda edición obsoleta recibe 409. Sin
versión, PUT y DELETE devuelven 428. Los nuevos inactivos siguen reservando su DNI.
Las bajas históricas eliminan el origen y conservan la marca inactiva central.

Errores: 404 inexistente, 409 conflicto/duplicado, 422 datos inválidos, 428 versión
ausente, 503 dependencia requerida o reserva no disponible. Se conserva `detail`
y el envelope `error`, y cada respuesta incluye `request-id`. El servidor puede
generar ese identificador. Logs de negocio sin payloads, rutas parametrizadas y
accesos Uvicorn con DNI enmascarado. Unity tampoco imprime el DNI en su aviso de error.

## Pruebas y demostración

```powershell
& api/.venv/Scripts/python.exe -m pytest api/tests -q
# Solo sobre bases de demo; cerrar otra API durante el perfil de saturación.
$env:R01_INTEGRATION = '1'
& api/.venv/Scripts/python.exe -m pytest api/tests -q -p no:faulthandler
Remove-Item Env:R01_INTEGRATION
# Con Uvicorn arrancado:
& api/.venv/Scripts/python.exe scripts/verify-adat.py --compose-project r01-databases
```

La demo HTTP detiene/recupera H2 y MariaDB del proyecto indicado, comprueba los
rechazos, el fallback, cinco intentos automáticos reales, `FAILED`, divergencias
y relanzamiento administrativo. Puede tardar unos dos minutos. Conserva las bajas
de prueba, restaura el nombre histórico y no elimina volúmenes. Sin
`--compose-project` omite las caídas. Parámetros alternativos: `--base-url`,
`--env-file`, `--compose-env-file` y `--report`.

En Windows se usa `-p no:faulthandler` en la integración por el aviso conocido entre
pytest y JPype; no se desactivan aserciones. Ver [validación y límites](../docs/adat-validation.md).
