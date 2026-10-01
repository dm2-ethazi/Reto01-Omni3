# Guía de pruebas — Reto R01

**Versión:** 1.0 · **Fecha:** 2026-10-01
**Componente:** `r01/backend/tests` (pytest 9.1.1 + coverage)

> **Trazabilidad:** punto 5 de los cinco manuales obligatorios establecidos
> por **deducción** (no existe pliego en el repositorio; ver
> `01-README.md` §5).

---

## Índice

1. [Estrategia de pruebas](#1-estrategia-de-pruebas)
2. [Estructura del directorio `tests/`](#2-estructura-del-directorio-tests)
3. [Cómo ejecutar la suite](#3-cómo-ejecutar-la-suite)
4. [Fixtures y dobles de prueba](#4-fixtures-y-dobles-de-prueba)
5. [Catálogo de pruebas unitarias](#5-catálogo-de-pruebas-unitarias)
6. [Catálogo de pruebas de integración (API)](#6-catálogo-de-pruebas-de-integración-api)
7. [Catálogo de pruebas de integración (contenedores)](#7-catálogo-de-pruebas-de-integración-contenedores)
8. [Pruebas manuales / smoke test](#8-pruebas-manuales--smoke-test)
9. [Criterios de aceptación](#9-criterios-de-aceptación)
10. [Análisis estático y cobertura](#10-análisis-estático-y-cobertura)
11. [Cómo añadir una prueba nueva](#11-cómo-añadir-una-prueba-nueva)

---

## 1. Estrategia de pruebas

| Nivel | Directoria | Dependencias | Objetivo |
|-------|------------|--------------|----------|
| Unitario | `tests/unit/` | Ninguna (en memoria) | Transformación, dialectos, persistencia |
| Integración API | `tests/integration/test_api.py` | Ninguna (orígenes falsos) | Endpoints completos vía `TestClient` |
| Integración real | `tests/integration/test_contenedores.py` | Docker (`integration`) | Orígenes reales y JVM |

**Principios:**

1. La suite por defecto **no necesita Docker ni JVM**: los orígenes se
   sustituyen por `FakeDataSource`/`FakeRegistry` inyectados en
   `create_app(settings=..., registry=...)`.
2. Las pruebas que sí requieren contenedores llevan el marcador
   `integration` y **se auto-omiten** (`pytest.skip`) si los puertos no
   responden: la suite unitaria pasa en cualquier máquina.
3. `pyproject.toml` fija `addopts = "-ra -p no:faulthandler"` porque la
   JVM escribe accesos ilegales benignos al entrar/salir de JNI que
   generarían trazas falsas de pytest.

---

## 2. Estructura del directorio `tests/`

```
r01/backend/tests/
├── conftest.py                  # FILAS, FakeDataSource, FakeRegistry, fixtures
├── unit/
│   ├── test_transform.py        # normalización y validación de DNI
│   ├── test_dialects.py         # dialectos SQL e identificadores seguros
│   └── test_persistence.py      # central doble, upsert idempotente, cola
└── integration/
    ├── test_api.py              # API REST completa con orígenes falsos
    └── test_contenedores.py     # orígenes reales (marcado integration)
```

Configuración asociada (`pyproject.toml`):

```toml
[tool.pytest.ini_options]
testpaths = ["tests"]
pythonpath = ["."]
addopts = "-ra -p no:faulthandler"
markers = ["integration: pruebas que necesitan servicios Docker (maria/h2/oracle)"]
```

---

## 3. Cómo ejecutar la suite

```powershell
cd r01\backend

# Suite completa (unit + integración con orígenes falsos)
.\.venv\Scripts\python.exe -m pytest

# Sólo unitarias
.\.venv\Scripts\python.exe -m pytest tests/unit

# Sólo la API
.\.venv\Scripts\python.exe -m pytest tests/integration/test_api.py

# Con verbosidad y sin las de contenedores
.\.venv\Scripts\python.exe -m pytest -m "not integration"

# Sólo las de contenedores (requiere Docker en marcha)
.\.venv\Scripts\python.exe -m pytest -m integration

# Un caso concreto
.\.venv\Scripts\python.exe -m pytest tests/integration/test_api.py::test_sincronizacion_es_idempotente

# Cobertura
.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing
```

En Linux/macOS: `python -m pytest` desde `r01/backend`.

**Salida esperada:** todos los tests en verde; los de contenedores
aparecen como `SKIPPED` si Docker no está levantado.

---

## 4. Fixtures y dobles de prueba

Definidos en `tests/conftest.py`:

| Fixture / doble | Descripción |
|-----------------|-------------|
| `FILAS` | Datos semilla: `derby` (3 filas) y `h2` (2 filas) |
| `FakeDataSource` | Origen en memoria con la misma semántica que `JdbcDataSource` (`list`, `count`, `get`, `create`, `update`, `delete`, `ping`) |
| `FakeRegistry` | Registro mínimo con `get`, `ids`, `items`, `ping`, `status`, `avisos`, `jvm_iniciada=False` |
| `fake_registry` | Fixture con `derby` + `h2` |
| `app_settings` | `Settings` con SQLite temporal (`tmp_path`), `mariadb_enabled=False`, `environment="test"` |
| `client` | `fastapi.testclient.TestClient` sobre `create_app(...)` |

Gracias a esto, `test_api.py` valida el ciclo completo **sin JVM ni
Docker**, y `jvm.arrancada` se espera `false` (registro falso inyectado).

---

## 5. Catálogo de pruebas unitarias

### `tests/unit/test_transform.py`

| Prueba | Verifica |
|--------|----------|
| `test_normalizaciones` | `normalizar_espacios`, `normalizar_dni` (mayúsculas), `normalizar_nombre` |
| `test_validar_dni_formatos_correctos` (parametrizada) | `12345678Z`, `X1234567Z`, `Y0000001T`, `12345678A` |
| `test_validar_dni_formatos_incorrectos` (parametrizada) | `1234567`, `12345678ZZ`, `1234567A`, `ABC12345Z`, `""` → `ValidacionError` |
| Transformación de fila | `transform_row` normaliza y añade `origen`; fila incompleta → error |

### `tests/unit/test_dialects.py`

| Prueba | Verifica |
|--------|----------|
| `test_get_dialect_conoce_los_cuatro_origenes` | `derby`, `hsqldb`, `h2`, `oracle` |
| `test_get_dialect_desconocido` | `mysql` → `ValidacionError` |
| `test_safe_ident_acepta_y_rechaza` | Rechaza inyección (`; DROP TABLE`), identificadores que empiezan por dígito, espacios, vacíos y `None` |

### `tests/unit/test_persistence.py`

| Prueba | Verifica |
|--------|----------|
| Upsert idempotente | Repetir el mismo lote no duplica filas |
| Cola de reintentos | `SqliteRetryQueue` encola y drena `retry_pending()` |
| Composición | `CompositeCentralRepository` orquesta SQLite (+ MariaDB si procede) |

---

## 6. Catálogo de pruebas de integración (API)

`tests/integration/test_api.py` — todas con `TestClient` y orígenes falsos:

| Prueba | Endpoint | Criterio |
|--------|----------|----------|
| `test_raiz_y_salud` | `GET /`, `GET /health` | `docs=="/docs"`; `status=="ok"`; `sqlite.disponible`; conectores `{derby,h2}`; `jvm.arrancada==false` |
| `test_listado_y_estado_de_conectores` | `GET /connectors` | Ambos conectores disponibles |
| `test_listado_paginado_y_filtros` | `GET /connectors/derby/estudiantes` | `total=3`, `limit=2`; filtros `dni`, `nombre`; `offset` fuera de rango → `items=[]` |
| `test_conector_desconocido_devuelve_404` | `GET /connectors/postgres/...` | 404 con `codigo=="C-404"` y el id en el mensaje |
| `test_limit_fuera_de_rango_devuelve_422` | `?limit=0` | 422 con `C-422` |
| `test_crud_sobre_un_origen` | POST/GET/PUT/DELETE | 201 → DNI `99887766x`→`99887766X`, nombre colapsado; PUT parcial; DELETE 204; GET posterior 404 |
| `test_alta_con_dni_invalido_devuelve_400` | `POST` | 400 `C-400` |
| `test_alta_con_cuerpo_incompleto_devuelve_422` | `POST {}` | 422 `C-422` |
| `test_sincronizacion_es_idempotente` | `POST /sync` | 1ª vez: `insertados=5` (3 derby + 2 h2), `actualizados=0`; 2ª vez: `insertados=0`, `actualizados=5` |
| `test_consulta_a_la_central` | `GET /central/estudiantes[/{dni}]` | `total=5`, `backend in {sqlite,mariadb}`, `origen=="derby"`, 404 para DNI inexistente |
| `test_consistencia_de_la_central` | `GET /central/consistencia` | `coincide=true`, `pendientes_reintento=0`, conteos `{"derby":3,"h2":2}` |
| `test_sincronizar_conector_concreto` | `POST /sync/derby`, `POST /sync/postgres` | 200 con `conector=="derby"`; 400 `C-400` para desconocido |

---

## 7. Catálogo de pruebas de integración (contenedores)

`tests/integration/test_contenedores.py`, marcador `pytest.mark.integration`.
Puertos de referencia: `derby=1527`, `hsqldb=9001`, `h2=9092`,
`oracle=1521`. Si el puerto no responde → `pytest.skip`.

| Prueba | Parámetros | Criterio |
|--------|------------|----------|
| `test_origen_responde` | 4 conectores | `registry.ping(conector)` |
| `test_jvm_arranca_una_sola_vez` | — | `registry.jvm_iniciada is True` (una JVM para los 4) |
| `test_semilla_de_diez_estudiantes` | 4 conectores | `count()==10`, página de 5, campos `id,dni,nombre,casa,patronus`, `len(dni)==9` |
| `test_crud_sobre_el_origen_real` | 4 conectores | `create` → `get` → `update` → `delete` → `get is None` (usa finally para no dejar basura) |

Precondición: `docker compose -f r01/infra/docker-compose.yml up -d`.

---

## 8. Pruebas manuales / smoke test

Guion mínimo tras cualquier cambio (Windows, PowerShell):

```powershell
# 1. Arranque
powershell -ExecutionPolicy Bypass -File r01\scripts\run_demo.ps1

# 2. Salud
(Invoke-WebRequest http://127.0.0.1:8000/health).Content

# 3. Conectores
(Invoke-WebRequest http://127.0.0.1:8000/connectors).Content

# 4. Listado paginado
(Invoke-WebRequest "http://127.0.0.1:8000/connectors/derby/estudiantes?limit=2").Content

# 5. Sincronizar
Invoke-WebRequest -Method POST http://127.0.0.1:8000/sync

# 6. Central
(Invoke-WebRequest "http://127.0.0.1:8000/central/estudiantes?limit=2").Content

# 7. Consistencia (debe decir coincide=true)
(Invoke-WebRequest http://127.0.0.1:8000/central/consistencia).Content

# 8. Re-sincronizar -> insertados=0 (idempotencia)
Invoke-WebRequest -Method POST http://127.0.0.1:8000/sync
```

Casos negativos recomendados:

```powershell
# Conector inexistente -> 404 C-404
Invoke-WebRequest "http://127.0.0.1:8000/connectors/postgres/estudiantes"

# limit fuera de rango -> 422 C-422
Invoke-WebRequest "http://127.0.0.1:8000/connectors/derby/estudiantes?limit=0"

# DNI inválido -> 400 C-400
Invoke-WebRequest -Method POST http://127.0.0.1:8000/connectors/derby/estudiantes `
  -ContentType "application/json" -Body '{"dni":"no-es-dni","nombre":"Ana","casa":"Ravenclaw","patronus":"Buhos"}'
```

Verificación visual: Swagger en `http://127.0.0.1:8000/docs`.

---

## 9. Criterios de aceptación

| # | Criterio | Comprobación |
|---|----------|--------------|
| 1 | Suite completa en verde sin Docker | `python -m pytest` |
| 2 | `GET /health` → 200 con `status=ok` | Smoke test |
| 3 | Los 4 conectores responden | `GET /connectors` + `test_origen_responde` |
| 4 | CRUD completo sobre un origen | `test_crud_sobre_un_origen` y `test_crud_sobre_el_origen_real` |
| 5 | Validación de DNI y límites | 400/422 verificados en `test_api.py` |
| 6 | Sincronización idempotente | `test_sincronizacion_es_idempotente` |
| 7 | Central con trazabilidad de origen | `test_consulta_a_la_central` (`origen=="derby"`) |
| 8 | Paridad MariaDB/SQLite | `test_consistencia_de_la_central` (`coincide=true`) |
| 9 | Conmutación por fallo | Desactivar MariaDB → la central sigue sirviendo desde SQLite |
| 10 | Errores con envelope único | Todos los 4xx/5xx con `{"error":{codigo,mensaje,detalle}}` |
| 11 | Análisis estático limpio | `ruff check .` sin errores |

---

## 10. Análisis estático y cobertura

```powershell
cd r01\backend

# Lint (configurado en pyproject.toml: línea 100, target py311)
.\.venv\Scripts\python.exe -m ruff check .

# Autocorrección de orden de imports
.\.venv\Scripts\python.exe -m ruff check --fix .

# Cobertura (source=app, omite tests)
.\.venv\Scripts\python.exe -m pytest --cov=app --cov-report=term-missing
```

Reglas relevantes de `ruff`: `E,F,I,UP,B,SIM,C4`; `E501` ignorado (se
revisa a mano); `B008` ignorado en `app/api/routers/*` (patrón FastAPI de
`Depends`/`Query`); `B011` ignorado en `tests/*` (`assert False`).

---

## 11. Cómo añadir una prueba nueva

1. **Unitaria** → `tests/unit/test_<modulo>.py`; importar desde
   `app.*` y no tocar la red ni la JVM.
2. **De endpoint** → añadir una función en
   `tests/integration/test_api.py` usando la fixture `client`. No hace
   falta Docker.
3. **Contra contenedores reales** → añadir en
   `tests/integration/test_contenedores.py` con
   `@pytest.mark.parametrize("conector", sorted(PUERTOS))` y un
   `_responde(puerto)` previo con `pytest.skip` si no está disponible.
4. Nombrar en formato `test_<qué>_devuelve_<resultado>` o
   `test_<escenario>_<aserción>`.
5. Ejecutar `pytest` + `ruff check .` antes de entregar.
