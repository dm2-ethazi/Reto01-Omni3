# OLA 1 — PLAN DE EJECUCIÓN (Reto R01 · Hogwarts · Omni3)

> Orquestador: `@arquitecto_dios` · Prototipo funcional ultrarrápido y simplificado.
> Fuente única de verdad: parámetros del prompt de orden (PDF original no legible).
> Sin aprobación humana. Límite crítico: **3 min por subterminal**.

## 4. Tabla del Plan (Ola 1)

| WP | Nombre | Objetivo | Checklist (3-4 pasos) | Dependencias | Duración estimada |
|---|---|---|---|---|---|
| **WP1** | `infra-docker` | Un único `docker-compose.yml` que levante las 4 BD de origen (Derby, HSQLDB, H2, Oracle) + MariaDB como almacén principal | 1) Compose con 5 servicios (imágenes + puertos + volúmenes) 2) Init SQL de MariaDB (BD `hogwarts`, tabla `estudiantes`) 3) Validar `docker compose config` + `up -d` (mejor esfuerzo en background) 4) `infra/LEEME.md` con comandos de arranque y fallback manual | Ninguna | 2-3 min |
| **WP2** | `backend-core` | FastAPI con endpoint `/health` que responda `R01` + estructura base de persistencia doble (MariaDB/SQLite) | 1) `api/main.py` con `GET /health` → `{"reto":"R01"}` 2) `persistencia/` dual con DDL único y fallback automático SQLite 3) Smoke en background: levantar uvicorn + `GET /health` + guardar/leer 2 filas en SQLite 4) Informe en `.arquitecto/progress/` | WP1 (MariaDB opcional, tolera su ausencia) | 3 min |
| **WP3** | `sync-service` | Lógica del servicio "cocina": leer de orígenes simulados (4) y guardar en MariaDB/SQLite | 1) `acceso_datos/fuentes.py`: adaptador con 4 orígenes mock deterministas 2) `servicios/cocina.py`: `sincronizar()` + CLI `python -m servicios.cocina` 3) Smoke end-to-end en background (origen → persistencia) 4) Informe en `.arquitecto/progress/` | WP2 (contrato de persistencia, tolera con fallback temporal) + WP1 (MariaDB opcional) | 3 min |

### Contrato técnico compartido (definido por el arquitecto; no modificar)

- Raíz del prototipo: `prototipo_r01/` con capas `api/`, `servicios/`, `acceso_datos/`, `persistencia/`, `clientes_mock/`, `infra/`, `data/`.
- **WP1**: `prototipo_r01/docker-compose.yml` ÚNICO con servicios `derby`, `hsqldb`, `h2`, `oracle`, `mariadb`; init SQL en `prototipo_r01/infra/init/`.
- **WP2**:
  - `GET /health` → 200 `{"reto": "R01", "status": "ok"}`; app importable como `api.main:app` desde `prototipo_r01/`.
  - `persistencia/__init__.py` exporta `guardar_estudiantes(filas: list[dict]) -> int` y `obtener_estudiantes() -> list[dict]`.
  - Esquema único `estudiantes(id, nombre, casa, origen, sincronizado_en)`; si `MARIADB_URL` no conecta en ≤3 s → SQLite `prototipo_r01/data/r01.sqlite`. Nunca tumbar el arranque si MariaDB está caído.
- **WP3**:
  - `acceso_datos/fuentes.py`: `leer_origen(nombre: str) -> list[dict]` con `derby|hsqldb|h2|oracle` (mocks, ~3 estudiantes Hogwarts por origen).
  - `servicios/cocina.py`: `sincronizar() -> dict` = 4 orígenes → `guardar_estudiantes`; si `persistencia` aún no existe (WP2 en paralelo), usa fallback SQLite interno marcado `# fallback temporal WP2`.
- **Todos**: prototipo mínimo, sin pulido estético, SIN commits git.

### Canal de progreso

- Lanzamiento: `scripts/lanzar_workers.ps1` → subterminales en 2.º plano (PID en `workers/`, logs en `progress/*.log`).
- Seguimiento: `scripts/seguimiento.ps1` → actualiza `progress/estado.md`.
- Verificación final: `scripts/verificar.ps1` → estructura + procesos.
- Informes worker → `progress/wpN_informe.md`.
