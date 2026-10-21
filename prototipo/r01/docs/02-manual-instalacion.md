# Manual de instalación — Reto R01

**Versión:** 1.0 · **Fecha:** 2026-10-01
**Ámbito:** `r01/backend`, `r01/infra`, `r01/scripts`

> **Trazabilidad:** este manual corresponde al punto 2 de los cinco
> manuales obligatorios establecidos por **deducción** (no existe pliego en
> el repositorio; ver `01-README.md` §5).

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Estructura de puertos](#2-estructura-de-puertos)
3. [Paso 1 — Infraestructura (Docker)](#3-paso-1--infraestructura-docker)
4. [Paso 2 — Entorno virtual de Python](#4-paso-2--entorno-virtual-de-python)
5. [Paso 3 — Configuración (`.env`)](#5-paso-3--configuración-env)
6. [Paso 4 — Configuración de conectores](#6-paso-4--configuración-de-conectores)
7. [Paso 5 — Drivers JDBC](#7-paso-5--drivers-jdbc)
8. [Paso 6 — Arranque del backend](#8-paso-6--arranque-del-backend)
9. [Verificación de la instalación](#9-verificación-de-la-instalación)
10. [Clientes (JavaFX, Android, Unity)](#10-clientes-javafx-android-unity)
11. [Problemas conocidos](#11-problemas-conocidos)
12. [Desinstalación / limpieza](#12-desinstalación--limpieza)

---

## 1. Requisitos previos

| Requisito | Versión mínima | Uso |
|-----------|----------------|-----|
| Docker Desktop | 4.x con Compose v2 | Contenedores de orígenes y MariaDB |
| Python | 3.11+ (probado con 3.12) | Backend FastAPI |
| JDK | 21+ | Drivers vía JVM (JayDeBeApi/JPype1) |
| PowerShell | 5.1+ | `r01/scripts/run_demo.ps1` |
| Maven / Gradle | 3.9 / 9.1 (en `tools/`) | Compilación de clientes Java |

Dependencias Python (fijadas en `r01/backend/requirements.txt`):
`fastapi==0.142.1`, `uvicorn==0.54.0`, `pydantic==2.13.5`,
`pydantic-settings==2.15.0`, `SQLAlchemy==2.0.54`, `PyYAML==6.0.3`,
`JayDeBeApi==1.2.3`, `JPype1==1.7.1`, `PyMySQL==1.2.3`.

---

## 2. Estructura de puertos

| Servicio | Puerto | Contenedor |
|----------|--------|------------|
| Backend FastAPI | 8000 | (proceso local) |
| Apache Derby (Gryffindor) | 1527 | `r01-derby` |
| HSQLDB (Slytherin) | 9001 | `r01-hsqldb` |
| H2 TCP (Hufflepuff) | 9092 (web 8082) | `r01-h2` |
| Oracle Free (Ravenclaw) | 1521 | `r01-oracle` |
| MariaDB (central) | 3306 | `r01-mariadb` |
| SQLite (central) | — | embebido, sin contenedor |

---

## 3. Paso 1 — Infraestructura (Docker)

Desde la raíz del proyecto:

```powershell
docker compose -f r01\infra\docker-compose.yml up -d --build
```

O directamente con el script (recomendado, también arranca el backend):

```powershell
powershell -ExecutionPolicy Bypass -File r01\scripts\run_demo.ps1 -SoloInfra
```

Comprobar el estado de salud de los cinco contenedores:

```powershell
docker ps --filter "name=r01-"
```

Todos deben figurar `healthy` (Oracle puede tardar ~30 s en el primer
arranque; tiene `healthcheck` configurado).

---

## 4. Paso 2 — Entorno virtual de Python

```powershell
cd r01\backend
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt   # sólo para pruebas
```

`requirements-dev.txt` añade `pytest`, `pytest-cov`, `httpx` y `ruff`.

---

## 5. Paso 3 — Configuración (`.env`)

```powershell
Copy-Item r01\backend\.env.example r01\backend\.env
```

Variables principales (fijadas por `app/core/config.py` → clase `Settings`):

| Variable | Ejemplo | Descripción |
|----------|---------|-------------|
| `APP_HOST` / `APP_PORT` | `0.0.0.0` / `8000` | Escucha del backend |
| `ENVIRONMENT` | `dev` | Entorno activo |
| `LOG_LEVEL` | `INFO` | Nivel de log (ficheros en `backend/logs/`) |
| `MARIADB_HOST/PORT` | `localhost` / `3306` | Host de la central |
| `MARIADB_USER/PASSWORD` | `r01` / `r01pass` | Credenciales de la central |
| `MARIADB_DATABASE` | `central` | Base de datos de la central |
| `MARIADB_ENABLED` | `true` | Si `false`, la central funciona sólo con SQLite |
| `DERBY_USER/PASSWORD` | vacíos | Derby no autentica por defecto |
| `HSQLDB_USER/PASSWORD` | `SA` / `r01pass` | Usuario SA de HSQLDB |
| `H2_USER/PASSWORD` | `sa` / `r01pass` | Usuario sa de H2 |
| `ORACLE_USER` / `ORACLE_PASSWORD_CONN` | `r01` / `r01pass` | Usuario de aplicación Oracle |

> **Seguridad:** las credenciales de ejemplo son **sólo para demo local**.
> Nunca versionar un `.env` real ni reutilizarlas en entornos productivos.

---

## 6. Paso 4 — Configuración de conectores

Fichero: `r01/backend/config/connectors.yaml` (plantilla en
`connectors.example.yaml`).

Cada entrada declara: `driver_class`, `url` (JDBC en modo red), `jars`
(rutas relativas a `backend/`), `env_user`/`env_password` (**siempre por
variable de entorno**, nunca en claro), `dialect`
(`derby|hsqldb|h2|oracle`), `table`, `columns` (mapeo modelo→columna) y
`page_size` (50 por defecto).

Conectores predefinidos:

| Id | URL JDBC | Dialecto |
|----|----------|----------|
| `derby` | `jdbc:derby://localhost:1527/r01derby` | `derby` |
| `hsqldb` | `jdbc:hsqldb:hsql://localhost:9001/r01hsqldb` | `hsqldb` |
| `h2` | `jdbc:h2:tcp://localhost:9092/r01h2` | `h2` |
| `oracle` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` | `oracle` |

Para añadir un origen basta con copiar una entrada y reiniciar el backend.

---

## 7. Paso 5 — Drivers JDBC

Los `.jar` deben existir bajo `r01/backend/drivers/` con los nombres
referenciados en el YAML:

```
drivers/derbyclient-10.16.1.1.jar
drivers/derbyshared-10.16.1.1.jar
drivers/hsqldb-2.7.3.jar
drivers/h2-2.2.224.jar
drivers/ojdbc11-23.26.3.0.0.jar
```

Si faltan, `ConnectorRegistry.load()` los omite y deja **avisos** que se
observan en `GET /health` → `jvm.avisos`.

---

## 8. Paso 6 — Arranque del backend

**Opción A — script completo (infra + backend):**

```powershell
powershell -ExecutionPolicy Bypass -File r01\scripts\run_demo.ps1
# Parámetros: -SoloInfra | -SinReconstruir | -Puerto 8000
```

El script crea `backend\.env` si no existe, espera el `200` de `/health`
y muestra `status` y conectores caídos.

**Opción B — manual:**

```powershell
cd r01\backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

La aplicación (`app/main.py:create_app`) construye en el `lifespan`:
los engines (MariaDB + SQLite), el esquema de la central, la cola de
reintentos, el `CompositeCentralRepository` y el `ConnectorRegistry`
(arranca la JVM una sola vez).

---

## 9. Verificación de la instalación

```powershell
# Salud (siempre HTTP 200; status ok|degraded)
curl http://127.0.0.1:8000/health

# Raíz
curl http://127.0.0.1:8000/

# Conectores disponibles
curl http://127.0.0.1:8000/connectors
```

Criterios de instalación correcta:

1. `GET /` devuelve `{"app": "R01 - Backend de integracion", "reto": "R01", "docs": "/docs"}`.
2. `GET /health` devuelve **200** con `"status": "ok"` y `"caidos": []`.
3. `central.sqlite.disponible == true` y `central.mariadb.disponible == true`.
4. `conectores` contiene `derby`, `hsqldb`, `h2` y `oracle` con `disponible: true`.
5. `jvm.arrancada == true`.
6. Swagger accesible en `http://127.0.0.1:8000/docs`.

---

## 10. Clientes (JavaFX, Android, Unity)

| Cliente | Instalación | Arranque |
|---------|-------------|----------|
| Desktop JavaFX | `tools/apache-maven-3.9.11` | `mvn -f pom.xml javafx:run` |
| Android | Android SDK + Gradle 9.1 (`tools/gradle-9.1.0`) | `gradle assembleDebug` |
| Unity | Unity con `r01/clients/unity-patronus/` | Abrir proyecto en Unity |

La URL por defecto del backend en el cliente JavaFX es
`http://127.0.0.1:8000` (editable en su barra superior).

---

## 11. Problemas conocidos

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `status: degraded` y un conector en `caidos` | Contenedor parado | `docker compose up -d` del servicio afectado |
| `jvm.avisos` con entradas | Falta un `.jar` en `drivers/` | Descargar drivers (§7) y reiniciar |
| `C-503` al sincronizar | MariaDB no responde | Comprobar `MARIADB_ENABLED` y el contenedor `r01-mariadb` |
| `central.mariadb` marcado caído | Puerto/credenciales incorrectos | Revisar `.env` (§5) |
| Pytest imprime trazas JNI falsas | La JVM escribe al salir de JNI | Ya mitigado: `pyproject.toml` fija `-p no:faulthandler` |
| Puerto 8000 ocupado | Proceso previo | Matar uvicorn o usar `-Puerto` |

---

## 12. Desinstalación / limpieza

```powershell
# Parar y borrar contenedores + volúmenes (borra los datos de las BDD)
docker compose -f r01\infra\docker-compose.yml down -v

# Limpiar artefactos Python
Remove-Item -Recurse r01\backend\__pycache__, r01\backend\.pytest_cache, r01\backend\.ruff_cache -ErrorAction SilentlyContinue

# Central SQLite (se regenera en el próximo arranque)
Remove-Item r01\backend\data\central.db -ErrorAction SilentlyContinue
```
