# R01 · Índice del módulo

![Módulo](https://img.shields.io/badge/módulo-R01-1E3A8A?style=flat-square)
![Backend](https://img.shields.io/badge/backend-FastAPI-009688?style=flat-square)
![Infra](https://img.shields.io/badge/infra-Docker__Compose-2496ED?style=flat-square)
![Clientes](https://img.shields.io/badge/clientes-3-06B6D4?style=flat-square)
![Docs](https://img.shields.io/badge/docs-5_manuales-172554?style=flat-square)

Este directorio contiene la entrega completa del **Reto R01**: integración de
cuatro orígenes JDBC (Derby, HSQLDB, H2, Oracle) con una central doble
(MariaDB + SQLite), su API REST y los tres clientes que la consumen.

> Vuelve al [README de la raíz](../README.md) para el arranque rápido o consulta
> la [matriz de trazabilidad](../RESULTADO.md).

---

## 📁 Subcarpetas

| Carpeta | Propósito | Enlace |
|---------|-----------|--------|
| `backend/` | API REST FastAPI: registro de conectores JDBC, central doble con conmutación por fallo, servicio de sincronización y tests (pytest + ruff). | [`backend/`](backend/) |
| `clients/` | Los tres clientes de la entrega (escritorio JavaFX, móvil Android y Unity) sobre la misma API y paleta visual. | [`clients/`](clients/) |
| `docs/` | Los cinco manuales obligatorios (`.md`) y los cuatro diagramas PlantUML con su render `.png`. | [`docs/01-README.md`](docs/01-README.md) |
| `infra/` | Composición Docker de los cinco servidores de datos, plantillas de variables, semillas SQL y script de descarga de drivers JDBC. | [`infra/README.md`](infra/README.md) |
| `scripts/` | Arranque único del demo (`run_demo`) y carga manual de las semillas de los orígenes (`seed_origins.py`). | [`scripts/`](scripts/) |

---

## 🧩 Clientes

| Cliente | Carpeta | Propósito | README |
|---------|---------|-----------|--------|
| Escritorio | `clients/desktop-javafx/` | Cliente JavaFX con patrón MVVM puro (`es.r01.desktop`) y FXML. | [`README.md`](clients/desktop-javafx/README.md) |
| Móvil | `clients/mobile-android/` | Cliente Android en Kotlin con MVVM ligero (`es.r01.mobile`), sin frameworks. | [`README.md`](clients/mobile-android/README.md) |
| Juego | `clients/unity-patronus/` | Cliente Unity en C# (`R01.Patronus`) con `UnityWebRequest` + `JsonUtility`. | [`README.md`](clients/unity-patronus/README.md) |

---

## 📚 Manuales (`docs/`)

| # | Documento | Enlace |
|---|-----------|--------|
| 01 | README general del backend | [`docs/01-README.md`](docs/01-README.md) |
| 02 | Manual de instalación | [`docs/02-manual-instalacion.md`](docs/02-manual-instalacion.md) |
| 03 | Manual de usuario | [`docs/03-manual-usuario.md`](docs/03-manual-usuario.md) |
| 04 | Manual de API y arquitectura | [`docs/04-manual-api-arquitectura.md`](docs/04-manual-api-arquitectura.md) |
| 05 | Guía de pruebas | [`docs/05-guia-de-pruebas.md`](docs/05-guia-de-pruebas.md) |

---

## ⚡ Arranque exprés

```powershell
# Desde la raíz del repositorio
.\run_demo.ps1                                    # infra + backend (:8000)

# Semillas de los orígenes JDBC (si aún no se han cargado)
r01\backend\.venv\Scripts\python.exe r01\scripts\seed_origins.py

# Verificación
Invoke-RestMethod http://127.0.0.1:8000/health
```

Detalle de la infraestructura (servicios, puertos, healthchecks, `.env` y
drivers): [`infra/README.md`](infra/README.md).
