#!/bin/bash
# R01 - Arranque de Apache Derby en modo servidor de red + semilla.
set -e

CP="/opt/derby/lib/*:/opt/derby"

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

echo "R01-derby: iniciando NetworkServerControl en 0.0.0.0:1527"
java -cp "$CP" org.apache.derby.drda.NetworkServerControl start -h 0.0.0.0 -p 1527 &
SERVER_PID=$!

esperar_puerto 127.0.0.1 1527 60

if java -cp "$CP" Seeder "jdbc:derby://127.0.0.1:1527/r01derby;create=true" "" "" /opt/derby/seed.sql; then
  echo "R01-derby: semilla cargada"
else
  echo "R01-derby: AVISO - la semilla ya existia, se omite"
fi

wait "$SERVER_PID"
