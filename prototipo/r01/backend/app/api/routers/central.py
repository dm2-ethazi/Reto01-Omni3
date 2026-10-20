"""Consultas sobre la central doble y comprobación de consistencia."""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, Depends, Path, Query

from app.api.deps import get_central_service
from app.api.schemas.estudiante import EstudianteCentralOut, PaginaCentral
from app.services.central_service import CentralService

router = APIRouter(prefix="/central", tags=["central"])


@router.get("/estudiantes", response_model=PaginaCentral, summary="Consulta la central")
def buscar_en_central(
    nombre: Annotated[str | None, Query(max_length=100, description="Búsqueda por nombre (parcial)")] = None,
    dni: Annotated[str | None, Query(max_length=9, description="DNI exacto")] = None,
    offset: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=500)] = 50,
    service: CentralService = Depends(get_central_service),
) -> dict[str, Any]:
    """Consulta paginada de la central con filtros opcionales."""
    return service.buscar(nombre=nombre, dni=dni, offset=offset, limit=limit)


@router.get(
    "/estudiantes/{dni}",
    response_model=EstudianteCentralOut,
    summary="Consulta por DNI",
)
def buscar_por_dni(
    dni: Annotated[str, Path(min_length=9, max_length=9)],
    service: CentralService = Depends(get_central_service),
) -> dict[str, Any]:
    """Devuelve 404 si el DNI no está en la central."""
    return service.por_dni(dni)


@router.get(
    "/consistencia",
    summary="Conteos y huellas por origen (MariaDB vs SQLite)",
)
def consistencia(
    service: CentralService = Depends(get_central_service),
) -> dict[str, Any]:
    """Compara conteos y hash de contenido entre los dos backends de la central."""
    return service.consistencia()
