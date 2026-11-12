"""API principal R01 con ciclo de vida y reintentos automáticos."""

import asyncio
import logging
import re
from contextlib import asynccontextmanager, suppress
from time import monotonic
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from .api.alumnos import router
from .config import settings
from .db.central_mariadb import CentralMariaDB
from .db.factory import obtener_repositorios
from .db.sqlite import CentralSQLite
from .services.alumnos import ErrorServicio, ServicioAlumnos

logger = logging.getLogger(__name__)


class FiltroDNI(logging.Filter):
    def filter(self, record):
        def ocultar(valor):
            return (
                re.sub(r"\b(\d{2})\d{6}([A-Za-z])\b", r"\1***\2", valor)
                if isinstance(valor, str)
                else valor
            )

        # AccessFormatter de Uvicorn necesita conservar sus cinco argumentos.
        record.msg = ocultar(record.msg)
        if isinstance(record.args, tuple):
            record.args = tuple(ocultar(v) for v in record.args)
        elif isinstance(record.args, dict):
            record.args = {k: ocultar(v) for k, v in record.args.items()}
        return True


def construir_servicio():
    repos = obtener_repositorios()
    maria = next((r for r in repos if isinstance(r, CentralMariaDB)), None)
    nombres = {
        "Ravenclaw": "oracle",
        "Hufflepuff": "h2",
        "Slytherin": "hsqldb",
        "Gryffindor": "derby",
    }
    fuentes = {nombres[r.casa]: r for r in repos if r is not maria}
    return ServicioAlumnos(CentralSQLite(settings.sqlite_path), maria, fuentes)


def crear_app(servicio=None, reintentos=True):
    @asynccontextmanager
    async def lifespan(app):
        app.state.servicio = servicio or construir_servicio()
        registro = logging.getLogger("proyecto_reto1")
        registro.setLevel(logging.INFO)
        if not registro.handlers:
            handler = logging.StreamHandler()
            handler.setFormatter(
                logging.Formatter("%(asctime)s %(levelname)s %(message)s")
            )
            handler.addFilter(FiltroDNI())
            registro.addHandler(handler)
        logging.getLogger("uvicorn.access").addFilter(FiltroDNI())
        await asyncio.to_thread(app.state.servicio.iniciar)

        async def worker():
            while True:
                await asyncio.sleep(settings.retry_interval_seconds)
                try:
                    await asyncio.to_thread(app.state.servicio.reintentar)
                except Exception as exc:
                    logger.error("reintentos causa=%s", type(exc).__name__)

        tarea = asyncio.create_task(worker()) if reintentos else None
        try:
            yield
        finally:
            if tarea:
                tarea.cancel()
                with suppress(asyncio.CancelledError):
                    await tarea

    app = FastAPI(
        title="API Alumnos R01",
        version="2.0.0",
        lifespan=lifespan,
        description="API común, conectores Oracle/H2/HSQLDB/Derby y centrales MariaDB + SQLite con cola persistente.",
    )
    app.include_router(router)

    @app.middleware("http")
    async def correlacion(request: Request, call_next):
        recibido = request.headers.get("request-id", "")
        request.state.request_id = (
            recibido
            if re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", recibido)
            else str(uuid4())
        )
        inicio = monotonic()
        try:
            respuesta = await call_next(request)
        except Exception as exc:
            logger.error(
                "error_api request_id=%s causa=%s",
                request.state.request_id,
                type(exc).__name__,
            )
            respuesta = error(
                request,
                500,
                "Error interno; consulta el estado y el identificador de petición",
            )
        respuesta.headers["request-id"] = request.state.request_id
        ruta = getattr(request.scope.get("route"), "path", "desconocida")
        logger.info(
            "request_id=%s metodo=%s ruta=%s status=%s duracion_ms=%.1f",
            request.state.request_id,
            request.method,
            ruta,
            respuesta.status_code,
            (monotonic() - inicio) * 1000,
        )
        return respuesta

    def error(request, status, mensaje, detalle=None):
        return JSONResponse(
            status_code=status,
            content={
                "detail": mensaje,
                "error": {
                    "codigo": str(status),
                    "mensaje": mensaje,
                    "detalle": detalle,
                    "request_id": getattr(request.state, "request_id", None),
                },
            },
        )

    @app.exception_handler(ErrorServicio)
    async def error_servicio(request, exc):
        return error(request, exc.status, exc.mensaje)

    @app.exception_handler(RequestValidationError)
    async def error_validacion(request, exc):
        return error(
            request,
            422,
            "Datos no válidos",
            [{"campo": list(e["loc"]), "mensaje": e["msg"]} for e in exc.errors()],
        )

    @app.exception_handler(HTTPException)
    async def error_http(request, exc):
        return error(request, exc.status_code, str(exc.detail))

    return app


app = crear_app()
