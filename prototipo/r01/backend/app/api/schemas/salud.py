"""Esquemas de salud, sincronización y consistencia."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict


class BackendEstado(BaseModel):
    """Estado de un backend de la central."""

    model_config = ConfigDict(extra="allow")

    disponible: bool
    detalle: str | None = None


class HealthResponse(BaseModel):
    """Respuesta de ``GET /health`` (siempre HTTP 200)."""

    status: str
    reto: str
    central: dict[str, Any]
    conectores: dict[str, Any]
    jvm: dict[str, Any] = {}
    errores: list[str] = []


class ConnectorEstado(BaseModel):
    """Estado de un conector."""

    model_config = ConfigDict(extra="allow")

    id: str
    nombre: str
    dialect: str
    url: str
    disponible: bool
    detalle: str


class SyncResumen(BaseModel):
    """Resumen del flujo leer -> transformar -> insertar -> consultar."""

    model_config = ConfigDict(extra="allow")

    conector: str
    leidos: int
    insertados: int
    actualizados: int
    errores: int
