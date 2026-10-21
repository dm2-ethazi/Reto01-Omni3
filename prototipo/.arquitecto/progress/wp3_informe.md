# WP3 · sync-service — Informe (Ola 1 · Reto R01)

**Estado general: OK** · Persistencia usada: **fallback temporal** (`servicios/_persistencia_fallback.py`, `# fallback temporal WP2`) — `persistencia/` sigue vacío al finalizar (WP2 en paralelo).

## Checklist (OK/FAIL por paso)

| # | Paso | Estado | Evidencia |
|---|------|--------|-----------|
| 1 | Adaptador de acceso: `acceso_datos/__init__.py` + `acceso_datos/fuentes.py` (4 orígenes mock, ~3 estudiantes de Hogwarts c/u, sin duplicados; claves `nombre`, `casa`, `origen`) | **OK** | `leer_origen(nombre) -> list[dict]`; orígenes `derby`, `hsqldb`, `h2`, `oracle` |
| 2 | Servicio cocina: `servicios/__init__.py` + `servicios/cocina.py` con `sincronizar() -> dict` y bloque `__main__` que imprime JSON; import de `persistencia` protegido con `try/except` → fallback | **OK** | `{"por_origen": {...}, "total": n}` + `persistencia: "fallback"` |
| 3 | Smoke end-to-end en subterminal en background (≤3 min, sin bloquear) | **OK** | PID 15748 en background; log `.arquitecto/progress/wp3_smoke.log`; terminó en <5 s |
| 4 | Verificación de filas consultables en persistencia (SQLite estándar) | **OK** | `prototipo_r01/data/r01.sqlite`: **12 filas** (3 por origen: derby, hsqldb, h2, oracle) |

## Detalle

- Ejecutable: `python -m servicios.cocina` desde `prototipo_r01/` (solo stdlib).
- Salida del smoke:
  ```json
  {"por_origen": {"derby": 3, "hsqldb": 3, "h2": 3, "oracle": 3},
   "total": 12, "persistencia": "fallback"}
  ```
- Verificación SQL: `select count(*) from estudiantes` → `12`; agrupado por origen → 3/3/3/3.
- Fallback: misma firma `guardar_estudiantes(filas) -> int`, esquema `estudiantes(nombre, casa, origen)` con PK `(origen, nombre)`, destino `data/r01.sqlite`. **Sin cambios fuera del alcance WP3** (no se tocó `docker-compose.yml`, `api/`, `persistencia/`, `clientes_mock/`, `r01/`; sin commits).
