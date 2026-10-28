"""Configuración de los tests de integración del Reto R01.

Los tests usan la MariaDB real de Docker Compose (servicio `mariadb`) y un
fichero SQLite temporal como respaldo. Configuran el entorno **antes** de
importar la aplicación, porque `config.Settings` se carga al importar el módulo.

Las pruebas de caída de MariaDB pausan/despausan el contenedor `r01-mariadb`
con el CLI de Docker; si el contenedor no existe, esas pruebas se omiten.
"""

import os
import subprocess
from pathlib import Path

import pytest

# --- Entorno ANTES de importar la app (pydantic-settings lee a nivel módulo) ---
os.environ["DB_ENGINES"] = "mariadb"
os.environ["MARIADB_HOST"] = "127.0.0.1"
os.environ["MARIADB_PORT"] = "3306"
os.environ["MARIADB_DATABASE"] = "r01_central"
os.environ["MARIADB_USER"] = "example"
os.environ["MARIADB_PASSWORD"] = "example"
_RUTA_SQLITE = Path(__file__).resolve().parent / "_respaldo_test.db"
if _RUTA_SQLITE.exists():
    _RUTA_SQLITE.unlink()
os.environ["SQLITE_PATH"] = str(_RUTA_SQLITE)
os.environ["SYNC_INTERVALO_SEGUNDOS"] = "1"
os.environ["SYNC_BACKOFF_INICIAL_SEGUNDOS"] = "1"
os.environ["SYNC_BACKOFF_MAX_SEGUNDOS"] = "5"
os.environ["SYNC_MAX_INTENTOS"] = "8"

CONTENEDOR_MARIADB = "r01-mariadb"


def _docker_disponible() -> bool:
    """Comprueba que el CLI de Docker responde y el contenedor existe."""
    try:
        resultado = subprocess.run(
            ["docker", "ps", "--format", "{{.Names}}"],
            capture_output=True,
            text=True,
            timeout=30,
        )
        return CONTENEDOR_MARIADB in resultado.stdout
    except Exception:
        return False


DOCKER_OK = _docker_disponible()


def pausar_mariadb() -> None:
    """Para el contenedor de MariaDB (simula caída del servicio).

    Se usa `stop` (y no `pause`) porque con un contenedor pausado el proxy de
    Docker acepta el TCP y el handshake de MySQL queda colgado.
    """
    subprocess.run(["docker", "stop", CONTENEDOR_MARIADB], check=True, capture_output=True, timeout=120)


def despausar_mariadb() -> None:
    """Arranca de nuevo el contenedor de MariaDB (simula recuperación)."""
    subprocess.run(["docker", "start", CONTENEDOR_MARIADB], check=True, capture_output=True, timeout=120)


@pytest.fixture(scope="session")
def cliente():
    """Cliente de prueba de FastAPI con el lifespan aplicado (arranca el worker)."""
    from fastapi.testclient import TestClient

    from proyecto_reto1.main import app

    with TestClient(app) as client:
        yield client


@pytest.fixture()
def mariadb_pausada():
    """Fixture que pausa MariaDB durante la prueba y la restaura al final.

    Las pruebas pueden despausar el contenedor por su cuenta; la restauración
    final ignora el error si ya estaba despausado.
    """
    if not DOCKER_OK:
        pytest.skip("El contenedor r01-mariadb no está disponible")
    pausar_mariadb()
    try:
        yield
    finally:
        # Asegura que el contenedor queda arrancado aunque la prueba lo parara.
        subprocess.run(
            ["docker", "start", CONTENEDOR_MARIADB], capture_output=True, timeout=120
        )
