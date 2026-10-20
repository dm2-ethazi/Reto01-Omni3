"""Middleware HTTP: trazabilidad de accesos con ``X-Request-ID``."""

from __future__ import annotations

import logging
import time
import uuid
from collections.abc import Awaitable, Callable

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("r01.accesos")


class RequestIdMiddleware(BaseHTTPMiddleware):
    """Asigna un ``X-Request-ID`` a cada petición y registra método, ruta,
    estado HTTP y duración. Los errores se registran con el mismo identificador
    para poder correlacionarlos con el fichero de log.
    """

    async def dispatch(
        self,
        request: Request,
        call_next: Callable[[Request], Awaitable[Response]],
    ) -> Response:
        incoming = request.headers.get("X-Request-ID", "").strip()
        request_id = incoming or uuid.uuid4().hex[:16]
        request.state.request_id = request_id

        start = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            duration_ms = (time.perf_counter() - start) * 1000
            logger.exception(
                "request_id=%s metodo=%s ruta=%s estado=500 duracion_ms=%.1f error_inesperado",
                request_id,
                request.method,
                request.url.path,
                duration_ms,
            )
            raise

        duration_ms = (time.perf_counter() - start) * 1000
        response.headers["X-Request-ID"] = request_id
        logger.info(
            "request_id=%s metodo=%s ruta=%s estado=%s duracion_ms=%.1f",
            request_id,
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )
        return response
