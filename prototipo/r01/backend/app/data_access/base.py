"""Contratos de la capa de acceso a datos a los orígenes."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

#: Tipo esperado de cada campo del modelo en el lado Python.
TIPOS_MODELO: dict[str, type] = {
    "id": int,
    "dni": str,
    "nombre": str,
    "casa": str,
    "patronus": str,
}

#: Campos del modelo que siempre viajan juntos en las respuestas.
CAMPOS_MODELO: tuple[str, ...] = ("id", "dni", "nombre", "casa", "patronus")


@dataclass(slots=True)
class ConnectorSpec:
    """Definición de un conector: todo lo variable vive aquí (no en el código)."""

    id: str
    nombre: str
    driver_class: str
    url: str
    jars: tuple[Path, ...]
    user: str
    password: str
    dialect: str
    table: str
    columns: dict[str, str] = field(default_factory=dict)
    page_size: int = 50

    def credentials(self) -> dict[str, str]:
        """Credenciales sólo si tienen valor (evita ``user=''`` en JDBC)."""
        props: dict[str, str] = {}
        if self.user:
            props["user"] = self.user
        if self.password:
            props["password"] = self.password
        return props


class DataSource(ABC):
    """Interfaz única de un origen de datos (la consumen servicios y API)."""

    @property
    @abstractmethod
    def spec(self) -> ConnectorSpec:
        """Especificación del conector."""

    @property
    @abstractmethod
    def id(self) -> str:
        """Identificador estable del conector."""

    @abstractmethod
    def ping(self) -> bool:
        """Comprueba que el origen responde."""

    @abstractmethod
    def list(
        self,
        *,
        offset: int = 0,
        limit: int = 50,
        filters: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        """Lista paginada de estudiantes del origen."""

    @abstractmethod
    def count(self, filters: dict[str, Any] | None = None) -> int:
        """Total de filas que cumplen los filtros."""

    @abstractmethod
    def get(self, row_id: int) -> dict[str, Any] | None:
        """Devuelve un estudiante por su id en el origen."""

    @abstractmethod
    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        """Alta en el origen y devuelve la fila creada."""

    @abstractmethod
    def update(self, row_id: int, data: dict[str, Any]) -> dict[str, Any] | None:
        """Actualización parcial en el origen."""

    @abstractmethod
    def delete(self, row_id: int) -> bool:
        """Borra la fila y devuelve si existía."""
