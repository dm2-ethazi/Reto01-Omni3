# R01 · Backend de integración (FastAPI)

> API REST que conecta **cuatro bases de datos de origen heterogéneas**
> (Apache Derby, HSQLDB, H2 y Oracle) con una **central doble**
> (MariaDB/MySQL + SQLite), mediante un flujo de **sincronización trazable**
> y un envelope de errores único.

| Aspecto | Valor |
|---------|-------|
| Lenguaje / runtime | Python 3.11+ |
| Framework web | FastAPI + Uvicorn |
| ORM de la central | SQLAlchemy 2.x (modo `Mapped[]`) |
| Acceso a orígenes | JDBC vía JayDeBeApi + JPype1 (JVM embebida) |
| Configuración | `pydantic-settings` + `.env` + `config/connectors.yaml` |
| Pruebas | `pytest` (unidad + integración) y `ruff` |

---

## 1. Arquitectura por capas

El código está partido en **cuatro capas** con dependencias en una sola
dirección: `api → services → (persistence | data_access) → core`.

| # | Capa | Paquete | Responsabilidad | Regla |
|---|------|---------|-----------------|-------|
| 1 | **API** | `app/api/` | Routers, esquemas Pydantic, dependencias e instalación de handlers de error. Traduce HTTP ⇄ llamadas de servicio. | No habla con bases de datos ni con JDBC; sólo con servicios. |
| 2 | **Servicios** | `app/services/` | Reglas de negocio: CRUD de orígenes, sincronización (leer → transformar → insertar) y consultas de la central. | No conoce HTTP ni SQL concreto; depende de abstracciones (`DataSource`, `CompositeCentralRepository`). |
| 3a | **Persistencia** | `app/persistence/` | Modelos SQLAlchemy, engines y repositorios de la **central doble** (MariaDB + SQLite + cola de reintentos). | Un único código SQL reutilizado por ambos backends. |
| 3b | **Acceso a datos** | `app/data_access/` | Registro de conectores, JVM/JDBC, dialectos SQL y mapeo de columnas de los **cuatro orígenes**. | Una única implementación JDBC parametrizada por `ConnectorSpec`. |
| 4 | **Núcleo** | `app/core/` | Configuración, excepciones de aplicación, middleware de trazabilidad y logging. | Sin dependencias de las capas superiores. |

```text
        cliente (JavaFX / Android / Unity / curl)
                         │  HTTP
                         ▼
   ┌──────────────── app/api ────────────────┐
   │ routers/  schemas/  deps.py  errors.py  │
   └───────────────────┬─────────────────────┘
                       │ Depends(...)
        ┌──────────────┴───────────────┐
        ▼                              ▼
  app/services                  app/services
  (connector, central, sync,    (transform: normaliza
   transform)                    y valida filas)
        │                              │
        ▼                              ▼
 app/data_access                app/persistence
 (registry + JVM +             (db, models,
  jdbc_connector +              repositories/
  dialects)                     composite|base|
        │                       mariadb|sqlite|
        │                       retry_queue)
        ▼                              ▼
  Derby · HSQLDB · H2 · Oracle   MariaDB (principal)
                                 + SQLite (espejo)
```

> El módulo `app/core/` lo consumen **todas** las capas
> (excepciones, `Settings`, logging, middleware).

---

## 2. Árbol de módulos (una línea de propósito por fichero)

```text
backend/
├── app/
│   ├── main.py                  # Fábrica create_app(): lifespan, middlewares y routers.
│   ├── core/
│   │   ├── config.py            # Settings (variables de entorno + .env) y runtime_env().
│   │   ├── exceptions.py        # Jerarquía AppError → código HTTP + payload de error.
│   │   ├── middleware.py        # RequestIdMiddleware: X-Request-ID y duración de cada petición.
│   │   └── logging_config.py    # Consola + logs/app.log rotativo, con redacción de secretos.
│   ├── api/
│   │   ├── deps.py              # Dependencias FastAPI: lee los servicios desde app.state.
│   │   ├── errors.py            # Handlers globales: traduce excepciones al envelope único.
│   │   ├── routers/
│   │   │   ├── health.py        # GET /health (siempre 200; ok|degraded en el cuerpo).
│   │   │   ├── connectors.py    # CRUD de estudiantes contra los orígenes (/connectors).
│   │   │   ├── sync.py          # POST /sync y POST /sync/{id} (flujo de sincronización).
│   │   │   └── central.py       # Lecturas de la central y /central/consistencia.
│   │   └── schemas/
│   │       ├── estudiante.py    # Modelos Pydantic v2 de estudiantes y páginas.
│   │       └── salud.py         # HealthResponse, ConnectorEstado y SyncResumen.
│   ├── services/
│   │   ├── connector_service.py # CRUD validado sobre el registro de conectores.
│   │   ├── central_service.py   # Consultas paginadas y consistencia de la central.
│   │   ├── sync_service.py      # Orquesta paginar → transformar → upsert + resumen.
│   │   └── transform.py         # Normalización/validación de filas (DNI, longitudes).
│   ├── data_access/
│   │   ├── base.py              # Contratos: ConnectorSpec y DataSource (interfaz).
│   │   ├── registry.py          # Carga connectors.yaml y arranca la JVM una sola vez.
│   │   ├── dialects.py          # Dialectos SQL, identificadores seguros y map_row().
│   │   └── jdbc_connector.py    # Única implementación JDBC (conexión, SQL, mapeo).
│   └── persistence/
│       ├── db.py                # Fábrica de engines SQLite/MariaDB e init_schema().
│       ├── models.py            # EstudianteCentral y PendienteReintento (SQLAlchemy).
│       └── repositories/
│           ├── base.py          # Interfaz CentralRepository + SQL compartido (upsert/hash).
│           ├── mariadb.py       # Backend MariaDB/MySQL (subclase con name="mariadb").
│           ├── sqlite.py        # Backend SQLite (subclase con name="sqlite").
│           ├── composite.py     # Escritura en ambos backends, failover de lectura.
│           └── retry_queue.py   # Cola persistente de upserts fallidos por reintento.
├── config/
│   ├── connectors.yaml          # Los cuatro conectores (URL, jars, dialecto, columnas).
│   └── connectors.example.yaml  # Plantilla versionable del fichero anterior.
├── drivers/                     # JARs JDBC (se descargan; no se versionan credenciales).
├── data/                        # central.db (SQLite de la central + cola de reintentos).
├── logs/                        # app.log rotativo.
├── tests/
│   ├── conftest.py              # Orígenes falsos + create_app con registry inyectado.
│   ├── unit/                    # transform, persistencia, dialectos (sin Docker/JVM).
│   └── integration/             # API y contenedores reales (marcador `integration`).
├── .env.example                 # Plantilla de variables de entorno (sin secretos reales).
├── requirements.txt             # Dependencias de ejecución.
├── requirements-dev.txt         # pytest, pytest-cov, httpx, ruff.
└── pyproject.toml               # Reglas de ruff y de pytest.
```

---

## 3. Quickstart

### 3.1 Requisitos

| Requisito | Detalle |
|-----------|---------|
| Python | 3.11 o superior |
| `.env` | Copiar `.env.example` → `.env` y revisar los valores |
| `config/connectors.yaml` | Debe existir (hay plantilla `connectors.example.yaml`) |
| Drivers JDBC | JARs en `drivers/` (los referencia `connectors.yaml`) |
| Orígenes / central | Contenedores de `r01/infra` (ver `docs/02-manual-instalacion.md`) |

### 3.2 Pasos

```powershell
# 0) Ir al directorio del backend
cd r01\backend

# 1) Entorno virtual + dependencias
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt   # sólo para pruebas

# 2) Configuración: copiar la plantilla y revisarla (no versionar el .env real)
Copy-Item .env.example .env
Copy-Item config\connectors.example.yaml config\connectors.yaml   # sólo si no existe

# 3) Arranque del servidor
python -m uvicorn app.main:app --port 8000
# equivalente con el venv activo:
#   .\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
```

### 3.3 Comprobación

```powershell
curl http://127.0.0.1:8000/health     # status: ok | degraded
curl http://127.0.0.1:8000/docs       # Swagger UI
```

### 3.4 Pruebas y calidad

```powershell
.\.venv\Scripts\python.exe -m pytest                       # todo
.\.venv\Scripts\python.exe -m pytest tests/unit            # sin Docker ni JVM
.\.venv\Scripts\python.exe -m pytest -m "not integration"  # excluye integración
.\.venv\Scripts\python.exe -m ruff check .
```

---

## 4. Configuración

La **configuración variable** sale del fichero `.env` (nunca del código) y de
`config/connectors.yaml` (rutas, drivers y mapeo de columnas); `.env.example`
documenta cada variable y **no contiene credenciales reales**.

| Grupo | Variables (ver `.env.example`) | Uso |
|-------|--------------------------------|-----|
| Conectores | `DERBY_*`, `HSQLDB_*`, `H2_*`, `ORACLE_*` | Credenciales JDBC resueltas por nombre en `connectors.yaml` (`env_user` / `env_password`). |
| Central | `MARIADB_HOST`, `MARIADB_PORT`, `MARIADB_USER`, `MARIADB_PASSWORD`, `MARIADB_DATABASE`, `MARIADB_ENABLED` | Backend principal de la central; si no responde, el espejo SQLite sigue operativo. |
| Servidor | `APP_HOST`, `APP_PORT`, `ENVIRONMENT`, `LOG_LEVEL` | Arranque y nivel de log. |

**Orden de resolución** (`app/core/config.py`): variables de entorno →
fichero `.env` → valores por defecto de `Settings`.

---

## 5. Documentación

| Documento | Contenido |
|-----------|-----------|
| [`../docs/01-README.md`](../docs/01-README.md) | Visión global del reto y estructura del repositorio |
| [`../docs/02-manual-instalacion.md`](../docs/02-manual-instalacion.md) | Requisitos, infraestructura, configuración y arranque |
| [`../docs/03-manual-usuario.md`](../docs/03-manual-usuario.md) | Uso operativo de la API y de los clientes |
| [`../docs/04-manual-api-arquitectura.md`](../docs/04-manual-api-arquitectura.md) | Referencia de endpoints, esquemas, errores y diseño |
| [`../docs/05-guia-de-pruebas.md`](../docs/05-guia-de-pruebas.md) | Suite pytest, marcadores y criterios de aceptación |
| [`../docs/06-arquitectura-backend.md`](../docs/06-arquitectura-backend.md) | Recorrido de una petición extremo a extremo y ciclo de vida de `create_app` |
| [`../docs/07-piezas-complejas.md`](../docs/07-piezas-complejas.md) | Deep dive: sincronización, central doble, JVM/JDBC y núcleo transversal |

> **Nota de seguridad:** este README (y el resto de documentación) sólo
> referencia `.env.example` y `config/connectors.example.yaml`.
> Las credenciales reales viven en `.env`, que **no debe versionarse**.
