"""Traducción de excepciones al formato de error unificado del proyecto.

Formato::

    {"error": {"codigo": "C-404", "mensaje": "...", "detalle": {...}}}
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.exceptions import AppError

logger = logging.getLogger("r01.errores")

CODIGOS_HTTP = {
    400: "C-400",
    404: "C-404",
    422: "C-422",
    500: "C-500",
    502: "C-502",
    503: "C-503",
}


def _envelope(codigo: str, mensaje: str, detalle=None) -> dict:
    return {"error": {"codigo": codigo, "mensaje": mensaje, "detalle": detalle}}


def _sanitizar_errores(errores: list[dict]) -> list[dict]:
    """Elimina del error de Pydantic todo lo no serializable a JSON."""
    limpios = []
    for error in errores:
        limpios.append(
            {
                "campo": ".".join(str(parte) for parte in error.get("loc", [])),
                "mensaje": error.get("msg", ""),
                "tipo": error.get("type", ""),
            }
        )
    return limpios


def install_error_handlers(app: FastAPI) -> None:
    """Registra los handlers globales de error."""

    @app.exception_handler(AppError)
    async def _app_error(request: Request, exc: AppError) -> JSONResponse:
        logger.error(
            "request_id=%s codigo=%s status=%s mensaje=%s",
            getattr(request.state, "request_id", "-"),
            exc.codigo,
            exc.status_code,
            exc.mensaje,
        )
        return JSONResponse(status_code=exc.status_code, content=exc.to_payload())

    @app.exception_handler(RequestValidationError)
    async def _validacion(request: Request, exc: RequestValidationError) -> JSONResponse:
        logger.warning(
            "request_id=%s codigo=C-422 ruta=%s",
            getattr(request.state, "request_id", "-"),
            request.url.path,
        )
        return JSONResponse(
            status_code=422,
            content=_envelope(
                "C-422",
                "Datos de entrada inválidos",
                _sanitizar_errores(list(exc.errors())),
            ),
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        codigo = CODIGOS_HTTP.get(exc.status_code, f"C-{exc.status_code}")
        detalle = exc.detail if isinstance(exc.detail, (dict, list)) else None
        mensaje = exc.detail if isinstance(exc.detail, str) else "Error HTTP"
        return JSONResponse(
            status_code=exc.status_code,
            content=_envelope(codigo, mensaje, detalle),
        )

    @app.exception_handler(Exception)
    async def _inesperado(request: Request, exc: Exception) -> JSONResponse:
        logger.exception(
            "request_id=%s ruta=%s error=%s",
            getattr(request.state, "request_id", "-"),
            request.url.path,
            exc,
        )
        return JSONResponse(
            status_code=500,
            content=_envelope("C-500", "Error interno del servidor", {"tipo": type(exc).__name__}),
        )
