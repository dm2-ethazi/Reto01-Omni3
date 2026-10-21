"""Jerarquía de excepciones de la aplicación y su traducción a HTTP.

Formato de respuesta de error (idéntico en todos los endpoints)::

    {"error": {"codigo": "C-404", "mensaje": "...", "detalle": {...}}}
"""

from __future__ import annotations

from typing import Any


class AppError(Exception):
    """Error de aplicación con código HTTP y detalle estructurado."""

    def __init__(
        self,
        codigo: str,
        mensaje: str,
        status_code: int = 400,
        detalle: Any = None,
    ) -> None:
        super().__init__(mensaje)
        self.codigo = codigo
        self.mensaje = mensaje
        self.status_code = status_code
        self.detalle = detalle

    def to_payload(self) -> dict[str, Any]:
        """Convierte el error al cuerpo JSON de la respuesta."""
        return {"error": {"codigo": self.codigo, "mensaje": self.mensaje, "detalle": self.detalle}}


class ValidacionError(AppError):
    """Entrada mal formada o datos inválidos (400)."""

    def __init__(self, mensaje: str, detalle: Any = None) -> None:
        super().__init__("C-400", mensaje, 400, detalle)


class NoEncontradoError(AppError):
    """El recurso solicitado no existe (404)."""

    def __init__(self, mensaje: str, detalle: Any = None) -> None:
        super().__init__("C-404", mensaje, 404, detalle)


class ErrorOrigen(AppError):
    """Fallo al hablar con un conector/base de origen (502)."""

    def __init__(self, mensaje: str, detalle: Any = None) -> None:
        super().__init__("C-502", mensaje, 502, detalle)


class ServicioNoDisponible(AppError):
    """Dependencia caída o no configurada (503)."""

    def __init__(self, mensaje: str, detalle: Any = None) -> None:
        super().__init__("C-503", mensaje, 503, detalle)
