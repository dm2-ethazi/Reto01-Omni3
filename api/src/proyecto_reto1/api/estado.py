"""Endpoint de estado de la API (Reto R01).

`GET /status` informa de la salud del sistema: identificador del reto,
estado de MariaDB y del respaldo SQLite, operaciones pendientes de
sincronización y fecha/hora del servidor.
"""

import socket
from datetime import datetime, timezone

from fastapi import APIRouter, Depends

from ..db.base import RepositorioAlumnos
from ..db.factory import obtener_repositorios, obtener_sincronizador
from ..db.mariadb import RepositorioMariaDB
from ..db.sincronizacion import Sincronizador

router = APIRouter()


def get_repositorios() -> list[RepositorioAlumnos]:
    """Dependencia que inyecta la lista de repositorios activos."""
    return obtener_repositorios()


def get_sincronizador() -> Sincronizador:
    """Dependencia que inyecta el sincronizador (MariaDB + SQLite)."""
    return obtener_sincronizador()


@router.get("/status", summary="Estado del sistema (Reto R01)")
def estado_sistema(
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Devuelve el estado de la API en JSON, con la referencia al reto «R01».

    Campos:
        reto: Siempre "R01".
        estado: "ok" si MariaDB y SQLite responden; "degradado" si solo
            responde el respaldo; "down" si nada responde.
        mariadb: "up" / "down".
        sqlite: "up" / "down".
        operaciones_pendientes: Operaciones en `pending_sync` por aplicar.
        operaciones_fallidas: Operaciones que agotaron los reintentos.
        fecha_hora: Marca de tiempo ISO-8601 con zona UTC.

    Args:
        fuentes: Repositorios activos (inyectados por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Returns:
        Dict con el estado del sistema.
    """
    repositorio_mariadb = next(
        (r for r in fuentes if isinstance(r, RepositorioMariaDB)), None
    )
    mariadb_up = repositorio_mariadb.ping() if repositorio_mariadb is not None else False
    sqlite_up = sincronizador.respaldo.ping()
    pendientes = sincronizador.respaldo.contar_pendientes()
    fallidas = sincronizador.respaldo.contar_fallidas()

    if mariadb_up and sqlite_up:
        estado_general = "ok"
    elif sqlite_up:
        estado_general = "degradado"
    else:
        estado_general = "down"

    return {
        "reto": "R01",
        "estado": estado_general,
        "mariadb": "up" if mariadb_up else "down",
        "sqlite": "up" if sqlite_up else "down",
        "operaciones_pendientes": pendientes,
        "operaciones_fallidas": fallidas,
        "fecha_hora": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "servidor": socket.gethostname(),
    }
