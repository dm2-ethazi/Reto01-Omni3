"""Repositorio abstracto de la central y su implementación SQL compartida.

MariaDB y SQLite comparten esta implementación; sólo cambia el engine y el
nombre del backend. Así la lógica de upsert/búsqueda/hash está escrita una
única vez (reutilización por composición, sin duplicación).
"""

from __future__ import annotations

import hashlib
import logging
from abc import ABC, abstractmethod
from collections.abc import Iterable, Mapping, Sequence
from dataclasses import dataclass
from typing import Any

from sqlalchemy import Engine, func, select
from sqlalchemy.orm import Session

from app.persistence.models import EstudianteCentral

logger = logging.getLogger(__name__)

CLAVES_FILA = ("origen", "id_origen", "dni", "nombre", "casa", "patronus")


@dataclass
class UpsertStats:
    """Resultado de una sincronización contra un backend de la central."""

    insertados: int = 0
    actualizados: int = 0
    errores: int = 0


def _fila_valida(row: Mapping[str, Any]) -> bool:
    return all(key in row for key in CLAVES_FILA)


class CentralRepository(ABC):
    """Interfaz común de un backend de la central."""

    name: str

    @abstractmethod
    def upsert(self, rows: Sequence[Mapping[str, Any]]) -> UpsertStats:
        """Inserta o actualiza por la clave ``(origen, id_origen)``."""

    @abstractmethod
    def count_by_origen(self) -> dict[str, int]:
        """Total de filas agrupadas por origen."""

    @abstractmethod
    def hash_by_origen(self) -> dict[str, str]:
        """Huella de contenido por origen, para comparar backends."""

    @abstractmethod
    def search(
        self,
        *,
        nombre: str | None = None,
        dni: str | None = None,
        offset: int = 0,
        limit: int = 50,
    ) -> list[dict[str, Any]]:
        """Consulta paginada de la central."""

    @abstractmethod
    def count(self, *, nombre: str | None = None, dni: str | None = None) -> int:
        """Total de filas que cumplen los filtros (para paginar)."""

    @abstractmethod
    def get_by_dni(self, dni: str) -> dict[str, Any] | None:
        """Devuelve la primera coincidencia por DNI."""

    @abstractmethod
    def ping(self) -> bool:
        """Indica si el backend responde."""


class SqlCentralRepository(CentralRepository):
    """Implementación con SQLAlchemy 2.x reutilizable por ambos backends."""

    def __init__(self, engine: Engine, name: str) -> None:
        self._engine = engine
        self.name = name

    def upsert(self, rows: Sequence[Mapping[str, Any]]) -> UpsertStats:
        stats = UpsertStats()
        pendientes = [row for row in rows if _fila_valida(row)]
        if len(pendientes) != len(rows):
            stats.errores += len(rows) - len(pendientes)
            logger.warning(
                "%s: %d filas descartadas por no tener todos los campos",
                self.name,
                len(rows) - len(pendientes),
            )

        if not pendientes:
            return stats

        # Se usa Session (y no Connection) para que las consultas con la
        # entidad devuelvan objetos ORM y no filas Core de una sola columna.
        with Session(self._engine) as session, session.begin():
            for row in pendientes:
                existente = session.execute(
                    select(EstudianteCentral).where(
                        EstudianteCentral.origen == row["origen"],
                        EstudianteCentral.id_origen == row["id_origen"],
                    )
                ).scalar_one_or_none()
                if existente is None:
                    session.add(
                        EstudianteCentral(
                            origen=row["origen"],
                            id_origen=row["id_origen"],
                            dni=row["dni"],
                            nombre=row["nombre"],
                            casa=row["casa"],
                            patronus=row["patronus"],
                        )
                    )
                    stats.insertados += 1
                else:
                    existente.dni = row["dni"]
                    existente.nombre = row["nombre"]
                    existente.casa = row["casa"]
                    existente.patronus = row["patronus"]
                    existente.actualizado = func.now()
                    stats.actualizados += 1
        return stats

    def count_by_origen(self) -> dict[str, int]:
        with self._engine.connect() as connection:
            rows = connection.execute(
                select(EstudianteCentral.origen, func.count())
                .group_by(EstudianteCentral.origen)
                .order_by(EstudianteCentral.origen)
            ).all()
        return {origen: int(total) for origen, total in rows}

    def hash_by_origen(self) -> dict[str, str]:
        """Huella estable: sha256 de las filas ordenadas por origen y DNI."""
        with self._engine.connect() as connection:
            rows = connection.execute(
                select(
                    EstudianteCentral.origen,
                    EstudianteCentral.dni,
                    EstudianteCentral.nombre,
                    EstudianteCentral.casa,
                    EstudianteCentral.patronus,
                ).order_by(EstudianteCentral.origen, EstudianteCentral.dni, EstudianteCentral.id)
            ).all()

        acumulado: dict[str, list[str]] = {}
        for origen, dni, nombre, casa, patronus in rows:
            acumulado.setdefault(origen, []).append(f"{dni}|{nombre}|{casa}|{patronus}")

        return {
            origen: hashlib.sha256("\n".join(valores).encode()).hexdigest()
            for origen, valores in sorted(acumulado.items())
        }

    def search(
        self,
        *,
        nombre: str | None = None,
        dni: str | None = None,
        offset: int = 0,
        limit: int = 50,
    ) -> list[dict[str, Any]]:
        statement = select(EstudianteCentral)
        if dni:
            statement = statement.where(EstudianteCentral.dni == dni.upper())
        if nombre:
            statement = statement.where(EstudianteCentral.nombre.ilike(f"%{nombre}%"))
        statement = (
            statement.order_by(EstudianteCentral.origen, EstudianteCentral.id)
            .offset(offset)
            .limit(limit)
        )
        with Session(self._engine) as session:
            rows = session.execute(statement).scalars().all()
        return [row.as_dict() for row in rows]

    def count(self, *, nombre: str | None = None, dni: str | None = None) -> int:
        statement = select(func.count()).select_from(EstudianteCentral)
        if dni:
            statement = statement.where(EstudianteCentral.dni == dni.upper())
        if nombre:
            statement = statement.where(EstudianteCentral.nombre.ilike(f"%{nombre}%"))
        with self._engine.connect() as connection:
            return int(connection.execute(statement).scalar_one())

    def get_by_dni(self, dni: str) -> dict[str, Any] | None:
        with Session(self._engine) as session:
            row = session.execute(
                select(EstudianteCentral)
                .where(EstudianteCentral.dni == dni.upper())
                .order_by(EstudianteCentral.id)
                .limit(1)
            ).scalar_one_or_none()
        return row.as_dict() if row else None

    def ping(self) -> bool:
        try:
            with self._engine.connect() as connection:
                connection.execute(select(func.count()).select_from(EstudianteCentral))
            return True
        except Exception as exc:
            logger.warning("%s no responde: %s", self.name, exc)
            return False


def totalizar(stats: Iterable[UpsertStats]) -> UpsertStats:
    """Suma varios resultados de upsert (para el resumen del sync)."""
    total = UpsertStats()
    for item in stats:
        total.insertados += item.insertados
        total.actualizados += item.actualizados
        total.errores += item.errores
    return total
