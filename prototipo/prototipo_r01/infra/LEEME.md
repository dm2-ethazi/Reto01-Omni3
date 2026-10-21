# LEEME · infra Docker (prototipo R01)

## Arranque (único compose: `prototipo_r01/docker-compose.yml`, 5 servicios)

```bash
docker compose -f prototipo_r01/docker-compose.yml config   # valida, exit 0
docker compose -f prototipo_r01/docker-compose.yml pull
docker compose -f prototipo_r01/docker-compose.yml up -d    # o: up -d mariadb
docker compose -f prototipo_r01/docker-compose.yml down -v  # para y borra volúmenes
```

| Servicio | Imagen | Puerto | Volúmenenombrado |
|---|---|---|---|
| mariadb | `mariadb:11` | `3307:3306` | `mariadb_data` |
| oracle | `gvenzl/oracle-free:23-slim-faststart` | `1521:1521` | `oracle_data` |
| h2 | `oscarfonts/h2` (override `-tcpPort 9092`) | `9092:9092` | `h2_data` |
| hsqldb | `blacklabelops/hsqldb` | `9001:9001` | `hsqldb_data` |
| derby | `adito/derby` | `1527:1527` | `derby_data` |

Init: `infra/init/01_esquema.sql` → BD `hogwarts`, tabla `estudiantes(id, nombre, casa,
origen, sincronizado_en)`, usuario `r01`/`r01`. Conexión: `mysql -h 127.0.0.1 -P 3307 -ur01 -pr01 hogwarts`.

**Conflictos**: 1521/1527/9001/9092 están ocupados por contenedores previos `r01-*` (repo `r01/`,
fuera de este WP) → `up -d` de esos 4 falla con `port is already allocated`. Solución:
`docker stop r01-oracle r01-derby r01-hsqldb r01-h2` y re-ejecutar `up -d`.

**Fallback sin Docker**: usar cada BD instalada en local, o SQLite
`prototipo_r01/data/r01.sqlite` (contrato WP2). Derby/HSQLDB: imágenes Hub comprobadas;
`openjdk:17-slim` ya no existe → `eclipse-temurin:17-jre-jammy`.
