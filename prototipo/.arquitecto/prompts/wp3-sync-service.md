# WP3 · `sync-service` — Prompt de ejecución (Ola 1 · Reto R01)

**ROL**: Worker de `@arquitecto_dios`. Prototipo funcional **mínimo**, nada de pulido estético.
**LÍMITE CRÍTICO**: 3 minutos por subterminal. Tareas pequeñas.

## ORDEN EXPLÍCITA DE PARALELIZACIÓN (OBLIGATORIA)

- Usa **subterminales EN PARALELO sin bloquear los hilos principales**:
  - Los smokes/tests: lánzalos en **segundo plano** (`Start-Process ... -PassThru` con salida a log, u `opencode run --agent general "<subpaso>"` en proceso aparte) y continúa con el siguiente paso; recoge resultados leyendo el log.
  - **Nunca esperes de forma bloqueante** más de ~30 s en un solo comando.
  - Ejecuta en paralelo todo lo que no compita por el mismo archivo.

## ALCANCE (no tocar nada fuera)

- Tuyos: `prototipo_r01/acceso_datos/**`, `prototipo_r01/servicios/**`.
- Prohibido: `docker-compose.yml`, `api/`, `persistencia/` (solo la importas), `clientes_mock/`, `r01/`, y **cualquier commit git**.
- WP2 corre EN PARALELO: `persistencia/` puede no existir al empezar. Cógetela con `try/except ImportError`; si falta, usa un fallback SQLite interno (`servicios/_persistencia_fallback.py`, marcado `# fallback temporal WP2`) con la misma firma. **No esperes a WP2**.

## CONTRATO COMPARTIDO (no modificar)

- `acceso_datos/fuentes.py`: `leer_origen(nombre: str) -> list[dict]` con los 4 orígenes `derby`, `hsqldb`, `h2`, `oracle` (simulados/mocks deterministas, ~3 estudiantes de Hogwarts por origen; claves: `nombre`, `casa`, `origen`).
- `servicios/cocina.py`: `sincronizar() -> dict` → recorre los 4 orígenes, llama a `guardar_estudiantes(filas)` de `persistencia` y devuelve `{"por_origen": {...}, "total": n}`.
- Ejecutable: `python -m servicios.cocina` desde `prototipo_r01/` (usa solo stdlib salvo la dependencia de persistencia).

## CHECKLIST (3-4 pasos)

1. **Adaptador de acceso**: crea `prototipo_r01/acceso_datos/__init__.py` y `acceso_datos/fuentes.py` con los 4 orígenes mock (estudiantes de Hogwarts variados, sin duplicados entre orígenes).
2. **Servicio cocina**: crea `prototipo_r01/servicios/__init__.py` y `servicios/cocina.py` con `sincronizar()` + bloque `__main__` que imprime JSON del resultado; import de `persistencia` protegido con fallback temporal.
3. **Smoke end-to-end en subterminal en background**: ejecuta `python -m servicios.cocina` redirigiendo a log, verifica que quedaron filas (lee `data/r01.sqlite` con sqlite3 estándar, o MariaDB si está disponible); cierra limpio en ≤3 min.
4. **Informe**: escribe `.arquitecto/progress/wp3_informe.md` con estado **OK/FAIL por paso** (indicando si usaste `persistencia` real o el fallback temporal).

## DEFINICIÓN DE HECHO (DoD)

- `python -m servicios.cocina` sincroniza los 4 orígenes y las filas son consultables en la persistencia (SQLite mínimo).
- `wp3_informe.md` existe. Mensaje final: resumen de 5 líneas con OK/FAIL por paso.
