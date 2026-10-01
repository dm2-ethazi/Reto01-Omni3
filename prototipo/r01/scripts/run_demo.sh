#!/usr/bin/env bash
# R01 - Arranque completo en Linux/macOS (Docker + backend FastAPI)
#
# Uso:
#   ./scripts/run_demo.sh              # infraestructura + backend
#   ./scripts/run_demo.sh --solo-infra # sólo los contenedores
#
# Requisitos: Docker (con compose v2), Python 3.11+ y el entorno virtual
# de backend/ ya creado (python -m venv .venv && pip install -r requirements.txt).

set -euo pipefail

SOLO_INFRA=0
SIN_RECONSTRUIR=0
PUERTO=8000
for arg in "$@"; do
  case "$arg" in
    --solo-infra) SOLO_INFRA=1 ;;
    --sin-reconstruir) SIN_RECONSTRUIR=1 ;;
    --puerto=*) PUERTO="${arg#*=}" ;;
    *) echo "Argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$RAIZ/infra"
BACKEND="$RAIZ/backend"

echo "== R01: levantando la infraestructura =="
if [ "$SIN_RECONSTRUIR" -eq 1 ]; then
  docker compose -f "$INFRA/docker-compose.yml" up -d
else
  docker compose -f "$INFRA/docker-compose.yml" up -d --build
fi

echo "== Estado de los contenedores =="
docker compose -f "$INFRA/docker-compose.yml" ps --format 'table {{.Name}}\t{{.Status}}'

[ "$SOLO_INFRA" -eq 1 ] && { echo "== Sólo infraestructura solicitada =="; exit 0; }

if [ ! -f "$BACKEND/.env" ]; then
  echo "== Creando backend/.env a partir del ejemplo =="
  cp "$BACKEND/.env.example" "$BACKEND/.env"
fi

PYTHON="$BACKEND/.venv/bin/python"
[ -x "$PYTHON" ] || PYTHON="$(command -v python3 || command -v python)"

LOG_SALIDA="${TMPDIR:-/tmp}/r01-backend.log"
LOG_ERROR="${TMPDIR:-/tmp}/r01-backend.err.log"

echo "== R01: arrancando el backend en el puerto $PUERTO =="
(cd "$BACKEND" && "$PYTHON" -m uvicorn app.main:app --host 127.0.0.1 --port "$PUERTO" \
  >"$LOG_SALIDA" 2>"$LOG_ERROR" &)

for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$PUERTO/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:$PUERTO/health" >/dev/null 2>&1; then
  echo "== El backend no ha respondido; últimas líneas del log ==" >&2
  tail -n 30 "$LOG_ERROR" >&2 || true
  exit 1
fi

echo "== Salud =="
curl -fsS "http://127.0.0.1:$PUERTO/health"
echo
echo "== API:  http://127.0.0.1:$PUERTO/docs =="
echo "== Log:  $LOG_SALIDA =="
