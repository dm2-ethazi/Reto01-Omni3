"""Punto de entrada de la API FastAPI (capa API)."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI

from app.api.errors import install_error_handlers
from app.api.routers import central, connectors, health, sync
from app.core.config import Settings, get_settings, runtime_env
from app.core.logging_config import configure_logging
from app.core.middleware import RequestIdMiddleware
from app.data_access.registry import ConnectorRegistry
from app.persistence.db import build_engines, init_schema
from app.persistence.repositories.composite import CompositeCentralRepository
from app.persistence.repositories.mariadb import MariaDbRepository
from app.persistence.repositories.retry_queue import SqliteRetryQueue
from app.persistence.repositories.sqlite import SqliteRepository
from app.services.central_service import CentralService
from app.services.connector_service import ConnectorService
from app.services.sync_service import SyncService

logger = logging.getLogger(__name__)

DESCRIPTION = """
API del Reto R01: conectores a Derby/HSQLDB/H2/Oracle, central doble
MariaDB/MySQL + SQLite y flujo de sincronización con trazabilidad.
"""


def create_app(
    settings: Settings | None = None,
    registry: ConnectorRegistry | None = None,
) -> FastAPI:
    """Fabrica la aplicación.

    Parámetros:
        settings: ajustes propios (útil para pruebas).
        registry: registro de conectores precargado (las pruebas inyectan
            conectores falsos y así no se arranca la JVM).
    """
    app_settings = settings or get_settings()
    configure_logging(app_settings.log_dir, app_settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        engines = build_engines(app_settings)
        init_schema(engines)

        retry_queue = SqliteRetryQueue(engines.sqlite)
        # MariaDB es el backend principal de lecturas; SQLite es el espejo
        # que garantiza disponibilidad si MariaDB no responde.
        repos = []
        if engines.mariadb is not None:
            repos.append(MariaDbRepository(engines.mariadb))
        repos.append(SqliteRepository(engines.sqlite))
        central_repo = CompositeCentralRepository(repos, retry_queue)

        conectores = registry or ConnectorRegistry.load(
            app_settings.connectors_config,
            app_settings.drivers_dir,
            environ=runtime_env(),
        )

        app.state.settings = app_settings
        app.state.engines = engines
        app.state.central = central_repo
        app.state.registry = conectores
        app.state.connector_service = ConnectorService(conectores)
        app.state.central_service = CentralService(central_repo)
        app.state.sync_service = SyncService(
            conectores, central_repo, page_size=app_settings.sync_page_size
        )
        logger.info(
            "Aplicación arrancada: conectores=%s central_sqlite=ok central_mariadb=%s jvm=%s",
            conectores.ids(),
            engines.mariadb_disponible,
            conectores.jvm_iniciada,
        )
        yield
        for engine in (engines.sqlite, engines.mariadb):
            if engine is not None:
                engine.dispose()

    app = FastAPI(
        title=app_settings.app_name,
        version=app_settings.app_version,
        description=DESCRIPTION,
        lifespan=lifespan,
    )
    app.add_middleware(RequestIdMiddleware)
    install_error_handlers(app)

    app.include_router(health.router)
    app.include_router(connectors.router)
    app.include_router(sync.router)
    app.include_router(central.router)

    @app.get("/", tags=["raiz"], include_in_schema=False)
    def raiz() -> dict[str, Any]:
        return {
            "app": app_settings.app_name,
            "version": app_settings.app_version,
            "reto": "R01",
            "docs": "/docs",
        }

    return app


app = create_app()
