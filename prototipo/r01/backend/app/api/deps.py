"""Dependencias compartidas por los routers (acceso a la capa de servicios)."""

from __future__ import annotations

from fastapi import Request

from app.services.central_service import CentralService
from app.services.connector_service import ConnectorService
from app.services.sync_service import SyncService


def get_connector_service(request: Request) -> ConnectorService:
    """Servicio de conectores (capa de servicios)."""
    return request.app.state.connector_service


def get_central_service(request: Request) -> CentralService:
    """Servicio de la central (capa de servicios)."""
    return request.app.state.central_service


def get_sync_service(request: Request) -> SyncService:
    """Servicio de sincronización (capa de servicios)."""
    return request.app.state.sync_service
