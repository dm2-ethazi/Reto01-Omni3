#!/usr/bin/env bash
# R01 - Descarga los drivers JDBC desde Maven Central a backend/drivers/.
# Uso: bash infra/scripts/fetch_drivers.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../../backend" && pwd)"
DEST="${BACKEND_DIR}/drivers"
BASE="https://repo1.maven.org/maven2"

DERBY_VERSION="${DERBY_VERSION:-10.16.1.1}"
HSQLDB_VERSION="${HSQLDB_VERSION:-2.7.3}"
H2_VERSION="${H2_VERSION:-2.2.224}"
OJDBC_VERSION="${OJDBC_VERSION:-23.26.3.0.0}"

mkdir -p "${DEST}"

descargar() {
  local ruta="$1" fichero="$2"
  if [[ -f "${DEST}/${fichero}" ]]; then
    echo "ya existe: ${fichero}"
    return 0
  fi
  echo "descargando ${fichero} ..."
  curl -fsSL --retry 3 -o "${DEST}/${fichero}" "${BASE}/${ruta}/${fichero}"
  echo "  OK $(du -h "${DEST}/${fichero}" | cut -f1)"
}

descargar "org/apache/derby/derbyclient/${DERBY_VERSION}" "derbyclient-${DERBY_VERSION}.jar"
descargar "org/apache/derby/derbyshared/${DERBY_VERSION}" "derbyshared-${DERBY_VERSION}.jar"
descargar "org/hsqldb/hsqldb/${HSQLDB_VERSION}" "hsqldb-${HSQLDB_VERSION}.jar"
descargar "com/h2database/h2/${H2_VERSION}" "h2-${H2_VERSION}.jar"
descargar "com/oracle/database/jdbc/ojdbc11/${OJDBC_VERSION}" "ojdbc11-${OJDBC_VERSION}.jar"

echo "Drivers disponibles en ${DEST}:"
ls -1 "${DEST}"
