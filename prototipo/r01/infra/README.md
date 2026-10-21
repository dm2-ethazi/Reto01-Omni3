# R01 · Infraestructura (Docker)

![Infra](https://img.shields.io/badge/infra-Docker__Compose-2496ED?style=flat-square)
![Servicios](https://img.shields.io/badge/servicios-5_contenedores-1E3A8A?style=flat-square)
![SQLite](https://img.shields.io/badge/central-SQLite_embebido-003B57?style=flat-square)
![Healthchecks](https://img.shields.io/badge/healthchecks-oracle__2B__mariadb-10B981?style=flat-square)

Compose arranca los **cinco servidores de datos** del Reto R01 en la red
interna `r01` (proyecto `r01-databases`). La **central doble** se completa con
**SQLite**, que no es un servicio: va embebido en el proceso del backend
(`r01/backend/data/central.db`).

> Vuelve al [índice del módulo](../README.md) o al [README de la raíz](../../README.md).

---

## 📑 Índice

1. [Servicios](#1-servicios)
2. [Arranque](#2-arranque)
3. [Variables de entorno (`.env.example`)](#3-variables-de-entorno-envexample)
4. [Healthchecks](#4-healthchecks)
5. [Semillas (`seeds/`)](#5-semillas-seeds)
6. [Drivers JDBC (`scripts/fetch_drivers.ps1`)](#6-drivers-jdbc-scriptsfetch_driversps1)
7. [Qué no se versiona](#7-qué-no-se-versiona)

---

## 1. Servicios

Fichero: [`docker-compose.yml`](docker-compose.yml)

| Servicio | Contenedor | Imagen | Puerto(s) | Datos | Rol |
|----------|-----------|--------|-----------|-------|-----|
| `derby` | `r01-derby` | `maven:3.9-eclipse-temurin-21` (arranca Derby 10.17) | **1527** | volúmenes `derby_data`, `derby_libs` | Origen JDBC — *Gryffindor* |
| `hsqldb` | `r01-hsqldb` | `maven:3.9-eclipse-temurin-21` (arranca HSQLDB 2.7.4) | **9001** | volúmenes `hsqldb_data`, `hsqldb_libs` | Origen JDBC — *Slytherin* |
| `h2` | `r01-h2` | `maven:3.9-eclipse-temurin-21` (arranca H2 2.5.252) | **9092** (TCP) · **8082** (consola web) | volúmenes `h2_data`, `h2_libs` | Origen JDBC — *Hufflepuff* |
| `oracle` | `r01-oracle` | `gvenzl/oracle-free:23-slim` | **1521** | volumen `oracle_data` | Origen JDBC — *Ravenclaw* (PDB `FREEPDB1`) |
| `mariadb` | `r01-mariadb` | `mariadb:11.8` | **3306** | volumen `mariadb_data` | **Central principal** (`r01_central` / usuario de aplicación) |
| — *(embebido)* | — | — | — | `r01/backend/data/central.db` | **Central espejo** SQLite: conmutación por fallo + cola de reintentos |

Los tres contenedores Java (`derby`, `hsqldb`, `h2`) descargan su jar en el
primer arranque con Maven y lo guardan en un volumen dedicado, por lo que no
precisan imagen propia.

---

## 2. Arranque

Desde la **raíz del repositorio**:

```powershell
# 1) Variables de entorno (sólo copiar la plantilla y completarla)
Copy-Item r01\infra\.env.example r01\infra\.env

# 2) Levantar los 5 servicios
docker compose -f r01/infra/docker-compose.yml up -d

# 3) Estado
docker compose -f r01/infra/docker-compose.yml ps
docker compose -f r01/infra/docker-compose.yml logs -f oracle   # o el que necesites
```

Desde este directorio:

```powershell
docker compose up -d
docker compose ps
```

**Atajo en Windows** (infra + backend + espera de `/health`):
[`../scripts/run_demo.ps1`](../scripts/run_demo.ps1) — o [`../../run_demo.ps1`](../../run_demo.ps1)
en la raíz. Flags: `-SoloInfra`, `-SinReconstruir`, `-Puerto`.

Apagado:

```powershell
docker compose -f r01/infra/docker-compose.yml down        # conserva datos
docker compose -f r01/infra/docker-compose.yml down -v     # borra volúmenes
```

---

## 3. Variables de entorno (`.env.example`)

Plantilla: [`.env.example`](.env.example). Cópiala a `infra/.env`; Compose la
lee automáticamente desde el directorio del fichero de composición.

> [!WARNING]
> Las credenciales de la plantilla son **DEMO locales para desarrollo**.
> **Nunca** uses las credenciales reales de ningún entorno, no las versiones
> y no las compartas. El fichero `.env` real está en `.gitignore`.

**Variables que lee `docker-compose.yml`:**

| Variable | Obligatoria | Default | Uso |
|----------|-------------|---------|-----|
| `MARIADB_ROOT_PASSWORD` | ✅ (`:?`) | — | Password root de MariaDB |
| `MARIADB_DATABASE` | ❌ | `r01_central` | Base de datos creada en el primer arranque |
| `MARIADB_USER` | ❌ | `r01` | Usuario de aplicación |
| `MARIADB_PASSWORD` | ✅ (`:?`) | — | Password del usuario de aplicación |
| `ORACLE_PASSWORD` | ✅ (`:?`) | — | Password de `SYSTEM` en Oracle Free |
| `ORACLE_USER` | ❌ | `ravenclaw` | Usuario de aplicación creado por la imagen |
| `ORACLE_USER_PASSWORD` | ✅ (`:?`) | — | Password del usuario de aplicación |

**Plantilla de ejemplo** (sólo valores DEMO, renómbralos según la tabla anterior):

```env
# r01/infra/.env  —  DEMO / local.  Nunca usar en producción.
MARIADB_ROOT_PASSWORD=r01root
MARIADB_DATABASE=r01_central
MARIADB_USER=r01
MARIADB_PASSWORD=r01pass

ORACLE_PASSWORD=r01root
ORACLE_USER=r01
ORACLE_USER_PASSWORD=r01pass
```

> [!NOTE]
> El fichero [`.env.example`](.env.example) nombra las variables de Oracle como
> `ORACLE_APP_USER` / `ORACLE_APP_PASSWORD`; **Compose lee** `ORACLE_USER` /
> `ORACLE_USER_PASSWORD`, así que al copiar la plantilla hay que renombrarlas.
> Las variables `MARIADB_PORT`, `ORACLE_PORT`, `DERBY_PORT`, `HSQLDB_PORT` y
> `H2_PORT` del ejemplo son **informativas**: el compose fija los puertos de la
> tabla de la §1.
>
> El backend consume además su propia plantilla:
> [`../backend/.env.example`](../backend/.env.example) → `../backend/.env`.

---

## 4. Healthchecks

Sólo **dos** servicios definen `healthcheck` en el compose:

| Servicio | Test | Intervalo / timeout | Reintentos | `start_period` |
|----------|------|---------------------|------------|----------------|
| `oracle` | `CMD healthcheck.sh` (imagen `gvenzl/oracle-free`) | 10 s / 5 s | 20 | 30 s |
| `mariadb` | `mariadb-admin ping -h 127.0.0.1 -u… -p… --silent` | 10 s / 5 s | 10 | 20 s |

`derby`, `hsqldb` y `h2` **no declaran healthcheck**: su disponibilidad se
comprueba desde el backend con `GET /health` (el script
[`../scripts/run_demo.ps1`](../scripts/run_demo.ps1) tolera ese caso y muestra
`sin-healthcheck`).

---

## 5. Semillas (`seeds/`)

| Fichero | Origen | Contenido |
|---------|--------|-----------|
| [`seeds/derby.sql`](seeds/derby.sql) | Apache Derby | `CREATE TABLE ESTUDIANTE` + 10 registros |
| [`seeds/hsqldb.sql`](seeds/hsqldb.sql) | HSQLDB | Ídem (fija la contraseña de `SA`) |
| [`seeds/h2.sql`](seeds/h2.sql) | H2 | Ídem |
| [`seeds/oracle.sql`](seeds/oracle.sql) | Oracle Free | Ídem (con privilegios sobre el usuario de aplicación) |

Todas usan el mismo modelo: `ESTUDIANTE(ID, DNI, NOMBRE, CASA, PATRONUS)`.

### ¿Cómo se cargan hoy?

**El compose NO cablea los `init.sh`**: existen Dockerfiles y scripts de
inicialización en [`java-servers/`](java-servers/) (`derby/`, `hsqldb/`,
`h2/`, `common/Seeder.java`) y en [`oracle/`](oracle/) (`001_seed.sh`), pero
los servicios del compose arrancan los servidores JDBC **sin ejecutarlos**.

Por eso, la carga se hace con el script Python
[`../scripts/seed_origins.py`](../scripts/seed_origins.py), que usa los mismos
drivers y conectores que el backend
([`../backend/config/connectors.yaml`](../backend/config/connectors.yaml)):

```powershell
# Desde la raíz del repositorio, con los contenedores levantados
r01\backend\.venv\Scripts\python.exe r01\scripts\seed_origins.py

# Sólo algunos orígenes (argumentos opcionales)
r01\backend\.venv\Scripts\python.exe r01\scripts\seed_origins.py derby h2
```

- Carga `derby.sql`, `hsqldb.sql`, `h2.sql` y `oracle.sql` en ese orden.
- Lee `infra/.env` para las credenciales de Oracle y `backend/.env` para el resto.
- Es **idempotente en la práctica**: si una semilla ya existe, la sentencia
  duplicada se reporta como fallo de ese origen.
- Salida correcta: `RESULTADO: 4 origen(es) procesados sin fallo`.

---

## 6. Drivers JDBC (`scripts/fetch_drivers.ps1`)

Los drivers **no se versionan**; se descargan de Maven Central:

```powershell
# Windows (desde la raíz)
powershell -ExecutionPolicy Bypass -File r01\infra\scripts\fetch_drivers.ps1

# Linux/macOS
./r01/infra/scripts/fetch_drivers.sh
```

| Artefacto | Versión por defecto | Variable para sobrescribir |
|-----------|---------------------|----------------------------|
| `derbyclient` + `derbyshared` | 10.16.1.1 | `DERBY_VERSION` |
| `hsqldb` | 2.7.3 | `HSQLDB_VERSION` |
| `h2` | 2.2.224 | `H2_VERSION` |
| `ojdbc11` | 23.26.3.0.0 | `OJDBC_VERSION` |

Destino: `r01/backend/drivers/` (los jar que ya existen se omiten).

---

## 7. Qué no se versiona

Definido en [`.gitignore`](../../.gitignore):

| Ruta | Motivo |
|------|--------|
| `tools/` | Distribuciones binarias de Maven y Gradle; se descargan o se instalan en el equipo. |
| `r01/backend/drivers/` | Jar JDBC (~50 MB); se regeneran con `scripts/fetch_drivers.ps1`. |
| `r01/infra/.env` y `r01/backend/.env` | Credenciales reales; sólo se versionan los `.env.example`. |
| `*.db`, `*.sqlite` | Datos locales (incluida la central SQLite). |
| `target/`, `build/`, `.gradle/`, `.venv/` | Artefactos de build y entornos virtuales. |

> Si clonas el repo y faltan los jar, ejecuta `fetch_drivers.ps1` **antes** de
> arrancar el backend o `seed_origins.py` fallará al cargar los conectores.
