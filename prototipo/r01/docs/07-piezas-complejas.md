# 07 — Piezas complejas del backend (deep dive técnico)

**Versión:** 1.0 · **Fecha:** 2026-10-01
**Componente:** `r01/backend`

> **Trazabilidad:** anexo técnico del conjunto de manuales de `r01/docs`.
> Todas las citas `archivo:línea` se han tomado **leyendo el código fuente real**
> del repositorio. Cada sección responde a tres preguntas:
> **¿qué problema resuelve? · ¿cómo funciona? · ¿qué punto crítico hay que vigilar?**

---

## Índice

1. [Sincronización: `sync_service.py` + `transform.py`](#1-sincronización-sync_servicepy--transformpy)
2. [Central doble: `persistence/repositories/`](#2-central-doble-persistencerepositories)
3. [Acceso a datos: `data_access/` (JVM, dialectos, JDBC)](#3-acceso-a-datos-data_access-jvm-dialectos-jdbc)
4. [Núcleo transversal: `core/` + `api/`](#4-núcleo-transversal-core--api)
5. [Cuadro de vigilancia](#5-cuadro-de-vigilancia)

---

## 1. Sincronización: `sync_service.py` + `transform.py`

### 1.1 ¿Qué problema resuelve?

Consolidar cuatro orígenes heterogéneos (Derby, HSQLDB, H2, Oracle) en una
central doble **sin perder trazabilidad de procedencia**, **sin abortar por una
fila defectuosa** y **sin duplicar datos si el proceso se repite**.

| Problema | Solución implementada |
|----------|----------------------|
| Orígenes con datos «sucios» (espacios, minúsculas, DNI mal formado) | Normalización y validación centralizadas en `transform.py` (una sola vez, sin duplicar por conector). |
| Orígenes grandes que no caben en una respuesta | Paginación por `OFFSET/FETCH` con `Settings.sync_page_size`. |
| Filas inválidas que romperían todo el lote | Captura por fila: se cuenta el error y se continúa. |
| Reejecución del sync (doble clic, reintento de red) | Clave de negocio `(origen, id_origen)` + *upsert* idempotente. |
| Un backend de la central caído | Cola de reintentos persistente y resumen con pendientes. |

### 1.2 ¿Cómo funciona?

#### (a) Paginación de los orígenes

El bucle vive en `SyncService.sync()`:

```python
# app/services/sync_service.py:49-68
total = datasource.count()
offset = 0
while offset < max(total, 1):
    pagina = datasource.list(offset=offset, limit=self._page_size)
    if not pagina:
        break
    for raw in pagina:
        leidos += 1
        try:
            transformados.append(transform_row(raw, connector_id))
        except ValidacionError as exc:
            errores += 1
            detalle_errores.append({"id_origen": raw.get("id"), "motivo": exc.mensaje})
    offset += self._page_size
```

| Elemento | Valor / origen | Referencia |
|----------|----------------|------------|
| `page_size` | `Settings.sync_page_size` (por defecto 100) | `app/core/config.py:62`, `app/main.py:74-76` |
| Piso mínimo | `max(1, page_size)` (nunca 0 → bucle infinito) | `app/services/sync_service.py:28` |
| Total previo | `datasource.count()` | `app/services/sync_service.py:49` |
| SQL de página | `OFFSET n ROWS FETCH FIRST m ROWS ONLY` (SQL:2008) | `app/data_access/dialects.py:31-35` |
| Ensamblado del `SELECT` | `jdbc_connector.list()` con filtros parametrizados | `app/data_access/jdbc_connector.py:199-212` |

> El `page_size` declarado en `config/connectors.yaml` se carga en la
> especificación (`app/data_access/registry.py:121` → `app/data_access/base.py:37`)
> pero **hoy no lo consume ni el sync ni `list()`** (este último usa `limit=50`
> por defecto, `app/data_access/jdbc_connector.py:202`).

#### (b) Normalización y mapeo de filas (`transform.py`)

`transform_row(raw, origen)` (`app/services/transform.py:61-86`) convierte una
fila cruda del origen en una fila de la central:

| Paso | Regla | Referencia |
|------|-------|------------|
| 1 | Completitud: `id`, `dni`, `nombre`, `casa`, `patronus` deben existir (se acepta `0`) | `app/services/transform.py:67-72` |
| 2 | `id` convertible a `int` | `app/services/transform.py:74-77` |
| 3 | Espacios: `trim` + colapso de espacios internos (`" ".join(v.split())`) | `app/services/transform.py:26-28` |
| 4 | DNI/NIE en mayúsculas y validado por regex `^(?:[0-9]{8}[A-Z]\|[XYZ][0-9]{7}[A-Z])$` | `app/services/transform.py:19`, `41-49` |
| 5 | Longitudes: nombre 100, casa 30, patronus 50 (si no, `ValidacionError`) | `app/services/transform.py:21-23`, `52-58` |
| 6 | Trazabilidad: se añade el campo `origen` | `app/services/transform.py:79-81` |

Salida exacta (claves de `CLAVES_FILA`, `app/persistence/repositories/base.py:24`):

```json
{"origen": "derby", "id_origen": 1, "dni": "12345678Z",
 "nombre": "Alicia Torres", "casa": "Gryffindor", "patronus": "Leon"}
```

Los errores de transformación **no interrumpen** el bucle: se registran con
`logger.warning` y se acumulan en `detalle_errores`
(`app/services/sync_service.py:57-67`), que el resumen recorta a **20 entradas**
para no inflar la respuesta (`app/services/sync_service.py:135`).

#### (c) Idempotencia de la sincronización

La idempotencia se construye en **tres anillos**:

| Anillo | Mecanismo | Referencia |
|--------|-----------|------------|
| Modelo | `UniqueConstraint("origen", "id_origen")` → la misma fila del mismo origen sólo puede existir una vez | `app/persistence/models.py:24` |
| Escritura | *select-then-insert/update*: si `(origen, id_origen)` existe se **actualiza**, si no se **inserta** | `app/persistence/repositories/base.py:104-131` |
| Cola de reintentos | `UniqueConstraint("backend", "origen", "id_origen")`: marcar dos veces incrementa `intentos` en lugar de duplicar | `app/persistence/models.py:59`, `app/persistence/repositories/retry_queue.py:38-62` |

Consecuencia observable: un segundo `POST /sync/{id}` sobre datos sin cambios
devuelve `insertados = 0` y `actualizados = N` (verificado por
`tests/integration/test_api.py:100` y `tests/unit/test_persistence.py:55`).

#### (d) Resumen `SyncResumen`

Lo construye `_resumen()` (`app/services/sync_service.py:104-148`) y lo valida
el esquema `SyncResumen` (`app/api/schemas/salud.py:43-52`, con `extra="allow"`
para admitir los campos de detalle):

| Campo | Significado | Cálculo |
|-------|-------------|---------|
| `conector` | Identificador sincronizado | argumento |
| `leidos` | Filas leídas del origen (incluye las inválidas) | contador del bucle (`sync_service.py:56`) |
| `insertados` / `actualizados` | Cambios en la central | **máximo entre backends** (`sync_service.py:121-126`) para no doblar la cuenta |
| `errores` | Transformación **+** central | `errores_transformacion + len(upsert.errores)` (`sync_service.py:127`) |
| `errores_transformacion` | Sólo filas rechazadas por `transform_row` | contador |
| `detalle_errores` | Muestra de fallos (`id_origen` + motivo) | limitado a 20 (`sync_service.py:135`) |
| `central` | Desglose real **por backend** (mariadb / sqlite) | `upsert.por_backend` (`sync_service.py:136-143`) |
| `errores_central` | Mensajes de fallo de cada backend | `upsert.errores` (`composite.py:66`) |
| `pendientes_reintento` | Filas en la cola tras este sync | `upsert.pendientes_reintento` (`composite.py:68-70`) |
| `reintentos` | Resultado de `retry_pending()` previo | `sync_service.py:41`, `146` |
| `duracion_ms` | Duración total | `sync_service.py:38`, `73` |

`sync_all()` simplemente repite `sync()` por cada id del registro y **suma** los
totales (`app/services/sync_service.py:94-102`).

### 1.3 Punto crítico a vigilar

1. **Memoria proporcional al origen.** Todas las filas transformadas se
   acumulan en `transformados` (`sync_service.py:45`) y se envían al upsert en
   un único `self._central.upsert(transformados)` (`sync_service.py:71`): con
   orígenes muy grandes el consumo crece linealmente; no hay *streaming* por
   lotes.
2. **`count()` inicial + páginas consecutivas.** Si otro proceso inserta o borra
   filas entre `count()` y las páginas, el desplazamiento se desajusta
   (filas omitidas o repetidas). Hoy se asume un origen estable durante el sync.
3. **`max()` entre backends.** `insertados`/`actualizados` toma el máximo
   (`sync_service.py:121-126`): si MariaDB escribe 100 y SQLite 0, el resumen
   global muestra 100 y el desfase se ve en `central` y en
   `pendientes_reintento` (no en la cifra principal).
4. **Dos configuraciones de paginación** (`connectors.yaml` vs
   `Settings.sync_page_size`); si alguien cambia una pensando en el sync, no
   tendrá efecto (ver §1.2a).

---

## 2. Central doble: `persistence/repositories/`

### 2.1 ¿Qué problema resuelve?

Mantener **dos backends de la central** (MariaDB/MySQL como principal y SQLite
como espejo local) con la **misma lógica SQL**, sin fingir una transacción
distribuida y dejando **traza explícita** de cada fallo y de cada reintento.

| Fichero | Rol |
|---------|-----|
| `base.py` | Interfaz `CentralRepository` + implementación SQL **compartida** (`SqlCentralRepository`): upsert, búsquedas, conteos y hash. |
| `mariadb.py` | Subclase de 4 líneas: `super().__init__(engine, name="mariadb")` (`mariadb.py:10-14`). |
| `sqlite.py` | Subclase de 4 líneas: `super().__init__(engine, name="sqlite")` (`sqlite.py:10-14`). |
| `composite.py` | Composición: escritura en **todos**, lectura con **failover**, agregación de resultados. |
| `retry_queue.py` | Cola persistente de filas fallidas por backend. |

> El patrón es **reutilización por composición**: el SQL está escrito una sola
> vez en `base.py` y los backends sólo cambian `engine` y `name`.

### 2.2 ¿Cómo funciona?

#### (a) Escritura en todos los backends (`CompositeCentralRepository.upsert`)

```python
# app/persistence/repositories/composite.py:57-71
result = CompositeUpsertResult()
for repo in self._repos:
    try:
        stats = repo.upsert(rows)
        result.por_backend[repo.name] = stats
    except Exception as exc:
        logger.error("Fallo al escribir en %s: %s", repo.name, exc, exc_info=True)
        result.errores.append(f"{repo.name}: {exc}")
        result.por_backend[repo.name] = UpsertStats(errores=len(rows))
        result.pendientes_reintento += self._retry_queue.mark(repo.name, rows, str(exc))
return result
```

* El **orden de los repos** se fija en `create_app`: primero `MariaDbRepository`
  (si el engine existe) y después `SqliteRepository` (`app/main.py:56-60`).
* Un fallo en un backend **no** cancela el otro: cada uno se evalúa por separado.
* El resultado agrega `por_backend`, `errores` y `pendientes_reintento`
  (`composite.py:16-32`).

#### (b) Upsert y hash en `base.py`

| Operación | Comportamiento | Referencia |
|-----------|----------------|------------|
| `upsert()` | Dentro de una transacción `Session(...).begin()`, busca por `(origen, id_origen)` y **inserta** o **actualiza** (incluye `actualizado = func.now()`) | `base.py:88-131` (transacción: `104`; ramas: `112-130`) |
| Filas incompletas | Se descartan antes de tocar la BD y se cuentan como `errores` | `base.py:90-97` (regla: `36-37`, claves: `24`) |
| `search()` / `count()` | Filtros `dni` exacto en mayúsculas y `nombre` con `ilike`, paginación `offset/limit` | `base.py:164-193` |
| `hash_by_origen()` | sha256 de las filas ordenadas agrupadas por origen (`dni\|nombre\|casa\|patronus`) | `base.py:142-162` |
| `ping()` | `SELECT COUNT(*)` con captura de cualquier excepción → `False` | `base.py:205-212` |

La **huella de consistencia** excluye los id autoincrementales de cada backend
(sólo entran `dni|nombre|casa|patronus`), por lo que MariaDB y SQLite se pueden
comparar aunque sus `id` difieran (`base.py:146-160`). El resultado se expone en
`GET /central/consistencia` (`composite.py:138-164`).

#### (c) Failover de lectura

```python
# app/persistence/repositories/composite.py:104-113
def _read_repo(self) -> CentralRepository:
    for repo in self._repos:
        if repo.ping():
            return repo
    raise RuntimeError("Ningún backend de la central responde: ...")
```

* Las lecturas (`search`, `get_by_dni`, `count`) devuelven además **el nombre del
  backend utilizado** (`composite.py:115-136`), que el servicio refleja en la
  respuesta (`central_service.py:45-58`).
* La **escritura** nunca usa failover: se escribe en todos.

#### (d) Cola de reintentos (`retry_queue.py`)

| Método | Función | Referencia |
|---------|---------|------------|
| `mark(backend, rows, motivo)` | Inserta la fila fallida (o incrementa `intentos` y actualiza `motivo`, truncado a 400) | `retry_queue.py:28-64` |
| `pending(backend)` | Lista ordenada por `id` de las pendientes de ese backend | `retry_queue.py:66-85` |
| `resolve(backend, origen, id_origen)` | Borra la fila tras un reintento exitoso | `retry_queue.py:87-96` |
| `count()` | Total de pendientes (lo usa `/health` y `/central/consistencia`) | `retry_queue.py:98-101` |

El reintento se dispara **al principio de cada sync**
(`sync_service.py:41` → `composite.retry_pending()`, `composite.py:73-98`):
recorre fila a fila, hace `repo.upsert([fila])` y resuelve la entrada; si vuelve
a fallar, se queda pendiente y se incrementa el contador.

### 2.3 Punto crítico a vigilar

1. **No hay reconciliación automática.** Si MariaDB está caído durante varios
   syncs, la cola crece (`PendienteReintento`) y **sólo** se vacía con los
   reintentos futuros; no existe proceso de purga ni de expiración de `intentos`.
2. **Reintento fila a fila.** `retry_pending()` ejecuta una transacción por fila
   pendiente (`composite.py:82-87`): con muchos pendientes es costoso.
3. **Coste del failover de lectura.** `_read_repo()` hace un `ping()` en **cada**
   lectura (`composite.py:107-110`), es decir una query extra por petición.
4. **El hash depende del orden final por `id`.** `hash_by_origen()` ordena por
   `(origen, dni, id)` (`base.py:152`) y `id` es autoincremental **distinto en
   cada backend**; si hubiera DNIs repetidos dentro de un origen, el orden de
   concatenación podría diferir y el hash dar un falso negativo de consistencia.
5. **`totalizar()` está declarado pero no se usa** en el flujo activo
   (`base.py:215-222`); idem con `ServicioNoDisponible`
   (`app/core/exceptions.py:55-59`): código disponible, aún no lanzado.

---

## 3. Acceso a datos: `data_access/` (JVM, dialectos, JDBC)

### 3.1 ¿Qué problema resuelve?

Hablar con **cuatro motores JDBC distintos** manteniendo **una sola clase de
conector**, una **única JVM** por proceso y **cero SQL interpolado**:

| Variación entre orígenes | Dónde vive |
|--------------------------|------------|
| Driver, URL, jars, credenciales, tabla, columnas, dialecto | `config/connectors.yaml` → `ConnectorSpec` (`app/data_access/base.py:23-46`) |
| Sintaxis de paginación / siguiente id / mayúsculas de etiquetas | `Dialect` y subclases (`app/data_access/dialects.py:24-76`) |
| Código operativo (conexión, SQL, mapeo) | `JdbcConnector` (`app/data_access/jdbc_connector.py`) |
| Credenciales en runtime | Variables de entorno nombradas en el YAML (`env_user`/`env_password`), resueltas con `runtime_env()` (`app/core/config.py:16-26`, `registry.py:116-117`) |

### 3.2 ¿Cómo funciona?

#### (a) JVM única: `registry.start_jvm()`

```python
# app/data_access/registry.py:23-50
_JVM_LOCK = threading.Lock()
_JVM_STARTED = False

def start_jvm(jars: list[Path], timeout_seconds: int = 10) -> bool:
    with _JVM_LOCK:
        if jpype.isJVMStarted() or _JVM_STARTED:
            logger.info("La JVM ya estaba arrancada; se reutiliza (classpath compartido).")
            return False
        classpath = os.pathsep.join(str(jar) for jar in jars)
        jpype.startJVM("-ea", jvmpath=jpype.getDefaultJVMPath(),
                       classpath=classpath, convertStrings=True, ignoreUnrecognized=True)
        _JVM_STARTED = True
        return True
```

* El **classpath se construye con los jars de todos los conectores** detectados
  durante la carga del YAML, descartando duplicados y avisando de los que faltan
  (`registry.py:101-107`).
* Se llama **una sola vez**, dentro de `ConnectorRegistry.load()`
  (`registry.py:128-139`), que a su vez se invoca desde el `lifespan`
  (`app/main.py:62-66`).
* Si la JVM ya corre (p. ej. segundo `load()` o proceso que reutiliza el
  intérprete), devuelve `False` y **reutiliza el classpath compartido**
  (`registry.py:35-37`).

**¿Por qué no se reinicia la JVM?**

| Razón | Detalle |
|-------|---------|
| Restricción de JPype | `startJVM()` sólo puede llamarse **una vez por proceso**; no existe API de «reinicio». |
| Coste | Arrancar una JVM (y cargar 5+ JARs de drivers) cuesta cientos de ms; sería inasumible por petición. |
| Estado compartido | Drivers JDBC registrados en la JVM (`DriverManager`) persisten: reutilizar evita volver a registrarlos. |
| Aviso documentado | El propio código lo declara: *«nunca se reinicia dentro del mismo proceso»* (`registry.py:28-32`). |
| Consecuencia | **Para cambiar el classpath hay que reiniciar el proceso** (`Ctrl+C` a uvicorn). |
| Ordenación de hilos | Al cerrar conexiones se «desprende» el hilo de la JVM (`_desprender_de_la_jvm`, `jdbc_connector.py:29-40`, invocada en `77` y `87`) porque, con JPype 1.7, los hilos del pool de trabajo dejan la JVM adherida y el apagado se bloquearía. |

#### (b) Dialectos (`dialects.py`)

| Pieza | Función | Referencia |
|-------|---------|------------|
| `safe_ident()` | Lista blanca `^[A-Za-z_][A-Za-z0-9_]*$` para identificadores (**anti-inyección**) | `dialects.py:14-21` |
| `Dialect.fetch_clause()` | `OFFSET n ROWS FETCH FIRST m ROWS ONLY` — SQL:2008 válido en los cuatro orígenes; valida `offset ≥ 0` y `limit ≥ 1` | `dialects.py:31-35` |
| `Dialect.next_id_expression()` | `SELECT COALESCE(MAX(ID), 0) + 1 FROM <tabla>` (alta sin id) | `dialects.py:43-45` |
| `OracleDialect` | `upper_labels = True`: Oracle devuelve etiquetas en MAYÚSCULAS | `dialects.py:64-68` |
| `get_dialect()` | Fábrica por nombre; desconocido → `ValidacionError` | `dialects.py:71-84` |
| `map_row()` | Etiqueta JDBC → campo del modelo + coerción de tipos (`_coerce`: `int`/`str`/`None`) | `dialects.py:87-117` |

`map_row()` es **insensible a mayúsculas** en dos pasos: construye `lookup` con
etiquetas en mayúsculas y `reverse` con las columnas configuradas en mayúsculas
(`dialects.py:98-99`), por lo que funciona igual con etiquetas `DNI` (Oracle,
Derby) o `dni` (H2/HSQLDB según configuración).

#### (c) Conector JDBC: conexiones, sentencias y mapeo (`jdbc_connector.py`)

| Método | Papel | Referencia |
|--------|-------|------------|
| `_connection()` | Gestor de contexto: abre `jaydebeapi.connect(...)`, **cierra siempre** la conexión y suelta el hilo de la JVM; cualquier fallo se traduce en `ErrorOrigen` (502) | `jdbc_connector.py:66-87` |
| `_query_raw()` | Funnel de **lecturas**: `cursor.execute(sql, params)` → etiquetas (`cursor.description`) + filas crudas; cierra el cursor con `_close_quietly()` | `jdbc_connector.py:97-116` |
| `_query()` | `map_row(..., TIPOS_MODELO)` sobre cada fila cruda | `jdbc_connector.py:118-121` |
| `_execute()` | Funnel de **escrituras** (INSERT/UPDATE/DELETE) → nº de filas afectadas (`rowcount`) | `jdbc_connector.py:131-149` |
| `_where()` | WHERE parametrizado: sólo columnas mapeadas, `LIKE ?` para `nombre`/`patronus`, `= ?` para el resto | `jdbc_connector.py:162-184` |
| `list()` / `count()` / `get()` | SELECT con `fetch_clause` / `COUNT(*)` / primera fila | `jdbc_connector.py:199-229` |
| `create()` | Si no llega `id`, calcula `MAX(ID)+1` y relee la fila creada | `jdbc_connector.py:231-252` |
| `update()` / `delete()` | UPDATE parcial / DELETE por id; `affected == 0` → `None`/`False` | `jdbc_connector.py:254-274` |
| `_validar_payload()` | Última línea de defensa: campos desconocidos, `id` inmutable, tipos y vacíos | `jdbc_connector.py:279-307` |

**Sobre el «pool de sentencias»:** no existe un pool de conexiones explícito en
esta capa. El diseño es *conexión por sentencia*: **todas** las sentencias pasan
por los dos únicos embudos `_query_raw()` (lectura) y `_execute()` (escritura),
que gestionan el ciclo `conexión → cursor → cierre silencioso` y traducen
cualquier excepción a `ErrorOrigen`. La reutilización real de conexiones queda
en manos del driver JDBC y, para la central, del pool de SQLAlchemy
(`create_engine` en `app/persistence/db.py:29-38`).

### 3.3 Punto crítico a vigilar

1. **Classpath estático.** Añadir/actualizar un driver exige **reiniciar el
   proceso**; versiones incompatibles de un mismo driver en la misma JVM
   conviven sin posibilidad de aislarlas (`registry.py:38-48`).
2. **Conexión por sentencia.** Cada `list()`/`count()` abre y cierra una
   conexión JDBC (`jdbc_connector.py:99-107`): en un sync con muchas páginas se
   multiplican los *handshakes*. Es el punto a vigilar si se sube el volumen.
3. **Fuga de hilos adheridos a la JVM.** Si algún código JDBC nuevo no pasa por
   `_connection()`, el hilo se queda adherido y el apagado puede bloquearse
   (`jdbc_connector.py:29-40`).
4. **Mapeo de columnas sensible al YAML.** Un renombrado de columna en
   `connectors.yaml` sin actualizar `columns` produce filas «a medio mapear»:
   `map_row()` omite las etiquetas que no encuentra (`dialects.py:101-104`) y la
   fila fallará después en `transform_row()` como *incompleta*.
5. **Errores de origen = 502.** Cualquier fallo JDBC se envuelve en `ErrorOrigen`
   (`jdbc_connector.py:78-81`, `112-115`): el cliente ve `C-502`, nunca un
   stacktrace de Java.

---

## 4. Núcleo transversal: `core/` + `api/`

### 4.1 ¿Qué problema resuelve?

Unificar **errores, trazabilidad y acceso a servicios** para que los cuatro
routers se comporten igual y cualquier cliente (JavaFX, Android, Unity) pueda
interpretar las respuestas sin casos especiales.

### 4.2 ¿Cómo funciona?

#### (a) Envelope de errores unificado

| Pieza | Contenido | Referencia |
|-------|-----------|------------|
| `AppError` | `codigo` + `mensaje` + `status_code` + `detalle`; `to_payload()` produce `{"error": {...}}` | `app/core/exceptions.py:13-31` |
| Subclases | `ValidacionError` → `C-400`; `NoEncontradoError` → `C-404`; `ErrorOrigen` → `C-502`; `ServicioNoDisponible` → `C-503` | `app/core/exceptions.py:34-59` |
| `CODIGOS_HTTP` | Mapa 400/404/422/500/502/503 → `C-xxx` para las `HTTPException` de Starlette | `app/api/errors.py:21-28` |
| `_sanitizar_errores()` | Reduce el error de Pydantic a `{campo, mensaje, tipo}` (serializable) | `app/api/errors.py:35-46` |
| `install_error_handlers()` | Cuatro handlers globales | `app/api/errors.py:49-100` |

| Handler | Se activa en | Respuesta |
|---------|--------------|-----------|
| `_app_error` | Cualquier `AppError` lanzada por servicios/conectores | `status_code` propio + envelope (`errors.py:52-61`) |
| `_validacion` | Fallo de validación de Pydantic en la entrada | 422 `C-422` con detalle saneado (`errors.py:63-77`) |
| `_http` | `HTTPException` de Starlette (404 de ruta, etc.) | envuelto con `CODIGOS_HTTP` (`errors.py:79-87`) |
| `_inesperado` | Cualquier excepción no controlada | 500 `C-500` con **sólo** el tipo de la excepción (`errors.py:89-100`) |

Todas las respuestas de error llevan el mismo esqueleto:

```json
{"error": {"codigo": "C-404", "mensaje": "No hay ningún estudiante con DNI ...", "detalle": {...}}}
```

#### (b) Middleware y logging

| Elemento | Comportamiento | Referencia |
|----------|----------------|------------|
| `RequestIdMiddleware` | Reutiliza la cabecera `X-Request-ID` entrante o genera `uuid4().hex[:16]`; la guarda en `request.state` y la devuelve en la respuesta | `app/core/middleware.py:28-30`, `47` |
| Métricas de acceso | `request_id`, método, ruta, estado y `duracion_ms` en logger `r01.accesos`; en error inesperado registra antes de re-lanzar | `app/core/middleware.py:36-55` |
| Handlers de log | Consola + `logs/app.log` rotativo (2 MB × 5 copias) | `app/core/logging_config.py:45-50` |
| Redacción | Filtro que sustituye el mensaje por `[REDACTADO...]` si contiene `password`, `token`, `secret`, etc. | `app/core/logging_config.py:12-29` |
| Ruido de Uvicorn | `uvicorn.access` a WARNING para que el log de accesos no se duplique | `app/core/logging_config.py:57` |
| Correlación | Los handlers de error reutilizan `request.state.request_id` | `app/api/errors.py:54-59`, `65-69`, `91-96` |

#### (c) Inyección de servicios (`deps.py`)

```python
# app/api/deps.py:12-24
def get_connector_service(request: Request) -> ConnectorService:
    return request.app.state.connector_service

def get_central_service(request: Request) -> CentralService:
    return request.app.state.central_service

def get_sync_service(request: Request) -> SyncService:
    return request.app.state.sync_service
```

* No hay contenedor de dependencias: los singletons se construyen **una vez** en
  el `lifespan` (`app/main.py:68-76`) y se leen desde `app.state`.
* Los routers sólo declaran `Depends(get_*)`, lo que hace trivial sustituir el
  servicio en pruebas (los tests montan la app con un registro falso,
  `app/main.py:42-43`).
* Cada router usa exclusivamente su servicio: `health` combina dos
  (`app/api/routers/health.py:35-36`).

### 4.3 Punto crítico a vigilar

1. **Orden de los handlers.** Si una excepción no es `AppError` ni está
   registrada, cae en `_inesperado` (500): conviene revisar que los servicios
   lancen siempre `ValidacionError`/`NoEncontradoError`/`ErrorOrigen` y no
   `ValueError` sueltas.
2. **La redacción es tijeretazo.** `_redact()` sustituye **el mensaje entero**
   si detecta una palabra sensible (`logging_config.py:15-21`), por lo que puede
   ocultar el diagnóstico real de un fallo de conexión.
3. **`BaseHTTPMiddleware` añade sobrecarga** por petición (copia de scope y
   respuesta); medir con `duracion_ms` si se exigiera latencia mínima.
4. **`app.state` mutuable.** Nada impide sobrescribir un servicio en caliente; el
   contrato es «se construye en el `lifespan` y sólo se lee después».
5. **Errores de validación duplicados.** La misma regla se aplica en tres capas
   (esquema Pydantic, servicio y conector): es defensa en profundidad, pero si
   se cambia una regla hay que mantener las tres (`schemas/estudiante.py:23-36`,
   `connector_service.py:129-147`, `jdbc_connector.py:279-307`).

---

## 5. Cuadro de vigilancia

| # | Pieza | Riesgo principal | Dónde mirar |
|---|-------|------------------|-------------|
| 1 | Paginación del sync | Memoria lineal y desfases si el origen cambia | `app/services/sync_service.py:45-71` |
| 2 | Cola de reintentos | Crece sin techo mientras MariaDB esté caído | `app/persistence/repositories/retry_queue.py:28-64` |
| 3 | Failover de lectura | `ping()` extra en cada consulta | `app/persistence/repositories/composite.py:104-113` |
| 4 | Hash de consistencia | Orden final por `id` autoincremental | `app/persistence/repositories/base.py:142-162` |
| 5 | JVM única | Classpath inmutable hasta reiniciar el proceso | `app/data_access/registry.py:27-50` |
| 6 | Conexión por sentencia | Latencia acumulada en páginas grandes | `app/data_access/jdbc_connector.py:66-149` |
| 7 | Mapeo de columnas | Filas «medias» si cambia el YAML | `app/data_access/dialects.py:87-117` |
| 8 | Envelope de errores | Excepciones no controladas → 500 genérico | `app/api/errors.py:49-100` |
| 9 | Redacción de logs | Mensajes útiles sustituidos íntegramente | `app/core/logging_config.py:12-29` |

---

**Documentos relacionados:** [`01-README.md`](01-README.md) ·
[`04-manual-api-arquitectura.md`](04-manual-api-arquitectura.md) ·
[`06-arquitectura-backend.md`](06-arquitectura-backend.md) ·
[`../backend/README.md`](../backend/README.md)
