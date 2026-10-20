# WP1 · `infra-docker` — Informe (Ola 1 · Reto R01)

Worker: `general` · Archivos tocados: `prototipo_r01/docker-compose.yml`,
`prototipo_r01/infra/init/01_esquema.sql`, `prototipo_r01/infra/LEEME.md`.
Sin commits git. Nada fuera de `prototipo_r01/docker-compose.yml` + `prototipo_r01/infra/**`.

## Estado por paso

| # | Paso | Estado | Evidencia |
|---|---|---|---|
| 1 | Compose único con 5 servicios (`derby`, `hsqldb`, `h2`, `oracle`, `mariadb`), puertos 3307/1521/9092/9001/1527 y volúmenes nombrados | **OK** | `prototipo_r01/docker-compose.yml`; `docker compose config --services` lista los 5 |
| 2 | Init MariaDB `infra/init/01_esquema.sql` (BD `hogwarts`, tabla `estudiantes`, usuario `r01`/`r01`, montado en `/docker-entrypoint-initdb.d`) | **OK** | Ejecutado en el primer arranque: `SHOW TABLES` → `estudiantes`; consulta como `r01` devuelve filas |
| 3a | `docker compose pull` en segundo plano | **OK** | Todas las imágenes descargadas (log BG, PID 17996, exit 0) |
| 3b | `docker compose -f prototipo_r01/docker-compose.yml config` → exit 0 | **OK** | `CONFIG_EXIT=0` |
| 3c | `docker compose up -d mariadb` | **OK** | `prototipo_r01-mariadb-1` → `Up (healthy)`, puerto `3307->3306`, MariaDB 11.8.9 |
| 3d | `docker compose up -d` (5 servicios) | **FAIL parcial** | 4 servicios quedan en `Created`: puertos 1521/1527/9001/9092 ya ocupados por contenedores previos `r01-oracle`, `r01-derby`, `r01-hsqldb`, `r01-h2` (del repo `r01/`, fuera de mi alcance). Error: `Bind for 0.0.0.0:1521 failed: port is already allocated` |
| 3e | Smoke de las 4 BD de origen (puertos alternativos desechables) | **OK** | Derby: `Network Server started ... on port 1527`; HSQLDB: `server 2.4.0 is online on port 9001`; H2: `TCP server running ...:9092`; Oracle: `FREEPDB1 opened read write` + `healthcheck.sh` exit 0. Contenedores de prueba eliminados después |
| 4 | `infra/LEEME.md` + este informe | **OK** | `prototipo_r01/infra/LEEME.md` (≤30 líneas), `.arquitecto/progress/wp1_informe.md` |

## Imágenes disponibles (todas con `pull` OK)

- `mariadb:11` → 11.8.9 (elegida del plan; local ya había `mariadb:11.4`/`11.8`)
- `gvenzl/oracle-free:23-slim-faststart` → OK (variante *faststart* del plan)
- `oscarfonts/h2` → OK (override de comando: el por defecto usa TCP 1521, se fuerza `-tcpPort 9092`)
- Derby: candidatos probados → `adito/derby` **OK** (elegida), `kazssym/derby` OK (alternativa); no hay imagen oficial en Docker Hub
- HSQLDB: candidatos probados → `blacklabelops/hsqldb` **OK** (elegida), `datagrip/hsqldb` OK (alternativa)
- Fallback documentado: `openjdk:17-slim` **NO existe** en Docker Hub (retirado) → alternativa Java `eclipse-temurin:17-jre-jammy`

## Cómo levantar los 4 servicios bloqueados

```powershell
docker stop r01-oracle r01-derby r01-hsqldb r01-h2   # dueño: repo r01/ (no tocado por WP1)
docker compose -f prototipo_r01/docker-compose.yml up -d
```

Mientras tanto el prototipo no se ve afectado: WP2/WP3 usan MariaDB (`3307`) o caen a
SQLite `prototipo_r01/data/r01.sqlite` (contrato compartido).

## Resumen final (OK/FAIL por paso)

1. Compose 5 servicios: **OK**
2. Init SQL MariaDB: **OK**
3. `config` exit 0 + `up -d mariadb`: **OK** · `pull` BG: **OK** · `up -d` total: **FAIL parcial** (puertos de `r01-*`)
4. `LEEME.md` + `wp1_informe.md`: **OK**
