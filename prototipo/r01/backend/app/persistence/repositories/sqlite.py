"""Repositorio de la central sobre SQLite."""

from __future__ import annotations

from sqlalchemy import Engine

from app.persistence.repositories.base import SqlCentralRepository


class SqliteRepository(SqlCentralRepository):
    """Backend SQLite de la central doble."""

    def __init__(self, engine: Engine) -> None:
        super().__init__(engine, name="sqlite")
