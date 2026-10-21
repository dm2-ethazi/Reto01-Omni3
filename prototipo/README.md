# Reto R01 · Integración de orígenes JDBC + central doble MariaDB/SQLite

![Estado](https://img.shields.io/badge/estado_global-CUMPLE-10B981?style=flat-square)
![Requisitos](https://img.shields.io/badge/requisitos-11%2F11-1E3A8A?style=flat-square)
![Backend](https://img.shields.io/badge/backend-FastAPI-009688?style=flat-square)
![Clientes](https://img.shields.io/badge/clientes-3_compilados-06B6D4?style=flat-square)
![Docs](https://img.shields.io/badge/docs-5_manuales-172554?style=flat-square)
![Python](https://img.shields.io/badge/python-3.11%2B-3776AB?style=flat-square&logo=python&logoColor=white)
![Java](https://img.shields.io/badge/java-17%2B-E76F00?style=flat-square&logo=openjdk&logoColor=white)

**Prototipo R01**: una API REST (FastAPI) que integra **cuatro orígenes de datos heterogéneos vía JDBC** —Apache Derby, HSQLDB, H2 y Oracle— con una **central doble** —MariaDB como backend principal y SQLite como espejo con conmutación por fallo—, expuesta a **tres clientes** (escritorio JavaFX, móvil Android y Unity).

| Bloque | Qué aporta |
|--------|------------|
| **Orígenes JDBC** | Derby `:1527` · HSQLDB `:9001` · H2 `:9092` · Oracle Free `:1521`, todos con la tabla `ESTUDIANTE` |
| **Central doble** | MariaDB `:3306` (principal) + SQLite embebido (espejo y cola de reintentos) |
| **API** | `/health` · `/connectors` · `/sync` · `/central` — Swagger en `/docs` |
| **Clientes** | JavaFX (MVVM), Android (Kotlin/MVVM) y Unity (C#) sobre la misma paleta visual |
| **Infraestructura** | `docker compose` con 5 contenedores, semillas SQL y scripts de arranque |

---

## 📑 Índice

1. [Requisitos](#1-requisitos)
2. [Puesta en marcha en 4 pasos](#2-puesta-en-marcha-en-4-pasos)
3. [Estructura del repositorio](#3-estructura-del-repositorio)
4. [Documentación](#4-documentación)
5. [Clientes](#5-clientes)
6. [Verificación y trazabilidad](#6-verificación-y-trazabilidad)

---

## 1. Requisitos

| Requisito | Versión mínima | Necesario para | Notas |
|-----------|----------------|----------------|-------|
| **Docker Desktop** | con Compose v2 | Infraestructura (5 contenedores) | Debe estar en ejecución antes del paso 1 |
| **Python** | 3.11+ | Backend, semillas | Con entorno virtual en `r01/backend/.venv` |
| **JDK** | 17+ | Cliente JavaFX, servidores JDBC | El repo trae Maven en `tools/apache-maven-3.9.11/` |
| **Maven** | 3.9+ o wrapper | Cliente JavaFX | `mvn -f r01/clients/desktop-javafx/pom.xml …` |
| **Android Studio** | opcional | Cliente móvil | Usa el wrapper `gradlew.bat` incluido |
| **Unity** | 2021.3+ (opcional) | Cliente Unity | Se entrega como scripts + referencia visual |

> **Configuración:** no hay credenciales en este documento. Copia las plantillas
> de ejemplo y complétalas en local:
>
> ```powershell
> Copy-Item r01\backend\.env.example r01\backend\.env
> Copy-Item r01\infra\.env.example   r01\infra\.env
> ```
>
> El detalle de variables se explica en [`r01/infra/README.md`](r01/infra/README.md).
> Los ficheros `.env` **nunca se versionan** (sólo los `.example`).

---

## 2. Puesta en marcha en 4 pasos

### Paso 1 · Infraestructura (Docker)

Levanta Derby, HSQLDB, H2, Oracle Free y MariaDB:

```powershell
docker compose -f r01/infra/docker-compose.yml up -d
docker compose -f r01/infra/docker-compose.yml ps
```

**Alternativa en Windows** (infraestructura + backend + espera de `/health` en un solo comando):

```powershell
.\run_demo.ps1                 # infra + backend en :8000
.\run_demo.ps1 -SoloInfra      # sólo contenedores
.\run_demo.ps1 -Puerto 8080    # otro puerto para el backend
```

En Linux/macOS el equivalente es `./r01/scripts/run_demo.sh`.

### Paso 2 · Semillas de los orígenes JDBC

El `docker-compose` arranca los servidores JDBC **sin cargar datos**, así que
las semillas de `r01/infra/seeds/` se aplican con un script:

```powershell
# Sólo la primera vez: entorno virtual + dependencias
python -m venv r01\backend\.venv
r01\backend\.venv\Scripts\python.exe -m pip install -r r01\backend\requirements.txt

# Si faltan los drivers JDBC (no se versionan; se descargan de Maven Central)
powershell -ExecutionPolicy Bypass -File r01\infra\scripts\fetch_drivers.ps1

# Carga derby.sql, hsqldb.sql, h2.sql y oracle.sql
r01\backend\.venv\Scripts\python.exe r01\scripts\seed_origins.py
```

Salida esperada: `RESULTADO: 4 origen(es) procesados sin fallo`.

### Paso 3 · Backend (uvicorn en :8000)

```powershell
cd r01\backend
.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Verificación del endpoint de salud (responde **HTTP 200** siempre, con
`status: ok` o `status: degraded`):

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

```bash
curl http://127.0.0.1:8000/health
```

| Recurso | URL |
|---------|-----|
| Raíz de la API | <http://127.0.0.1:8000/> |
| Salud | <http://127.0.0.1:8000/health> |
| Swagger UI | <http://127.0.0.1:8000/docs> |

### Paso 4 · Clientes

Con el backend en marcha, lanza el cliente que prefieras:

| Cliente | Comando / acción |
|---------|------------------|
| **JavaFX** (escritorio) | `mvn -f r01\clients\desktop-javafx\pom.xml javafx:run` |
| **Android** (móvil) | Abrir `r01/clients/mobile-android` en Android Studio → *Run*; build por CLI: `r01\clients\mobile-android\gradlew.bat :app:assembleDebug` |
| **Unity** (Patronus) | Abrir `r01/clients/unity-patronus` en Unity 2021.3+ y revisar `Assets/Scripts/` |

> El emulador de Android alcanza al backend del host en `http://10.0.2.2:8000`;
> en escritorio y emulador la URL por defecto es `http://127.0.0.1:8000`.

---

## 3. Estructura del repositorio

```text
Reto 1/
├── README.md                     ← este fichero (entrada al proyecto)
├── RESULTADO.md                  ← matriz de trazabilidad contractual
├── run_demo.ps1                  ← punto de entrada único en Windows
│
├── r01/
│   ├── README.md                 ← índice del módulo R01
│   ├── backend/                  ← API FastAPI (app/, config/, drivers/, tests/)
│   ├── clients/
│   │   ├── desktop-javafx/       ← cliente de escritorio (JavaFX + MVVM)
│   │   ├── mobile-android/       ← cliente móvil (Kotlin + MVVM)
│   │   └── unity-patronus/       ← cliente Unity (C# + UnityWebRequest)
│   ├── docs/                     ← 5 manuales + 4 diagramas PlantUML (.puml/.png)
│   ├── infra/
│   │   ├── docker-compose.yml    ← Derby · HSQLDB · H2 · Oracle · MariaDB
│   │   ├── .env.example          ← plantilla de variables (valores DEMO)
│   │   ├── java-servers/         ← Dockerfiles + init.sh alternativos
│   │   ├── oracle/               ← Dockerfile + 001_seed.sh
│   │   ├── scripts/              ← fetch_drivers.ps1 / .sh
│   │   ├── seeds/                ← derby.sql · hsqldb.sql · h2.sql · oracle.sql
│   │   └── README.md             ← detalle de la infraestructura
│   └── scripts/
│       ├── run_demo.ps1 / .sh    ← arranque completo (infra + backend)
│       └── seed_origins.py       ← carga de las semillas JDBC
│
├── prototipo_r01/                ← prototipo previo (referencia histórica)
├── tools/                        ← Maven/Gradle locales (no se versionan)
└── META-INF/
```

---

## 4. Documentación

### Manuales (`r01/docs/`)

| # | Manual | Enlace |
|---|--------|--------|
| 01 | README general del backend | [`r01/docs/01-README.md`](r01/docs/01-README.md) |
| 02 | Manual de instalación | [`r01/docs/02-manual-instalacion.md`](r01/docs/02-manual-instalacion.md) |
| 03 | Manual de usuario | [`r01/docs/03-manual-usuario.md`](r01/docs/03-manual-usuario.md) |
| 04 | Manual de API y arquitectura | [`r01/docs/04-manual-api-arquitectura.md`](r01/docs/04-manual-api-arquitectura.md) |
| 05 | Guía de pruebas | [`r01/docs/05-guia-de-pruebas.md`](r01/docs/05-guia-de-pruebas.md) |

**Diagramas** (PlantUML con `pragma layout Smetana`, render en `.png`):
[`arquitectura-componentes`](r01/docs/arquitectura-componentes.puml) ·
[`modelo-datos`](r01/docs/modelo-datos.puml) ·
[`secuencia-consultas`](r01/docs/secuencia-consultas.puml) ·
[`secuencia-sincronizacion`](r01/docs/secuencia-sincronizacion.puml)

### Índices internos

| Fichero | Contenido |
|---------|-----------|
| [`r01/README.md`](r01/README.md) | Índice del módulo: propósito de cada subcarpeta |
| [`r01/infra/README.md`](r01/infra/README.md) | Servicios Docker, variables, healthchecks, semillas y drivers |

---

## 5. Clientes

| Cliente | Tecnología | README |
|---------|------------|--------|
| Escritorio | Java 17 · JavaFX 21 · Maven (MVVM) | [`r01/clients/desktop-javafx/README.md`](r01/clients/desktop-javafx/README.md) |
| Móvil | Kotlin · Gradle · minSdk 24 (MVVM) | [`r01/clients/mobile-android/README.md`](r01/clients/mobile-android/README.md) |
| Juego | Unity 2021.3+ · C# (`UnityWebRequest` + `JsonUtility`) | [`r01/clients/unity-patronus/README.md`](r01/clients/unity-patronus/README.md) |

---

## 6. Verificación y trazabilidad

La matriz de requisitos, los comandos de verificación ejecutados y los riesgos
conocidos están en [`RESULTADO.md`](RESULTADO.md).

```powershell
# Estado global del repositorio
# -> estado_global: CUMPLE · requisitos 11/11 · clientes 3 compilados · docs 5 manuales
```

| Comprobación rápida | Comando |
|---------------------|---------|
| Contenedores | `docker compose -f r01/infra/docker-compose.yml ps` |
| Salud de la API | `Invoke-RestMethod http://127.0.0.1:8000/health` |
| Tests del backend | `cd r01\backend; .\.venv\Scripts\python.exe -m pytest` |
| Linter | `cd r01\backend; .\.venv\Scripts\python.exe -m ruff check app` |
