"""Endpoints de sincronización (flujo 4.4)."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.api.deps import get_sync_service
from app.api.schemas.salud import SyncResumen
from app.services.sync_service import SyncService

router = APIRouter(prefix="/sync", tags=["sincronizacion"])


@router.post(
    "/{connector_id}",
    response_model=SyncResumen,
    summary="Sincroniza un conector concreto con la central",
)
def sincronizar_conector(
    connector_id: str,
    service: SyncService = Depends(get_sync_service),
) -> dict[str, Any]:
    """Lee el origen, transforma, inserta en la central doble y devuelve resumen."""
    return service.sync(connector_id)


@router.post("", response_model=dict, summary="Sincroniza todos los conectores")
def sincronizar_todo(
    service: SyncService = Depends(get_sync_service),
) -> dict[str, Any]:
    """Ejecuta el flujo completo para todos los conectores configurados."""
    return service.sync_all()
