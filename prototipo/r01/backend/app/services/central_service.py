"""Servicio de consulta sobre la central doble (MariaDB/MySQL + SQLite)."""

from __future__ import annotations

import logging
from typing import Any

from app.core.exceptions import NoEncontradoError, ValidacionError
from app.persistence.repositories.composite import CompositeCentralRepository
from app.services.transform import normalizar_dni, normalizar_espacios

logger = logging.getLogger(__name__)

MAX_LIMIT = 500


class CentralService:
    """Lecturas y comprobaciones de consistencia de la central."""

    def __init__(self, central: CompositeCentralRepository) -> None:
        self._central = central

    @property
    def repository(self) -> CompositeCentralRepository:
        """Repositorio compuesto (lo usa el health check)."""
        return self._central

    def buscar(
        self,
        *,
        nombre: str | None = None,
        dni: str | None = None,
        offset: int = 0,
        limit: int = 50,
    ) -> dict[str, Any]:
        """Consulta paginada con filtros opcionales por nombre o DNI."""
        if offset < 0:
            raise ValidacionError("El offset no puede ser negativo", {"offset": offset})
        if limit < 1 or limit > MAX_LIMIT:
            raise ValidacionError(f"El limit debe estar entre 1 y {MAX_LIMIT}", {"limit": limit})

        nombre_limpio = normalizar_espacios(nombre) if nombre else None
        dni_limpio = normalizar_dni(dni) if dni else None

        items, backend = self._central.search(
            nombre=nombre_limpio or None,
            dni=dni_limpio or None,
            offset=offset,
            limit=limit,
        )
        total = self._central.count(nombre=nombre_limpio or None, dni=dni_limpio or None)
        return {
            "items": items,
            "total": total,
            "offset": offset,
            "limit": limit,
            "backend": backend,
            "filtros": {"nombre": nombre_limpio, "dni": dni_limpio},
        }

    def por_dni(self, dni: str) -> dict[str, Any]:
        """Consulta por DNI (404 si no está en la central)."""
        dni_limpio = normalizar_dni(dni)
        row, backend = self._central.get_by_dni(dni_limpio)
        if row is None:
            raise NoEncontradoError(
                f"No hay ningún estudiante con DNI {dni_limpio} en la central",
                {"dni": dni_limpio},
            )
        row["backend"] = backend
        return row

    def consistencia(self) -> dict[str, Any]:
        """Conteos y huellas por origen en cada backend de la central."""
        resultado = self._central.counts_and_hashes()
        logger.info(
            "consistencia=%s backends=%s pendientes=%s",
            resultado["coincide"],
            list(resultado["backends"]),
            resultado["pendientes_reintento"],
        )
        return resultado
