# Manual de API y arquitectura â€” Reto R01

**VersiÃ³n:** 1.0 Â· **Fecha:** 2026-10-01
**Componente:** `r01/backend` (FastAPI + SQLAlchemy + JDBC/JVM)

> **Trazabilidad:** punto 4 de los cinco manuales obligatorios establecidos
> por **deducciÃ³n** (no existe pliego en el repositorio; ver
> `01-README.md` Â§5). Todo el contenido se ha extraÃ­do de la fuente real:
> `app/api/routers/*.py`, `app/api/schemas/*.py`, `app/services/*.py` y
> `app/main.py`.

---

## Ãndice

1. [VisiÃ³n arquitectÃ³nica](#1-visiÃ³n-arquitectÃ³nica)
2. [Capas del backend](#2-capas-del-backend)
3. [Diagramas UML asociados](#3-diagramas-uml-asociados)
4. [Referencia de endpoints](#4-referencia-de-endpoints)
5. [Esquemas de datos](#5-esquemas-de-datos)
6. [Reglas de validaciÃ³n y transformaciÃ³n](#6-reglas-de-validaciÃ³n-y-transformaciÃ³n)
7. [Manejo de errores](#7-manejo-de-errores)
8. [Central doble y resiliencia](#8-central-doble-y-resiliencia)
9. [ConfiguraciÃ³n tÃ©cnica](#9-configuraciÃ³n-tÃ©cnica)
10. [Ciclo de vida de la aplicaciÃ³n](#10-ciclo-de-vida-de-la-aplicaciÃ³n)

---

## 1. VisiÃ³n arquitectÃ³nica

La API expone cuatro routers registrados en `app/main.py`:

```python
app.include_router(health.router)      # /health
app.include_router(connectors.router)  # /connectors
app.include_router(sync.router)        # /sync
app.include_router(central.router)     # /central
```

MÃ¡s la raÃ­z `GET /` (`include_in_schema=False`).

| Router | Prefijo | Tag | Papel |
|--------|---------|-----|-------|
| `health.py` | â€” | `salud` | Estado global, siempre HTTP 200 |
| `connectors.py` | `/connectors` | `conectores` | CRUD y listado sobre los orÃ­genes |
| `sync.py` | `/sync` | `sincronizacion` | Flujo leerâ†’transformarâ†’insertar (4.4) |
| `central.py` | `/central` | `central` | Consultas y consistencia (sÃ³lo lectura) |

---

## 2. Capas del backend

| Capa | Paquete | Responsabilidad |
|------|---------|-----------------|
| API | `app/api` | Routers, esquemas Pydantic, `deps.py`, `errors.py` |
| Servicios | `app/services` | `ConnectorService`, `SyncService`, `CentralService`, `transform` |
| Acceso a orÃ­genes | `app/data_access` | `ConnectorRegistry`, `JdbcDataSource`, `dialects` (JVM vÃ­a JPype/JayDeBeApi) |
| Persistencia central | `app/persistence` | Engines, `models`, repositorios `mariadb`/`sqlite`/`composite`/`retry_queue` |
| NÃºcleo | `app/core` | `config.Settings`, `exceptions`, `logging_config`, `middleware` |

Dependencias inyectadas (`app/api/deps.py`):

- `get_connector_service` â†’ `app.state.connector_service`
- `get_central_service` â†’ `app.state.central_service`
- `get_sync_service` â†’ `app.state.sync_service`

Middleware: `RequestIdMiddleware` (asigna `request_id` a cada peticiÃ³n,
usado en los logs de error).

---

## 3. Diagramas UML asociados

| Fichero | Tipo | Contenido |
|---------|------|-----------|
| `arquitectura-componentes.puml` | Componentes/despliegue | Clientes, API, servicios, orÃ­genes y central |
| `secuencia-sincronizacion.puml` | Secuencia | Flujo 4.4 completo de `POST /sync` |
| `secuencia-consultas.puml` | Secuencia | Lectura con conmutaciÃ³n por fallo en `/central` |
| `modelo-datos.puml` | Clases | Modelos Pydantic y entidades de persistencia |

Todos incluyen la directiva `pragma layout Smetana`.

---

## 4. Referencia de endpoints

### 4.1 `GET /` â€” RaÃ­z

Sin esquema en OpenAPI. Respuesta:

```json
{ "app": "R01 - Backend de integracion", "version": "1.0.0",
  "reto": "R01", "docs": "/docs" }
```

### 4.2 `GET /health` â€” Salud

| Aspecto | Valor |
|---------|-------|
| Tag | `salud` |
| CÃ³digo | **Siempre 200** |
| ParÃ¡metros | Ninguno |
| Depende de | `ConnectorService` y `CentralService` |

Cuerpo (`HealthResponse`):

| Campo | Tipo | DescripciÃ³n |
|-------|------|-------------|
| `status` | `str` | `ok` \| `degraded` |
| `reto` | `str` | Fijo `"R01"` |
| `central` | `object` | Estado por backend + `pendientes_reintento` |
| `conectores` | `object` | Estado por conector |
| `jvm` | `object` | `arrancada`, `avisos` |
| `errores` | `list[str]` | Errores acumulados de la central |
| `caidos` | `list[str]` | Dependencias caÃ­das |
| `app` | `object` | `nombre`, `version` |

Regla de negocio: MariaDB sÃ³lo se marca caÃ­da si `mariadb_enabled=true`
**y** no responde; si estÃ¡ deshabilitada por diseÃ±o no es una caÃ­da.

### 4.3 `GET /connectors` â€” Estado de los conectores

Devuelve `{"conectores": {id: ConnectorEstado}}` con
`id`, `nombre`, `dialect`, `url`, `disponible`, `detalle`.

### 4.4 `GET /connectors/{connector_id}/estudiantes` â€” Listado paginado

| ParÃ¡metro | Tipo | Restricciones | Por defecto |
|-----------|------|---------------|-------------|
| `connector_id` | path | id de conector existente | â€” |
| `offset` | query | `ge=0` | `0` |
| `limit` | query | `ge=1`, `le=500` | `50` |
| `dni` | query | `max_length=9` | `null` |
| `nombre` | query | `max_length=100` | `null` |

- **200** â†’ `PaginaEstudiantes` (`items`, `total`, `offset`, `limit`, `conector`).
- **404 `C-404`** â†’ conector desconocido.
- **422 `C-422`** â†’ `limit` fuera de rango.

### 4.5 `POST /connectors/{connector_id}/estudiantes` â€” Alta

- Body: `EstudianteCreate` (`dni`, `nombre`, `casa`, `patronus` + `id` opcional).
- **201** â†’ `EstudianteOut`.
- **400 `C-400`** â†’ DNI invÃ¡lido.
- **422 `C-422`** â†’ campos faltantes o extra.

### 4.6 `GET /connectors/{connector_id}/estudiantes/{row_id}`

- **200** â†’ `EstudianteOut`; **404** si no existe.

### 4.7 `PUT /connectors/{connector_id}/estudiantes/{row_id}` â€” Update parcial

- Body: `EstudianteUpdate` (todos opcionales); `solo_campos()` descarta los `null`.
- **200** â†’ `EstudianteOut`.

### 4.8 `DELETE /connectors/{connector_id}/estudiantes/{row_id}`

- **204** sin cuerpo; **404** si no existe.

### 4.9 `POST /sync/{connector_id}` â€” Sincroniza un conector

- **200** â†’ `SyncResumen` ampliado (`conector`, `leidos`, `insertados`,
  `actualizados`, `errores`, `detalle_errores`, `central`, `duracion_ms`, â€¦).
- **400 `C-400`** â†’ conector desconocido.

### 4.10 `POST /sync` â€” Sincroniza todos

- **200** â†’ `{"conectores": [...], "insertados": N, "actualizados": N, "errores": N}`.

### 4.11 `GET /central/estudiantes` â€” BÃºsqueda en la central

| ParÃ¡metro | Restricciones | Por defecto |
|-----------|---------------|-------------|
| `nombre` | `max_length=100`, parcial | `null` |
| `dni` | `max_length=9`, exacto | `null` |
| `offset` | `ge=0` | `0` |
| `limit` | `ge=1`, `le=500` | `50` |

- **200** â†’ `PaginaCentral` (`items`, `total`, `offset`, `limit`, `backend`, `filtros`).

### 4.12 `GET /central/estudiantes/{dni}` â€” Consulta exacta

- Path: `min_length=9`, `max_length=9`.
- **200** â†’ `EstudianteCentralOut`; **404 `C-404`** si no existe.

### 4.13 `GET /central/consistencia` â€” Paridad MariaDB vs SQLite

- **200** â†’ `{"coincide": bool, "pendientes_reintento": int,
  "backends": {"sqlite": {"conteos": {...}}, "mariadb": {...}}}`.

### 4.14 Resumen de cÃ³digos de estado

| MÃ©todo + Ruta | 200 | 201 | 204 | 400 | 404 | 422 |
|---------------|-----|-----|-----|-----|-----|-----|
| `GET /health` | âœ” | | | | | |
| `GET /connectors` | âœ” | | | | | |
| `GET /connectors/{id}/estudiantes` | âœ” | | | | âœ” | âœ” |
| `POST /connectors/{id}/estudiantes` | | âœ” | | âœ” | âœ” | âœ” |
| `PUT /connectors/{id}/estudiantes/{row}` | âœ” | | | âœ” | âœ” | âœ” |
| `DELETE /connectors/{id}/estudiantes/{row}` | | | âœ” | | âœ” | |
| `POST /sync` Â· `POST /sync/{id}` | âœ” | | | âœ” | | |
| `GET /central/estudiantes[/{dni}]` | âœ” | | | | âœ” | âœ” |
| `GET /central/consistencia` | âœ” | | | | | |

---

## 5. Esquemas de datos

Ficheros: `app/api/schemas/estudiante.py` y `app/api/schemas/salud.py`.
Todos usan `ConfigDict(extra="forbid")`.

### 5.1 Modelo de estudiante

| Campo | Tipo | Restricciones |
|-------|------|---------------|
| `dni` | `str` | Formato `12345678Z` o `XYZ1234567Z`; normalizado a mayÃºsculas |
| `nombre` | `str` | `min_length=1`, `max_length=100`; espacios colapsados |
| `casa` | `str` | `min_length=1`, `max_length=30` |
| `patronus` | `str` | `min_length=1`, `max_length=50` |
| `id` | `int \| None` | SÃ³lo en create; `ge=1` |

### 5.2 Tipos de respuesta

| Tipo | Campos |
|------|--------|
| `EstudianteOut` | `id`, `dni`, `nombre`, `casa`, `patronus` |
| `PaginaEstudiantes` | `items[]`, `total`, `offset`, `limit`, `conector` |
| `EstudianteCentralOut` | `id`, `origen`, `id_origen`, `dni`, `nombre`, `casa`, `patronus`, `actualizado`, `backend` |
| `PaginaCentral` | `items[]`, `total`, `offset`, `limit`, `backend`, `filtros` |
| `SyncResumen` | `conector`, `leidos`, `insertados`, `actualizados`, `errores` |
| `ConnectorEstado` | `id`, `nombre`, `dialect`, `url`, `disponible`, `detalle` |
| `HealthResponse` | `status`, `reto`, `central`, `conectores`, `jvm`, `errores` |

### 5.3 Modelo relacional de la central

| Columna | Tipo lÃ³gico | Notas |
|---------|-------------|-------|
| `id` | PK | Id propio de la central |
| `origen` | text | `derby`/`hsqldb`/`h2`/`oracle` (trazabilidad) |
| `id_origen` | int | Id de la fila en el origen |
| `dni`, `nombre`, `casa`, `patronus` | text | Normalizados |
| `actualizado` | timestamp | Ãšltima modificaciÃ³n |

Clave lÃ³gica de negocio: `(origen, id_origen)` y `dni` para bÃºsquedas.

---

## 6. Reglas de validaciÃ³n y transformaciÃ³n

Centralizadas en `app/services/transform.py` (se aplican igual en el CRUD
y en la sincronizaciÃ³n, sin duplicaciÃ³n por conector):

1. `trim` + colapso de espacios internos repetidos.
2. DNI en mayÃºsculas y validado con
   `^(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])$`.
3. LÃ­mites: `nombre â‰¤ 100`, `casa â‰¤ 30`, `patronus â‰¤ 50`.
4. Se aÃ±ade el campo `origen` para trazar el procedimiento de cada fila.
5. Fila incompleta o `id` no numÃ©rico â†’ `ValidacionError` (se cuenta como
   `errores_transformacion` y se continÃºa con el resto).

---

## 7. Manejo de errores

Envelope Ãºnico (`app/api/errors.py` â†’ `install_error_handlers`):

```json
{ "error": { "codigo": "C-404", "mensaje": "...", "detalle": {} } }
```

| CÃ³digo | HTTP | Origen |
|--------|------|--------|
| `C-400` | 400 | `ValidacionError` (DNI invÃ¡lido, conector desconocido) |
| `C-404` | 404 | `NoEncontradoError` (recurso/conector inexistente) |
| `C-422` | 422 | `RequestValidationError` de Pydantic/FastAPI (sanitizado: `campo`, `mensaje`, `tipo`) |
| `C-500` | 500 | Cualquier excepciÃ³n no controlada (log con stack trace) |
| `C-502` | 502 | `ErrorOrigen` (fallo contra un origen/base) |
| `C-503` | 503 | `ServicioNoDisponible` (dependencia caÃ­da) |

JerarquÃ­a en `app/core/exceptions.py`:
`AppError` â†’ `ValidacionError` Â· `NoEncontradoError` Â· `ErrorOrigen` Â·
`ServicioNoDisponible`.

---

## 8. Central doble y resiliencia

`CompositeCentralRepository` (`app/persistence/repositories/composite.py`)
mantiene un orden de backends: **MariaDB (principal) â†’ SQLite (espejo)**.

- **Lecturas:** si el backend principal no responde, se sirve desde SQLite
  (por eso `PaginaCentral.backend` puede ser `mariadb` o `sqlite`).
- **Escrituras (upsert):** se intentan en todos los backends
  configurados; los fallos se encolan en `SqliteRetryQueue`.
- **Cola de reintentos:** `retry_pending()` se ejecuta al inicio de cada
  `POST /sync`, asÃ­ el prÃ³ximo sincronismo drena los pendientes.
- **Consistencia:** `GET /central/consistencia` compara conteos y huellas
  de contenido entre backends (`coincide`).
- Si `MARIADB_ENABLED=false`, sÃ³lo se registra `SqliteRepository` y no se
  considera averÃ­a.

---

## 9. ConfiguraciÃ³n tÃ©cnica

`app/core/config.py` â†’ `Settings` (pydantic-settings; orden de resoluciÃ³n:
variables de entorno â†’ `.env` â†’ valores por defecto).

| Atributo | Por defecto |
|----------|-------------|
| `app_name` / `app_version` | `R01 - Backend de integracion` / `1.0.0` |
| `host` / `port` | `0.0.0.0` / `8000` |
| `log_level` / `log_dir` | `INFO` / `backend/logs` |
| `connectors_config` | `backend/config/connectors.yaml` |
| `drivers_dir` | `backend/drivers` |
| `sqlite_central_path` | `backend/data/central.db` |
| `mariadb_host/port/user/password/database` | `localhost:3306`, `r01`, `r01pass`, `central` |
| `mariadb_enabled` | `true` |
| `sync_page_size` | `100` |
| `request_timeout_seconds` | `15` |

URLs derivadas: `mariadb_url` (`mysql+pymysql://â€¦`) y `sqlite_url`
(`sqlite:///{sqlite_central_path}`).

---

## 10. Ciclo de vida de la aplicaciÃ³n

`create_app(settings=None, registry=None)` en `app/main.py`:

1. **ConfiguraciÃ³n y logging** (`configure_logging`).
2. **`lifespan`:**
   1. `build_engines(settings)` â†’ engines MariaDB y SQLite.
   2. `init_schema(engines)` â†’ crea el esquema de la central.
   3. `SqliteRetryQueue` + `MariaDbRepository` (si procede) +
      `SqliteRepository` â†’ `CompositeCentralRepository`.
   4. `ConnectorRegistry.load(connectors_config, drivers_dir, environ)` â†’
      arranca la JVM **una sola vez** y carga los `.jar`.
   5. Registra en `app.state`: `settings`, `engines`, `central`,
      `registry` y los tres servicios.
3. **Middleware y handlers:** `RequestIdMiddleware` + `install_error_handlers`.
4. **Routers:** `health`, `connectors`, `sync`, `central`.
5. **Apagado:** `dispose()` de ambos engines.

El parÃ¡metro `registry` de `create_app` permite inyectar conectores falsos
en las pruebas **sin arrancar la JVM** (ver `tests/conftest.py`).
