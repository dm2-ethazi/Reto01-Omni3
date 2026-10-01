# R01 — Backend de integración (README general)

> ### Reto R01 · Documento 1 de 5
> **Versión:** 1.0 · **Fecha:** 2026-10-01 · **Estado:** ✅ Entregado
>
> **Componente documentado:** `r01/backend` (FastAPI) · `r01/infra` · `r01/clients`

![Reto](https://img.shields.io/badge/Reto-R01-1E3A8A?style=flat-square)
![Backend](https://img.shields.io/badge/backend-FastAPI-009688?style=flat-square)
![Docs](https://img.shields.io/badge/docs-5_manuales-06B6D4?style=flat-square)
![UML](https://img.shields.io/badge/UML-4_diagramas_Smetana-172554?style=flat-square)
![Estado](https://img.shields.io/badge/estado-CUMPLE-10B981?style=flat-square)

> [!IMPORTANT]
> **Nota de trazabilidad documental.**
> Se ha buscado activamente el pliego/especificación del Reto R01 en
> `META-INF/`, `tools/`, `r01/`, ficheros `*.md`, nombres tipo *pliego*,
> *reto*, *spec* o *sección 8*, y en el README de la raíz. **No se ha
> encontrado ningún pliego en el repositorio.** En consecuencia, los cinco
> manuales obligatorios de este directorio se han establecido por
> **deducción del equipo** siguiendo la convención estándar de entrega
> académica (ver §5). Si el pliego oficial define otra nomenclatura, habrá
> que renombrar/ajustar estos documentos.

---

## 📑 Índice

| § | Sección | § | Sección |
|---|---------|---|---------|
| 1 | [Objetivo del proyecto](#1-objetivo-del-proyecto) | 5 | [Origen de la lista de manuales](#5-origen-de-la-lista-de-manuales-deducción) |
| 2 | [Arquitectura en una página](#2-arquitectura-en-una-página) | 6 | [Arranque rápido](#6-arranque-rápido) |
| 3 | [Estructura del repositorio](#3-estructura-del-repositorio) | 7 | [Estado de los componentes](#7-estado-de-los-componentes) |
| 4 | [Los cinco manuales obligatorios](#4-los-cinco-manuales-obligatorios) | | |

**Anexos técnicos del backend** (añadidos al índice):

| # | Documento | Contenido |
|---|-----------|-----------|
| 06 | [`06-arquitectura-backend.md`](06-arquitectura-backend.md) | Recorrido de una petición de extremo a extremo, diagrama `.puml` (`pragma layout Smetana`) y ciclo de vida de `create_app` |
| 07 | [`07-piezas-complejas.md`](07-piezas-complejas.md) | Deep dive: sincronización, central doble y cola de reintentos, JVM/dialectos JDBC y núcleo `core`/`api` |

---

## 1. Objetivo del proyecto

Integrar **cuatro bases de datos heterogéneas de origen** (Apache Derby,
HSQLDB, H2 y Oracle) con una **central doble** (MariaDB/MySQL + SQLite),
mediante una API REST que permita:

| # | Capabilidad | Endpoint |
|---|-------------|----------|
| 1 | Consultar y administrar estudiantes en cada origen | `/connectors` |
| 2 | **Sincronizar** los orígenes hacia la central con trazabilidad de origen | `/sync` |
| 3 | Consultar la central de forma unificada, con conmutación por fallo | `/central` |
| 4 | Verificar la salud del sistema completo | `/health` |

El modelo de dominio es un único registro de estudiante con cinco campos:
`id`, `dni`, `nombre`, `casa` y `patronus`.

---

## 2. Arquitectura en una página

```text
                       ┌───────────────────────────────────┐
                       │                                   │
    ┌──────────┐       │        r01/backend (FastAPI)      │
    │ JavaFX   │──────▶│  /health  /connectors  /sync      │
    │ Android  │ HTTP  │              │         │          │
    │ Unity    │──────▶│              ▼         ▼          │
    └──────────┘       │     ConnectorService   Central    │
                       │            │        Service       │
                       │     ConnectorRegistry   │         │
                       │     (JVM + JayDeBeApi)  ▼         │
                       │            │   CompositeCentral   │
                       │            │       Repository     │
                       └────────────┼───────────┼──────────┘
                                    ▼           ▼
                    ┌────────────────────────────────────────┐
                    │  Derby  HSQLDB  H2  Oracle │ MariaDB  SQLite
                    │  (4 orígenes JDBC)        │(principal)(espejo)
                    └────────────────────────────────────────┘
```

> **Diagramas completos** (PlantUML, render con `pragma layout Smetana`):
>
> | Diagrama | Archivo |
> |----------|---------|
> | Componentes | `arquitectura-componentes.puml` |
> | Secuencia de sincronización | `secuencia-sincronizacion.puml` |
> | Secuencia de consultas | `secuencia-consultas.puml` |
> | Modelo de datos | `modelo-datos.puml` |

---

## 3. Estructura del repositorio

| Ruta | Contenido |
|------|-----------|
| `r01/backend/app/api/routers/` | Routers: `health.py`, `connectors.py`, `sync.py`, `central.py` |
| `r01/backend/app/api/schemas/` | Esquemas Pydantic v2: `estudiante.py`, `salud.py` |
| `r01/backend/app/services/` | `connector_service.py`, `sync_service.py`, `central_service.py`, `transform.py` |
| `r01/backend/app/data_access/` | `registry.py`, `jdbc_connector.py`, `dialects.py` (capa JDBC/JVM) |
| `r01/backend/app/persistence/` | Modelos, engines y repositorios de la central |
| `r01/backend/config/connectors.yaml` | Definición de los cuatro conectores |
| `r01/backend/tests/` | Suite `unit/` e `integration/` |
| `r01/infra/docker-compose.yml` | Contenedores Derby, HSQLDB, H2, Oracle, MariaDB |
| `r01/scripts/run_demo.ps1` | Arranque completo (infra + backend) |
| `r01/clients/` | JavaFX (MVVM), Android, Unity |
| `r01/docs/` | Los cinco manuales + diagramas UML |

---

## 4. Los cinco manuales obligatorios

| # | Fichero | Cubre | Estado |
|---|---------|-------|--------|
| 1 | [`01-README.md`](01-README.md) | Este documento: visión global, estructura y estado | ✅ |
| 2 | [`02-manual-instalacion.md`](02-manual-instalacion.md) | Requisitos, despliegue de infraestructura, configuración y arranque | ✅ |
| 3 | [`03-manual-usuario.md`](03-manual-usuario.md) | Uso operativo de la API y de los clientes, con ejemplos curl | ✅ |
| 4 | [`04-manual-api-arquitectura.md`](04-manual-api-arquitectura.md) | Referencia completa de endpoints, esquemas, errores y diseño | ✅ |
| 5 | [`05-guia-de-pruebas.md`](05-guia-de-pruebas.md) | Suite pytest, marcadores, ejecución y criterios de aceptación | ✅ |

---

## 5. Origen de la lista de manuales (deducción)

**No existe pliego en el repositorio.** La lista anterior es una
**deducción del equipo de documentación**, basada en:

1. El encargo registrado en `orquestar_empleados.ps1` (línea 6):
   *«Redacta los 5 manuales obligatorios .md en docs/ usando layout
   Smetana en .puml»* — confirma el **número** de manuales (5) pero no sus
   nombres.
2. La convención estándar de entrega académica: README, instalación,
   usuario, API/arquitectura y pruebas.

Si el pliego oficial aparece, habrá que validar los cinco nombres y citar
en este punto el requisito exacto de cada uno.

---

## 6. Arranque rápido

```powershell
# 1) Infraestructura + backend (Windows)
powershell -ExecutionPolicy Bypass -File r01\scripts\run_demo.ps1

# 2) Comprobación
curl http://127.0.0.1:8000/health

# 3) Interfaz interactiva
# http://127.0.0.1:8000/docs
```

Detallado en [`02-manual-instalacion.md`](02-manual-instalacion.md).

---

## 7. Estado de los componentes

| Componente | Estado | Documentado en |
|------------|:------:|----------------|
| API FastAPI (4 routers) | ✅ Funcional | `04-manual-api-arquitectura.md` |
| Conectores JDBC (4 orígenes) | ✅ Funcional | `04-manual-api-arquitectura.md` |
| Central doble MariaDB + SQLite | ✅ Funcional | `04-manual-api-arquitectura.md` |
| Suite de pruebas pytest | ✅ Funcional | `05-guia-de-pruebas.md` |
| Cliente JavaFX (MVVM) | ✅ Funcional + tema visual | `03-manual-usuario.md` |
| Cliente Android (Kotlin) | ✅ Funcional + tema visual | `03-manual-usuario.md` |
| Cliente Unity | ✅ API + tema visual | `03-manual-usuario.md` |
| Documentación (estos 5 manuales) | ✅ Entregado | — |

---

**Identidad visual compartida por los tres clientes**
(`res/values/colors.xml` · `estilos.css` · `TemaR01.cs`):

| Rol | Hex | Rol | Hex |
|-----|-----|-----|-----|
| Primario | `#1E3A8A` | Fondo | `#F1F5F9` |
| Primario oscuro | `#172554` | Superficie | `#FFFFFF` |
| Acento | `#06B6D4` | Texto | `#0F172A` |
| Éxito | `#10B981` | Texto secundario | `#64748B` |
