"""Endpoint de salud: siempre HTTP 200, con estado ``ok`` o ``degraded``."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Request

from app.api.deps import get_central_service, get_connector_service
from app.services.central_service import CentralService
from app.services.connector_service import ConnectorService

router = APIRouter(tags=["salud"])


def _estado_central(service: CentralService) -> dict[str, Any]:
    """Estado por backend de la central sin hacer queries costosas."""
    repositorio = service.repository
    estado: dict[str, Any] = {"pendientes_reintento": repositorio.pendientes()}
    for repo in repositorio.repos:
        disponible = repo.ping()
        estado[repo.name] = {
            "disponible": disponible,
            "detalle": "disponible" if disponible else "sin respuesta",
        }
    return estado


@router.get(
    "/health",
    summary="Salud del backend, la central y los conectores",
)
def health(
    request: Request,
    connector_service: ConnectorService = Depends(get_connector_service),
    central_service: CentralService = Depends(get_central_service),
) -> dict[str, Any]:
    """Devuelve ``status: ok|degraded`` y ``reto: R01``.

    Responde 200 aunque algún conector esté caído: el detalle de cada
    dependencia va dentro del cuerpo.
    """
    central = _estado_central(central_service)
    conectores = connector_service.listar_estados()

    caidos = [
        cid for cid, info in conectores.items() if not info.get("disponible")
    ]
    # Sólo se marca la central como caída si MariaDB está configurado y no
    # responde; si está deshabilitada por diseño, no es una caída.
    mariadb_configurada = request.app.state.settings.mariadb_enabled
    if mariadb_configurada and not central.get("mariadb", {}).get("disponible", False):
        caidos.append("central.mariadb")

    status = "ok" if not caidos else "degraded"
    registry = connector_service.registry

    return {
        "status": status,
        "reto": "R01",
        "central": central,
        "conectores": conectores,
        "jvm": {
            "arrancada": registry.jvm_iniciada,
            "avisos": registry.avisos,
        },
        "errores": central.get("errores", []),
        "caidos": caidos,
        "app": {
            "nombre": request.app.state.settings.app_name,
            "version": request.app.state.settings.app_version,
        },
    }
