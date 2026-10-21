"""Servicio de conectores: CRUD sobre los orígenes con validación previa."""

from __future__ import annotations

import logging
from typing import Any

from app.core.exceptions import NoEncontradoError, ValidacionError
from app.data_access.registry import ConnectorRegistry
from app.services.transform import normalizar_dni, normalizar_nombre, validar_dni

logger = logging.getLogger(__name__)

FILTROS_PERMITIDOS = {"id", "dni", "nombre", "casa", "patronus"}
MAX_LIMIT = 500


def _normalizar_filtros(filtros: dict[str, Any] | None) -> dict[str, Any]:
    """Valida la estructura de los filtros de búsqueda."""
    if not filtros:
        return {}
    desconocidos = [key for key in filtros if key not in FILTROS_PERMITIDOS]
    if desconocidos:
        raise ValidacionError("Filtros no admitidos", {"filtros": desconocidos})
    limpio: dict[str, Any] = {}
    for key, value in filtros.items():
        if value is None or value == "":
            continue
        limpio[key] = normalizar_dni(value) if key == "dni" else str(value)
    return limpio


def _validar_paginacion(offset: int, limit: int) -> tuple[int, int]:
    if offset < 0:
        raise ValidacionError("El offset no puede ser negativo", {"offset": offset})
    if limit < 1 or limit > MAX_LIMIT:
        raise ValidacionError(
            f"El limit debe estar entre 1 y {MAX_LIMIT}", {"limit": limit}
        )
    return offset, limit


class ConnectorService:
    """Un servicio por operación sobre los conectores (capa de servicios)."""

    def __init__(self, registry: ConnectorRegistry) -> None:
        self._registry = registry

    @property
    def registry(self) -> ConnectorRegistry:
        """Registro subyacente (lo usa el health check)."""
        return self._registry

    def listar_estados(self) -> dict[str, Any]:
        """Estado de todos los conectores."""
        return self._registry.status()

    def listar(
        self,
        connector_id: str,
        *,
        offset: int = 0,
        limit: int = 50,
        filtros: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Consulta paginada en el origen."""
        offset, limit = _validar_paginacion(offset, limit)
        datasource = self._registry.get(connector_id)
        filters = _normalizar_filtros(filtros)
        items = datasource.list(offset=offset, limit=limit, filters=filters)
        total = datasource.count(filters)
        logger.info(
            "conector=%s operacion=list offset=%d limit=%d total=%d",
            connector_id,
            offset,
            limit,
            total,
        )
        return {
            "items": items,
            "total": total,
            "offset": offset,
            "limit": limit,
            "conector": connector_id,
        }

    def obtener(self, connector_id: str, row_id: int) -> dict[str, Any]:
        """Detalle de un estudiante en el origen."""
        datasource = self._registry.get(connector_id)
        row = datasource.get(row_id)
        if row is None:
            raise NoEncontradoError(
                f"No existe el estudiante {row_id} en el origen '{connector_id}'",
                {"conector": connector_id, "id": row_id},
            )
        return row

    def crear(self, connector_id: str, data: dict[str, Any]) -> dict[str, Any]:
        """Alta validada en el origen."""
        datasource = self._registry.get(connector_id)
        payload = self._limpiar(data)
        creado = datasource.create(payload)
        logger.info("conector=%s operacion=create id=%s", connector_id, creado.get("id"))
        return creado

    def actualizar(self, connector_id: str, row_id: int, data: dict[str, Any]) -> dict[str, Any]:
        """Actualización parcial validada en el origen."""
        datasource = self._registry.get(connector_id)
        payload = self._limpiar(data, permitir_id=False)
        actualizado = datasource.update(row_id, payload)
        if actualizado is None:
            raise NoEncontradoError(
                f"No existe el estudiante {row_id} en el origen '{connector_id}'",
                {"conector": connector_id, "id": row_id},
            )
        logger.info("conector=%s operacion=update id=%s", connector_id, row_id)
        return actualizado

    def borrar(self, connector_id: str, row_id: int) -> None:
        """Borrado en el origen."""
        datasource = self._registry.get(connector_id)
        if not datasource.delete(row_id):
            raise NoEncontradoError(
                f"No existe el estudiante {row_id} en el origen '{connector_id}'",
                {"conector": connector_id, "id": row_id},
            )
        logger.info("conector=%s operacion=delete id=%s", connector_id, row_id)

    @staticmethod
    def _limpiar(data: dict[str, Any], permitir_id: bool = True) -> dict[str, Any]:
        """Normaliza y valida el payload antes de mandarlo al conector."""
        if not isinstance(data, dict) or not data:
            raise ValidacionError("El cuerpo de la petición está vacío")

        payload: dict[str, Any] = {}
        for key, value in data.items():
            if key == "id" and not permitir_id:
                raise ValidacionError("El campo 'id' no se puede modificar")
            if key == "dni":
                payload[key] = validar_dni(str(value))
            elif key == "nombre":
                payload[key] = normalizar_nombre(str(value))
            elif key in {"casa", "patronus"}:
                payload[key] = " ".join(str(value).split())
            else:
                payload[key] = value
        return payload
