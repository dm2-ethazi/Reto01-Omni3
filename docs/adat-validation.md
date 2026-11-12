# Validación ADAT y propuesta v6 · 09/11/2026

El equipo confirmó que **Omni3_Propuesta_R01_v6.pdf** fue la versión aceptada.
La corrección del 05/11 cerró carencias del feedback, pero había comportamientos
que no coincidían con esa propuesta. Esta revisión los sustituye; los resultados
anteriores no constituyen evidencia de aceptación de la v6.

## Comportamiento aplicado

| Requisito | Implementación y comprobación |
|---|---|
| Históricos autoritativos en cuatro orígenes | Edición/baja síncrona con comparación de estado; copia central por refresco. |
| Nuevos autoritativos en MariaDB | Altas/ediciones/bajas en MariaDB con versión, Patronus obligatorio y baja lógica. |
| MariaDB caída | Consultas SQLite identificadas como caché; altas y cambios de nuevos con 503 sin tarea de escritura diferida. |
| Origen histórico caído | Escritura 503, reserva cancelada y ninguna ejecución posterior al recuperar el origen. |
| Reserva antes del cambio | Cola persistida y capacidad comprobada antes de tocar la fuente; fallo de persistencia produce 503. |
| Cinco intentos | Espera progresiva y `FAILED` retenido al quinto fallo; reintento administrativo posterior. |
| Saturación | Máximo configurable 5.000, aviso al 80 % y rechazo antes de modificar fuentes. Perfil real de prueba con capacidad 5. |
| Coherencia | Conteos y huellas SHA-256, incluyendo inactivos; informe de ausentes/diferentes e indisponibilidad. |
| Seguimiento individual | Estado, última sincronización UTC e indicador de caché en ficha y endpoint individual. |
| Ficha releída | Lectura posterior al UPDATE/INSERT dentro de la transacción de la fuente; incluye modificaciones de triggers. |
| Recuperación tras commit incierto | Reserva `PREPARED` recuperada mediante lectura; el worker nunca repite el INSERT/UPDATE/DELETE de usuario. |
| API compatible | `/students` como alias de `/alumnos`; se mantienen las rutas usadas por los clientes. |
| Trazabilidad | Request-id por respuesta, logs de acceso enmascarados y retirada del DNI del aviso Unity. |

## Verificación

Se mantienen pruebas HTTP y transacciones SQLite reales para caídas, conflictos,
reservas, recuperación tras commit incierto, saturación, límites de reintento y
coherencia. Los tests que antes aceptaban altas en SQLite con MariaDB caída o
ejecutaban más tarde una escritura rechazada han sido reemplazados.

Entorno real: proyecto Compose aislado `r01-adat-validation`, con MariaDB 11.8,
Oracle Free 23, H2 2.5.252, HSQLDB 2.7.4 y Derby 10.17.1.0. Puertos propios;
el contenedor MariaDB que ya existía en el equipo no se altera. La integración y
la API del mismo entorno comparten `SQLITE_PATH`. Ejecutar estos perfiles solo
con datos de demostración: conservan registros de prueba inactivos.

Las pruebas se ejecutan tanto con Python 3.14 como con Python 3.11 para comprobar
el mínimo declarado en la propuesta. Los informes locales se conservan bajo
`api/data/` y no se versionan. El guion HTTP genera `validation-v6-report.json`
y el registro Uvicorn identifica cada solicitud sin exponer DNI completos.

Resultado de esta revisión: **43 pruebas correctas por cada versión de Python**
(36 de API/persistencia y 7 de integración real), y **33 comprobaciones HTTP**
correctas en la demo con caídas y recuperación de contenedores.

Después se corrigió el selector **SQLite** de JavaFX: antes buscaba alumnos cuyo
origen fuese `SQLITE`, por lo que ocultaba copias con origen MariaDB o histórico.
Ahora solicita `/alumnos?lectura=sqlite` y conserva el origen autoritativo. Se
comprobó la respuesta real `X-Data-Source: sqlite-cache`, con los cinco alumnos
activos disponibles entonces, y la coincidencia de ambas centrales. Tras añadir
esta regresión pasan **38 pruebas de API/persistencia** (dos más que en el perfil
anterior) y **102 pruebas Java**. Las siete pruebas de integración real y las 33
comprobaciones con caídas corresponden al perfil anterior descrito arriba.

## Repetición

Consultar [api/README.md](../api/README.md) para preparar esquemas y semillas.
No basta con iniciar contenedores: ejecutar `scripts/seed-origins.py` y `POST /sync`.
Las operaciones funcionales se realizan por HTTP; la demo abre las centrales
adicionalmente para comprobar la evidencia de persistencia.

```powershell
& api/.venv/Scripts/python.exe -m pytest api/tests -q
$env:R01_INTEGRATION = '1'
& api/.venv/Scripts/python.exe -m pytest api/tests -q -p no:faulthandler
Remove-Item Env:R01_INTEGRATION
& api/.venv/Scripts/python.exe scripts/verify-adat.py --compose-project r01-databases
```

El último comando incluye una caída real del origen H2 y de MariaDB. La API
debe estar arrancada; la demo espera que el worker agote los cinco intentos,
comprueba `FAILED`, recupera MariaDB, detecta divergencia con igual conteo y
distinta huella y relanza el refresco administrativamente hasta converger.
El perfil de saturación usa bases reales y un worker pausado dentro del test;
debe ejecutarse sin otro servidor consumiendo su cola.

## Límites que siguen requiriendo demostración

Se abrió JavaFX en Windows contra la API local y el equipo comprobó el alta de un
alumno; esa prueba reveló el filtro SQLite corregido. No se ha completado una
sesión manual de todas las operaciones JavaFX, Unity ni de los dos dispositivos
Android con esta revisión. La propuesta incluye pruebas en Redmi Note 11 y
Redmi Pad SE, y la consulta Unity de un histórico y un nuevo. Los resultados
históricos de dispositivos no acreditan automáticamente esta versión corregida.
Los nombres del catálogo de ejemplo son válidos pero las URL de imágenes están
vacías; para demostrar imágenes de Patronus hay que cargar los recursos previstos.

La sincronización sigue siendo asíncrona: una escritura confirmada puede tardar
en aparecer en todas las copias. Eso se informa por registro y en administración;
no se promete una transacción distribuida. Mientras un refresco esté pendiente o
fallido, la API bloquea nuevas escrituras de esa ficha para no ocultar cambios.

Las tareas heredadas de la implementación anterior se preservan como `FAILED`
con `LEGACY_REVIEW` y no se ejecutan ni se relanzan automáticamente. Requieren
revisión porque podían representar órdenes de escritura que no fueron confirmadas.
Los datos y volúmenes deben conservarse: borrarlos no es un procedimiento de recuperación.

## Actualización 12/11/2026: datos históricos y arranque entre clones

Las cuatro fuentes se preparan con los CSV suministrados por el equipo, incluidos
sin modificar en `database/origins/data/`: 303 Gryffindor/Derby, 212 Hufflepuff/H2,
203 Ravenclaw/Oracle y 267 Slytherin/HSQLDB. La carga inicial conservaba sus **985 UUID únicos**.
La actualización al RAR descrita al final sustituye esos ID por los DNI aportados
y utiliza sus nombres, apellidos y Patronus; los CSV conservan los metadatos restantes. Los
esquemas y las centrales aceptan ID de hasta 64 caracteres. Las altas nuevas
siguen exigiendo DNI y Patronus. La API normaliza UUID también en las rutas y en
la sincronización selectiva para admitir clientes que los envíen en mayúsculas.

La carga no sobrescribe UUID existentes ni sus ediciones. Retira los cuatro
ejemplos anteriores solo si permanecen intactos; el arranque refleja sus bajas
en la central y espera a que termine el refresco de los nuevos históricos.
Se comprobó la carga repetida sin inserciones, la consulta de los 985 desde cada
central y la igualdad de sus huellas.

Los clones usan ahora una configuración compartida en
`%LOCALAPPDATA%/Omni3/R01/local.env` y la misma `central.sqlite3`. En la primera
migración se verifican las claves anteriores y se copia SQLite incluyendo la
cola mediante su API de backup. Se conserva el original. El seeder informa del
progreso y falla inmediatamente ante claves rechazadas. El arranque comprueba
la identidad de la API existente y la coherencia de las centrales antes de abrir
JavaFX. Se probó otro directorio de clon con claves privadas diferentes: reutilizó
la misma configuración, archivo SQLite e identidad de instalación.

Resultado: **56 pruebas correctas**, incluidas **8 de integración real**. El perfil
verifica también que repetir el seeder mantiene una edición de un UUID y sus
metadatos CSV. El arranque completo se repitió sobre la misma API sin regenerar
credenciales ni volver a insertar históricos.

Los informes de esta revisión quedan bajo `api/data/`; los archivos privados y
las bases no se versionan. El entorno JDK 17 señalado en la revisión anterior
sigue pendiente: este cambio se limita al seeder y al arranque compartido.

## Asignación de Patronus con el catálogo suministrado

El ZIP `patronus-20261112T094824Z-1-001.zip` aporta 143 tipos y 138 URL de imagen;
las cinco entradas restantes carecen de URL en el archivo original. Sus datos se
normalizan en `database/origins/data/patronus.json`, con procedencia y hash del ZIP.
No se ejecutan sus scripts de descarga. El seeder asigna nombres ingleses mediante
SHA-256 del UUID y solo completa valores nulos o vacíos. Conserva nombres, imágenes
del alumno, metadatos CSV y Patronus ya informados. Esta asignación es de semilla,
no una afirmación sobre el Patronus canónico de los personajes.

Se completa el catálogo de datos `PATRONUS_IMG` existente y se refresca su copia
SQLite durante el arranque. No se modifican los esquemas de las tablas para esta
operación. Los cambios se propagan a MariaDB y SQLite mediante la sincronización
habitual de la API.

Validación con los motores reales: 61 pruebas correctas; 985 históricos con
Patronus tanto en sus fuentes como en MariaDB y SQLite, y 143 entradas de catálogo
en ambas centrales. La comparación de columnas antes y después confirma que las
tablas conservan su estructura. Repetir el seeder no inserta ni cambia registros
que ya estén completos; `/admin/coherence` confirma que ambas centrales coinciden
y `/health` no muestra refrescos pendientes ni fallidos.


## Actualización de datos: alumnos del RAR con DNI

`database/origins/data/alumnos.json` normaliza exclusivamente los INSERT de datos
incluidos en `BBDD_con_Imagenes.rar`; no se ejecutan sus scripts ni sus CREATE TABLE.
Contiene 985 DNI únicos con letra de control correcta, nombres y apellidos
separados, sexo e imágenes. Se conservan sus 75 Patronus informados y los 910
vacíos se completan con el catálogo anterior. Las diferencias de mayúsculas se
normalizan al nombre existente del catálogo; los nueve nombres adicionales del
RAR amplían los datos de `PATRONUS_IMG` hasta 152 entradas, sin alterar tablas.

La migración cambia las claves UUID de las fuentes por los DNI suministrados.
Actualiza nombres/apellidos y Patronus de la semilla anterior cuando conservan su
valor de semilla, respetando ediciones particulares. Ante una colisión de claves,
una edición concurrente o refrescos UUID pendientes se rechaza la migración de
forma controlada. Se puede repetir tras resolver la causa. El arranque desactiva
las copias antiguas con UUID por la API y sincroniza las fichas con DNI; las copias
inactivas se conservan y quedan excluidas de las consultas habituales.

Por indicación del equipo no se repitió toda la batería de pruebas. Se ejecutaron
las 16 comprobaciones específicas de preparación y seeder, incluida la migración,
la carga repetida y el rechazo de colisiones sin sobrescribir datos.

La actualización local conserva 985 fichas activas con DNI y Patronus en las
cuatro fuentes y en cada central. Los seis campos del RAR coinciden con la
semilla cargada; las 985 copias UUID anteriores quedan inactivas. La comparación
de columnas confirma que no cambió la estructura de las tablas. El catálogo
tiene 152 entradas, ambas centrales coinciden y no quedan refrescos pendientes
ni fallidos. Evidencia local: `api/data/validation-dni-rar.json`.
