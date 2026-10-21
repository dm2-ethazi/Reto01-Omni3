"""Esquemas Pydantic v2 de las respuestas de estudiantes."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.services.transform import normalizar_dni, normalizar_nombre, validar_dni


class EstudianteBase(BaseModel):
    """Campos comunes del modelo Estudiante."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    dni: str = Field(..., description="DNI/NIE en formato 12345678Z", examples=["12345678Z"])
    nombre: str = Field(..., min_length=1, max_length=100)
    casa: str = Field(..., min_length=1, max_length=30)
    patronus: str = Field(..., min_length=1, max_length=50)

    @field_validator("dni")
    @classmethod
    def _dni_valido(cls, value: str) -> str:
        return validar_dni(value)

    @field_validator("nombre")
    @classmethod
    def _nombre_normalizado(cls, value: str) -> str:
        return normalizar_nombre(value)

    @field_validator("casa", "patronus")
    @classmethod
    def _sin_espacios_raros(cls, value: str) -> str:
        return " ".join(value.split())


class EstudianteCreate(EstudianteBase):
    """Alta en un origen (el id lo asigna el propio origen si no llega)."""

    id: int | None = Field(None, ge=1, description="Opcional: id en el origen")


class EstudianteUpdate(BaseModel):
    """Actualización parcial: todos los campos opcionales."""

    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)

    dni: str | None = None
    nombre: str | None = None
    casa: str | None = None
    patronus: str | None = None

    @field_validator("dni")
    @classmethod
    def _dni_valido(cls, value: str | None) -> str | None:
        return validar_dni(value) if value else value

    @field_validator("nombre")
    @classmethod
    def _nombre_normalizado(cls, value: str | None) -> str | None:
        return normalizar_nombre(value) if value else value

    def solo_campos(self) -> dict[str, Any]:
        """Devuelve sólo los campos informados (para el update parcial)."""
        return {key: value for key, value in self.model_dump().items() if value is not None}


class EstudianteOut(BaseModel):
    """Respuesta CRUD de un estudiante en un origen."""

    model_config = ConfigDict(extra="forbid")

    id: int
    dni: str
    nombre: str
    casa: str
    patronus: str

    @field_validator("dni")
    @classmethod
    def _dni_mayusculas(cls, value: str) -> str:
        return normalizar_dni(value)


class PaginaEstudiantes(BaseModel):
    """Página de resultados de un origen."""

    items: list[EstudianteOut]
    total: int
    offset: int
    limit: int
    conector: str


class EstudianteCentralOut(BaseModel):
    """Fila de la central (con trazabilidad de origen)."""

    model_config = ConfigDict(extra="forbid")

    id: int
    origen: str
    id_origen: int
    dni: str
    nombre: str
    casa: str
    patronus: str
    actualizado: datetime | str | None = None
    backend: str | None = None


class PaginaCentral(BaseModel):
    """Página de resultados de la central."""

    items: list[EstudianteCentralOut]
    total: int
    offset: int
    limit: int
    backend: str
    filtros: dict[str, Any]
