"""Repositorio de la central sobre MariaDB/MySQL."""

from __future__ import annotations

from sqlalchemy import Engine

from app.persistence.repositories.base import SqlCentralRepository


class MariaDbRepository(SqlCentralRepository):
    """Backend MariaDB/MySQL de la central doble."""

    def __init__(self, engine: Engine) -> None:
        super().__init__(engine, name="mariadb")
