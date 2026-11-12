"""Fábrica de repositorios.

Crea la lista de repositorios activos según la variable DB_ENGINES de la
configuración. Los repositorios JDBC inician la JVM al conectarse; un fallo
en Java no impide arrancar ni consultar SQLite central.
"""

from ..config import settings
from .base import RepositorioAlumnos
from .central_mariadb import CentralMariaDB
from .derby import RepositorioDerby
from .h2 import RepositorioH2
from .hsqldb import RepositorioHSQLDB
from .oracle import RepositorioOracle

# Caché de repositorios (singleton): se crea una sola vez y se reutiliza.
_repositorios: list[RepositorioAlumnos] | None = None


def _crear(nombre: str) -> RepositorioAlumnos:
    """Crea el repositorio correspondiente al nombre del motor.

    Args:
        nombre: Nombre del motor ('oracle', 'mariadb', 'h2', 'hsqldb', 'derby').

    Returns:
        Instancia del repositorio con las credenciales de settings.

    Raises:
        ValueError: Si el nombre del motor no es reconocido.
    """
    if nombre == "oracle":
        return RepositorioOracle(
            host=settings.oracle_host,
            puerto=settings.oracle_port,
            servicio=settings.oracle_service,
            usuario=settings.oracle_user,
            password=settings.oracle_password,
        )
    if nombre == "mariadb":
        return CentralMariaDB(
            host=settings.mariadb_host,
            puerto=settings.mariadb_port,
            base=settings.mariadb_database,
            usuario=settings.mariadb_user,
            password=settings.mariadb_password,
        )
    if nombre == "h2":
        return RepositorioH2(settings.h2_url, settings.h2_user, settings.h2_password)
    if nombre == "hsqldb":
        return RepositorioHSQLDB(settings.hsqldb_url, settings.hsqldb_user, settings.hsqldb_password)
    if nombre == "derby":
        return RepositorioDerby(settings.derby_url, settings.derby_user, settings.derby_password)
    raise ValueError(f"Motor desconocido: {nombre}")


def obtener_repositorios() -> list[RepositorioAlumnos]:
    """Devuelve la lista de repositorios activos (singleton).

    En la primera llamada, parsea DB_ENGINES y crea los repositorios.
    Las llamadas posteriores devuelven la misma
    lista cacheada.

    Returns:
        Lista de repositorios activos en el orden indicado en DB_ENGINES.
    """
    global _repositorios
    if _repositorios is None:
        nombres = [n.strip().lower() for n in settings.db_engines.split(",") if n.strip()]
        _repositorios = [_crear(n) for n in nombres]
    return _repositorios
