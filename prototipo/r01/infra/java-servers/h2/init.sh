#!/bin/bash
# R01 - Arranque de H2 en modo TCP + semilla.
set -e

CP="/opt/h2/lib/*:/opt/h2"
mkdir -p /opt/h2/data

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

echo "R01-h2: iniciando servidor TCP en 0.0.0.0:9092"
java -cp "$CP" org.h2.tools.Server \
  -tcp -tcpAllowOthers -tcpPort 9092 -ifNotExists -baseDir /opt/h2/data &
SERVER_PID=$!

esperar_puerto 127.0.0.1 9092 60

if java -cp "$CP" Seeder "jdbc:h2:tcp://127.0.0.1:9092/r01h2" sa r01pass /opt/h2/seed.sql; then
  echo "R01-h2: semilla cargada"
else
  echo "R01-h2: AVISO - la semilla ya existia, se omite"
fi

wait "$SERVER_PID"
