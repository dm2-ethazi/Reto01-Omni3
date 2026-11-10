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
