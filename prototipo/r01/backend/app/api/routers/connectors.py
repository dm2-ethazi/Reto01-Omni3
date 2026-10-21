"""CRUD de estudiantes contra los orígenes (conectores)."""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Depends, Path, Query

from app.api.deps import get_connector_service
from app.api.schemas.estudiante import (
    EstudianteCreate,
    EstudianteOut,
    EstudianteUpdate,
    PaginaEstudiantes,
)
from app.services.connector_service import ConnectorService

router = APIRouter(prefix="/connectors", tags=["conectores"])

ConnId = Annotated[str, Path(description="Identificador del conector", examples=["derby"])]


@router.get("", summary="Lista de conectores y su estado")
def listar_conectores(
    service: ConnectorService = Depends(get_connector_service),
) -> dict[str, Any]:
    """Devuelve id, nombre, dialecto, URL y disponibilidad de cada conector."""
    return {"conectores": service.listar_estados()}


@router.get("/{connector_id}/estudiantes", response_model=PaginaEstudiantes)
def listar_estudiantes(
    connector_id: ConnId,
    offset: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=500)] = 50,
    dni: Annotated[str | None, Query(max_length=9)] = None,
    nombre: Annotated[str | None, Query(max_length=100)] = None,
    service: ConnectorService = Depends(get_connector_service),
) -> dict[str, Any]:
    """Consulta paginada de un origen, con filtros opcionales por DNI/nombre."""
    filtros = {"dni": dni, "nombre": nombre}
    return service.listar(connector_id, offset=offset, limit=limit, filtros=filtros)


@router.post(
    "/{connector_id}/estudiantes",
    response_model=EstudianteOut,
    status_code=201,
)
def crear_estudiante(
    connector_id: ConnId,
    payload: EstudianteCreate,
    service: ConnectorService = Depends(get_connector_service),
) -> dict[str, Any]:
    """Alta de un estudiante en el origen indicado."""
    return service.crear(connector_id, payload.model_dump(exclude_none=True))


@router.get("/{connector_id}/estudiantes/{row_id}", response_model=EstudianteOut)
def obtener_estudiante(
    connector_id: ConnId,
    row_id: int,
    service: ConnectorService = Depends(get_connector_service),
) -> dict[str, Any]:
    """Detalle de un estudiante por su id en el origen."""
    return service.obtener(connector_id, row_id)


@router.put("/{connector_id}/estudiantes/{row_id}", response_model=EstudianteOut)
def actualizar_estudiante(
    connector_id: ConnId,
    row_id: int,
    payload: EstudianteUpdate,
    service: ConnectorService = Depends(get_connector_service),
) -> dict[str, Any]:
    """Actualización parcial de un estudiante en el origen."""
    return service.actualizar(connector_id, row_id, payload.solo_campos())


@router.delete("/{connector_id}/estudiantes/{row_id}", status_code=204)
def borrar_estudiante(
    connector_id: ConnId,
    row_id: int,
    service: ConnectorService = Depends(get_connector_service),
) -> None:
    """Borra un estudiante del origen."""
    service.borrar(connector_id, row_id)
