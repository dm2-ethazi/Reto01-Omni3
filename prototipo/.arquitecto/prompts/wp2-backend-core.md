# WP2 · `backend-core` — Prompt de ejecución (Ola 1 · Reto R01)

**ROL**: Worker de `@arquitecto_dios`. Prototipo funcional **mínimo**, nada de pulido estético.
**LÍMITE CRÍTICO**: 3 minutos por subterminal. Tareas pequeñas.

## ORDEN EXPLÍCITA DE PARALELIZACIÓN (OBLIGATORIA)

- Usa **subterminales EN PARALELO sin bloquear los hilos principales**:
  - Deja `pip install fastapi uvicorn pymysql sqlalchemy` corriendo en **segundo plano** (`Start-Process pip ... -PassThru`, u `opencode run --agent general "<subpaso>"` en proceso aparte) **mientras escribes el código**; revisa el log al final.
  - Pruebas de red (uvicorn + curl/urllib): lánzalas en background y sigue con el siguiente paso; **nunca esperes de forma bloqueante** más de ~30 s en un solo comando.
  - Ejecuta en paralelo todo lo que no compita por el mismo archivo.

## ALCANCE (no tocar nada fuera)

- Tuyos: `prototipo_r01/api/**`, `prototipo_r01/persistencia/**`, `prototipo_r01/data/**`.
- Prohibido: `docker-compose.yml`, `servicios/`, `acceso_datos/`, `clientes_mock/`, `r01/`, y **cualquier commit git**.
- Si `persistencia/` no existe aún, créala tú según el contrato (WP3 la importará); no esperes a nadie.

## CONTRATO COMPARTIDO (no modificar)

- `GET /health` → 200 `{"reto": "R01", "status": "ok"}`; app importable como `api.main:app` ejecutando uvicorn desde `prototipo_r01/`.
- `persistencia/__init__.py` exporta `guardar_estudiantes(filas: list[dict]) -> int` y `obtener_estudiantes() -> list[dict]`.
- Esquema único `estudiantes(id, nombre, casa, origen, sincronizado_en)`; si `MARIADB_URL` (env, p.ej. `mariadb+pymysql://r01:r01@127.0.0.1:3307/hogwarts`) no conecta en ≤3 s → fallback automático a SQLite `prototipo_r01/data/r01.sqlite`. **Nunca** tumbar el arranque si MariaDB está caído (WP1 corre en paralelo).

## CHECKLIST (3-4 pasos)

1. **API**: `prototipo_r01/api/__init__.py` + `api/main.py` con FastAPI y el endpoint `/health` exacto del contrato.
2. **Persistencia doble**: `prototipo_r01/persistencia/__init__.py` (+ `esquema.py` si quieres) con `guardar_estudiantes`/`obtener_estudiantes`, DDL único, motor MariaDB con timeout corto y fallback SQLite automático (bandera visible en `motor_usado` si prefieres, pero la firma NO cambia).
3. **Smoke en subterminal en background**: levanta `uvicorn api.main:app --port 8000` con `Start-Process`, comprueba `GET /health` con urllib, guarda 2 filas de ejemplo y léelas de vuelta; cierra el servidor. Todo en ≤3 min (si algo se cuelga, déjalo en background y documenta).
4. **Informe**: escribe `.arquitecto/progress/wp2_informe.md` con estado **OK/FAIL por paso** (y el motor realmente usado en el smoke).

## DEFINICIÓN DE HECHO (DoD)

- `GET /health` responde `R01` (verificado) y persistencia guarda/lee con SQLite (y MariaDB si WP1 ya levantó).
- `wp2_informe.md` existe. Mensaje final: resumen de 5 líneas con OK/FAIL por paso.
