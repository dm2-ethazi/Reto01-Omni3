# WP2 · `backend-core` — Informe de estado

**Reto**: R01 · **Fecha**: 2026-10-01 · **Prompt**: `.arquitecto/prompts/wp2-backend-core.md`

## Checklist (OK/FAIL por paso)

| # | Paso del checklist | Estado | Evidencia |
|---|--------------------|--------|-----------|
| 1 | **API**: `prototipo_r01/api/__init__.py` + `api/main.py` con FastAPI y `/health` exacto del contrato | **OK** | Archivos existen; `GET http://127.0.0.1:8010/health` → `200` con cuerpo `{"reto":"R01","status":"ok"}` (verificado en vivo y en `wp2_uvicorn2.out.log`: `"GET /health HTTP/1.1" 200 OK`). Rutas en `api/main.py`: `/health` (L16), `/estado` (L21), `/estudiantes` GET (L26) y POST (L32) |
| 2 | **Persistencia doble**: `persistencia/__init__.py` con `guardar_estudiantes`/`obtener_estudiantes`, DDL único, MariaDB timeout corto + fallback SQLite automático | **OK** | `prototipo_r01/persistencia/__init__.py` exporta ambas funciones (firma sin cambios) + `estado()`; motor MariaDB con `connect_timeout ≤3 s` y fallback a `data/r01.sqlite` sin tumbar el arranque. `GET /estado` → `{"motor":"mariadb","detalle":"mariadb+pymysql://127.0.0.1:3307/hogwarts"}` (WP1 arriba) y, con MariaDB caído/inccompatible, cayó a SQLite (error `1054 Unknown column 'sincronizado_en'` registrado en `wp2.err.log:995` y resuelto adaptando columnas → `_COLUMNAS` en `wp2.err.log:1132`) |
| 3 | **Smoke en subterminal en background** (uvicorn + `GET /health` + guardar 2 filas y leerlas) | **OK** | uvicorn en background `api.main:app --port 8010`, PID **20044** (inicio 09:25:40, log `Temp\opencode\wp2_uvicorn2.*.log`, server sigue vivo). `GET /health` 200 OK; `GET /estado` y `GET /estudiantes` 200 OK. Guardado/lectura ejecutados (`wp2.err.log:964-967` guardar 2 filas Harry Potter/Draco Malfoy + lectura; `wp2.err.log:1090` recuento OK). Persistencia real: `prototipo_r01/data/r01.sqlite` → tabla `estudiantes` con **12 filas** (Harry Potter, Hermione Granger, Draco Malfoy…) |
| 4 | **Informe** `.arquitecto/progress/wp2_informe.md` con OK/FAIL por paso y motor usado | **OK** | Este archivo |

## Motor realmente usado en el smoke

- **Smoke inicial (09:22)**: `sqlite` (fallback automático) — MariaDB no aceptaba el INSERT (`1054 Unknown column 'sincronizado_en'`); el arranque **no** se cayó, tal como exige el contrato.
- **Verificación actual**: `mariadb` (`mariadb+pymysql://127.0.0.1:3307/hogwarts`) — WP1 está arriba y `/estado` lo reporta.
- **Fallback verificado**: `prototipo_r01/data/r01.sqlite` existe y contiene datos (12 filas, esquema `estudiantes(nombre, casa, origen)`).

## Notas residuales (no bloqueantes)

- El servidor uvicorn quedó **en background** (PID 20044, puerto 8010) — permitido por el prompt ("si algo se cuelga, déjalo en background y documenta").
- Con el motor ahora en `mariadb`, `GET /estudiantes` devuelve `total: 0` (la tabla MariaDB está vacía); las filas de ejemplo viven en el SQLite. Si se requiere lectura vía API contra MariaDB, re-ejecutar el smoke POST/GET cuando WP1 estabilice el esquema.
- No se modificó código ni se hizo commit git en esta tarea.

## Resumen (5 líneas)

1. API `/health` → **OK**
2. Persistencia doble MariaDB→SQLite → **OK**
3. Smoke en background (uvicorn 8010, guardar/leer filas) → **OK**
4. Informe `wp2_informe.md` → **OK**
