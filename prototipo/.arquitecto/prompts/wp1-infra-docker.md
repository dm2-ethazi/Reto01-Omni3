# WP1 · `infra-docker` — Prompt de ejecución (Ola 1 · Reto R01)

**ROL**: Worker de `@arquitecto_dios`. Prototipo funcional **mínimo**, nada de pulido estético.
**LÍMITE CRÍTICO**: 3 minutos por subterminal. Tareas pequeñas.

## ORDEN EXPLÍCITA DE PARALELIZACIÓN (OBLIGATORIA)

- Usa **subterminales EN PARALELO sin bloquear los hilos principales**:
  - Comandos largos (`docker pull`, `docker compose up`, instalaciones): lánzalos en **segundo plano** con `Start-Process ... -PassThru` (o `opencode run --agent general "<subpaso>"` en proceso aparte) y **sigue trabajando inmediatamente**; recoge resultados leyendo los logs al final.
  - **Nunca esperes de forma bloqueante** más de ~30 s en un solo comando; si un paso tarda, pásalo a segundo plano y continúa con el siguiente paso del checklist.
  - Ejecuta en paralelo todo lo que no compita por el mismo archivo.

## ALCANCE (no tocar nada fuera)

- Tuyos: `prototipo_r01/docker-compose.yml`, `prototipo_r01/infra/**`.
- Prohibido: `api/`, `servicios/`, `acceso_datos/`, `persistencia/`, `clientes_mock/`, `r01/`, y **cualquier commit git**.

## CHECKLIST (3-4 pasos)

1. **Compose único**: crea `prototipo_r01/docker-compose.yml` con 5 servicios: `derby`, `hsqldb`, `h2`, `oracle`, `mariadb` (mejores imágenes conocidas: `mariadb:11`, `gvenzl/oracle-free:23-slim-faststart`, `oscarfonts/h2` para H2; para Derby/HSQLDB prueba candidatos de Docker Hub y bloquea la que haga `pull`; si ninguna imagen es viable, usa `openjdk:17-slim` con comando de arranque y documéntalo). Puertos: mariadb `3307:3306`, oracle `1521`, h2 `9092`, hsqldb `9001`, derby `1527`. Volúmenes nombrados por servicio.
2. **Init MariaDB**: `prototipo_r01/infra/init/01_esquema.sql` → BD `hogwarts`, tabla `estudiantes(id, nombre, casa, origen, sincronizado_en)`, usuario `r01`/`r01`; montado en `/docker-entrypoint-initdb.d`. Basado en el contrato compartido del plan (no lo cambies).
3. **Validación**: en **subterminal en background** arranca `docker compose pull` (o `up -d` si el pull ya terminó); en el hilo principal ejecuta `docker compose -f prototipo_r01/docker-compose.yml config` (debe dar exit 0 — esto es OBLIGATORIO) y `docker compose up -d mariadb` como mínimo. Timeout: si `up` supera el tiempo, déjalo corriendo y documenta el estado.
4. **Informe**: escribe `prototipo_r01/infra/LEEME.md` (≤30 líneas: comandos de arranque, puertos, fallback manual si Docker falla) y tu informe `.arquitecto/progress/wp1_informe.md` con estado **OK/FAIL por paso** (incluye qué imágenes quedaron disponibles).

## DEFINICIÓN DE HECHO (DoD)

- `docker compose config` valida el archivo (exit 0), aunque el daemon o las imágenes fallen (fallback documentado).
- `LEEME.md` + `wp1_informe.md` existen.
- Mensaje final: resumen de 5 líneas con OK/FAIL por paso.
