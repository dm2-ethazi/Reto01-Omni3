#!/bin/bash
# R01 - Semilla Oracle: otorga privilegios al usuario de aplicacion y crea
# la tabla ESTUDIANTE dentro de FREEPDB1. Se ejecuta en la primera
# inicializacion del contenedor, como indica gvenzl/oracle-free.
set -euo pipefail

echo "R01-oracle: otorgando privilegios a ${APP_USER}"
sqlplus -s "system/${ORACLE_PASSWORD}@//localhost:1521/FREEPDB1" <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO ${APP_USER};
GRANT UNLIMITED TABLESPACE TO ${APP_USER};
EXIT
SQL

echo "R01-oracle: cargando semilla en el esquema ${APP_USER}"
sqlplus -s "${APP_USER}/${APP_USER_PASSWORD}@//localhost:1521/FREEPDB1" <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
@/opt/oracle/r01_seed.sql
EXIT
SQL

echo "R01-oracle: semilla cargada"
