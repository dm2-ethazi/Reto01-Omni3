"""Cola de reintentos para upserts parciales de la central.

Si un backend falla (p. ej. MariaDB caído), la fila se registra aquí y el
siguiente ``sync`` la vuelve a intentar. No se simula una transacción
distribuida: se deja traza explícita del fallo y del reintento.
"""

from __future__ import annotations

import logging
from collections.abc import Mapping, Sequence
from typing import Any

from sqlalchemy import Engine, delete, func, select
from sqlalchemy.orm import Session

from app.persistence.models import PendienteReintento

logger = logging.getLogger(__name__)


class SqliteRetryQueue:
    """Cola persistente almacenada en el SQLite de la central."""

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def mark(
        self,
        backend: str,
        rows: Sequence[Mapping[str, Any]],
        motivo: str,
    ) -> int:
        """Registra filas fallidas para ese backend. Devuelve cuántas se marcaron."""
        marcadas = 0
        with Session(self._engine) as session, session.begin():
            for row in rows:
                existente = session.execute(
                    select(PendienteReintento).where(
                        PendienteReintento.backend == backend,
                        PendienteReintento.origen == row["origen"],
                        PendienteReintento.id_origen == row["id_origen"],
                    )
                ).scalar_one_or_none()
                if existente is None:
                    session.add(
                        PendienteReintento(
                            backend=backend,
                            origen=row["origen"],
                            id_origen=row["id_origen"],
                            dni=row["dni"],
                            nombre=row["nombre"],
                            casa=row["casa"],
                            patronus=row["patronus"],
                            motivo=motivo[:400],
                            intentos=1,
                        )
                    )
                else:
                    existente.motivo = motivo[:400]
                    existente.intentos = (existente.intentos or 0) + 1
                marcadas += 1
        logger.warning("Cola de reintentos: %d filas marcadas para %s (%s)", marcadas, backend, motivo)
        return marcadas

    def pending(self, backend: str) -> list[dict[str, Any]]:
        """Filas pendientes para un backend concreto."""
        with Session(self._engine) as session:
            rows = session.execute(
                select(PendienteReintento)
                .where(PendienteReintento.backend == backend)
                .order_by(PendienteReintento.id)
            ).scalars().all()
        return [
            {
                "origen": row.origen,
                "id_origen": row.id_origen,
                "dni": row.dni,
                "nombre": row.nombre,
                "casa": row.casa,
                "patronus": row.patronus,
                "intentos": row.intentos,
            }
            for row in rows
        ]

    def resolve(self, backend: str, origen: str, id_origen: int) -> None:
        """Elimina la fila pendiente tras un reintento exitoso."""
        with Session(self._engine) as session, session.begin():
            session.execute(
                delete(PendienteReintento).where(
                    PendienteReintento.backend == backend,
                    PendienteReintento.origen == origen,
                    PendienteReintento.id_origen == id_origen,
                )
            )

    def count(self) -> int:
        """Total de filas pendientes de todos los backends."""
        with Session(self._engine) as session:
            return int(session.execute(select(func.count(PendienteReintento.id))).scalar_one())
