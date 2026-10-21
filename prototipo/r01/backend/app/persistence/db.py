"""Fábrica de engines y creación del esquema de la central."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

from sqlalchemy import Engine, create_engine, text

from app.core.config import Settings
from app.persistence.models import Base

logger = logging.getLogger(__name__)


@dataclass
class CentralEngines:
    """Engines de la central doble + estado de disponibilidad."""

    sqlite: Engine
    mariadb: Engine | None = None
    mariadb_disponible: bool = False
    errores: list[str] = field(default_factory=list)


def build_engines(settings: Settings) -> CentralEngines:
    """Crea los engines (lazy): no abre conexiones todavía."""
    settings.sqlite_central_path.parent.mkdir(parents=True, exist_ok=True)
    sqlite_engine = create_engine(
        settings.sqlite_url,
        connect_args={"check_same_thread": False},
        future=True,
    )

    engines = CentralEngines(sqlite=sqlite_engine)
    if settings.mariadb_enabled:
        try:
            engines.mariadb = create_engine(settings.mariadb_url, future=True)
        except Exception as exc:  # pragma: no cover - configuración inválida
            engines.errores.append(f"mariadb: {exc}")
            logger.error("No se pudo crear el engine de MariaDB: %s", exc)
    return engines


def init_schema(engines: CentralEngines) -> None:
    """Crea las tablas si no existen y comprueba la disponibilidad."""
    Base.metadata.create_all(engines.sqlite)
    logger.info("Esquema SQLite verificado en %s", engines.sqlite.url.database)

    if engines.mariadb is None:
        engines.errores.append("mariadb: deshabilitado por configuración")
        return

    try:
        with engines.mariadb.connect() as connection:
            connection.execute(text("SELECT 1"))
        Base.metadata.create_all(engines.mariadb)
        engines.mariadb_disponible = True
        logger.info("Esquema MariaDB verificado")
    except Exception as exc:
        engines.mariadb_disponible = False
        motivo = f"mariadb: {exc}"
        engines.errores.append(motivo)
        logger.warning(
            "MariaDB no disponible (%s). La central sigue operativa con SQLite.",
            exc,
        )
