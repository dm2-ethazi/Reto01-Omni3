"""Modelos SQLAlchemy 2.x de la central (MariaDB/MySQL y SQLite)."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, Integer, String, UniqueConstraint, func
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    """Base común de todos los modelos de la central."""


class EstudianteCentral(Base):
    """Fila sincronizada desde un origen.

    La clave de negocio es ``(origen, id_origen)``: permite que el mismo
    estudiante aparezca en varios orígenes sin pisarse entre sí y hace que
    la sincronización sea idempotente.
    """

    __tablename__ = "estudiantes"
    __table_args__ = (UniqueConstraint("origen", "id_origen", name="uq_central_origen_id"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    origen: Mapped[str] = mapped_column(String(30), nullable=False, index=True)
    id_origen: Mapped[int] = mapped_column(Integer, nullable=False)
    dni: Mapped[str] = mapped_column(String(9), nullable=False, index=True)
    nombre: Mapped[str] = mapped_column(String(100), nullable=False)
    casa: Mapped[str] = mapped_column(String(30), nullable=False)
    patronus: Mapped[str] = mapped_column(String(50), nullable=False)
    actualizado: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, server_default=func.now()
    )

    def as_dict(self) -> dict[str, object]:
        """Representación JSON de la fila."""
        return {
            "id": self.id,
            "origen": self.origen,
            "id_origen": self.id_origen,
            "dni": self.dni,
            "nombre": self.nombre,
            "casa": self.casa,
            "patronus": self.patronus,
            "actualizado": self.actualizado.isoformat() if self.actualizado else None,
        }


class PendienteReintento(Base):
    """Registro de un upsert parcialmente fallido, pendiente de reintento.

    La central no simula una transacción distribuida: si un backend falla se
    deja constancia aquí y el siguiente ``sync`` vuelve a intentarlo.
    """

    __tablename__ = "pendientes_reintento"
    __table_args__ = (UniqueConstraint("backend", "origen", "id_origen", name="uq_pendiente"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    backend: Mapped[str] = mapped_column(String(20), nullable=False)
    origen: Mapped[str] = mapped_column(String(30), nullable=False)
    id_origen: Mapped[int] = mapped_column(Integer, nullable=False)
    dni: Mapped[str] = mapped_column(String(9), nullable=False)
    nombre: Mapped[str] = mapped_column(String(100), nullable=False)
    casa: Mapped[str] = mapped_column(String(30), nullable=False)
    patronus: Mapped[str] = mapped_column(String(50), nullable=False)
    motivo: Mapped[str] = mapped_column(String(400), nullable=False, default="")
    intentos: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    creado: Mapped[datetime] = mapped_column(DateTime, nullable=False, server_default=func.now())
