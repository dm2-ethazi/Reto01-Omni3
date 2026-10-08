from pathlib import Path

from ..config import settings
from .base import RepositorioAlumnos
from .derby import RepositorioDerby
from .h2 import RepositorioH2
from .hsqldb import RepositorioHSQLDB
from .mariadb import RepositorioMariaDB
from .oracle import RepositorioOracle

_JARS = {
    "h2": ("h2.jar",),
    "hsqldb": ("hsqldb.jar",),
    "derby": ("derbyclient.jar", "derbyshared.jar"),
}

_repositorios: list[RepositorioAlumnos] | None = None


def _crear(nombre: str) -> RepositorioAlumnos:
    if nombre == "oracle":
        return RepositorioOracle(
            host=settings.oracle_host,
            puerto=settings.oracle_port,
            servicio=settings.oracle_service,
            usuario=settings.oracle_user,
            password=settings.oracle_password,
        )
    if nombre == "mariadb":
        return RepositorioMariaDB(
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
    global _repositorios
    if _repositorios is None:
        nombres = [n.strip().lower() for n in settings.db_engines.split(",") if n.strip()]
        jars = Path(settings.jars_dir)
        rutas = [str(jars / nombre) for n in nombres for nombre in _JARS.get(n, ())]
        if rutas:
            from .jdbc import iniciar_jvm

            iniciar_jvm(rutas)
        _repositorios = [_crear(n) for n in nombres]
    return _repositorios
