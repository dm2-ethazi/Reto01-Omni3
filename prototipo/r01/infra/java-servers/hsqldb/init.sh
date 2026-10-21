#!/bin/bash
# R01 - Arranque de HSQLDB en modo servidor + semilla.
set -e

CP="/opt/hsqldb/lib/*:/opt/hsqldb"
mkdir -p /opt/hsqldb/data

esperar_puerto() {
  local host="$1" port="$2" intentos="${3:-60}"
  for _ in $(seq 1 "$intentos"); do
    if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "ERROR: el puerto ${host}:${port} no esta disponible" >&2
  return 1
}

echo "R01-hsqldb: iniciando servidor HSQLDB (archivo /opt/hsqldb/data/r01)"
java -cp "$CP" org.hsqldb.Server \
  --database.0 file:/opt/hsqldb/data/r01 \
  --dbname.0 r01hsqldb &
SERVER_PID=$!

esperar_puerto 127.0.0.1 9001 60

if java -cp "$CP" Seeder "jdbc:hsqldb:hsql://127.0.0.1:9001/r01hsqldb" SA "" /opt/hsqldb/seed.sql; then
  echo "R01-hsqldb: semilla cargada"
else
  echo "R01-hsqldb: AVISO - la semilla ya existia, se omite"
fi

wait "$SERVER_PID"
