"""Servicio de sincronización: leer origen -> transformar -> insertar en central."""

from __future__ import annotations

import logging
import time
from typing import Any

from app.core.exceptions import ValidacionError
from app.data_access.registry import ConnectorRegistry
from app.persistence.repositories.composite import CompositeCentralRepository
from app.services.transform import transform_row

logger = logging.getLogger(__name__)


class SyncService:
    """Ejecuta el flujo completo 4.4 para un conector o para todos."""

    def __init__(
        self,
        registry: ConnectorRegistry,
        central: CompositeCentralRepository,
        page_size: int = 100,
    ) -> None:
        self._registry = registry
        self._central = central
        self._page_size = max(1, page_size)

    def sync(self, connector_id: str) -> dict[str, Any]:
        """Sincroniza un conector concreto contra la central doble."""
        if connector_id not in self._registry.ids():
            raise ValidacionError(
                f"Conector desconocido: {connector_id!r}",
                {"conectores": self._registry.ids()},
            )

        inicio = time.perf_counter()

        # 1) Se reintentan antes los upserts que quedaron pendientes.
        reintentos = self._central.retry_pending()

        datasource = self._registry.get(connector_id)
        leidos = 0
        transformados: list[dict[str, Any]] = []
        errores = 0
        detalle_errores: list[dict[str, Any]] = []

        total = datasource.count()
        offset = 0
        while offset < max(total, 1):
            pagina = datasource.list(offset=offset, limit=self._page_size)
            if not pagina:
                break
            for raw in pagina:
                leidos += 1
                try:
                    transformados.append(transform_row(raw, connector_id))
                except ValidacionError as exc:
                    errores += 1
                    detalle_errores.append({"id_origen": raw.get("id"), "motivo": exc.mensaje})
                    logger.warning(
                        "Fila inválida en %s id=%s: %s",
                        connector_id,
                        raw.get("id"),
                        exc.mensaje,
                    )
            offset += self._page_size

        # 2) Upsert en la central (MariaDB + SQLite, con cola de reintentos).
        resultado = self._central.upsert(transformados)

        duracion_ms = round((time.perf_counter() - inicio) * 1000, 1)
        resumen = self._resumen(
            connector_id,
            leidos=leidos,
            errores_transformacion=errores,
            detalle_errores=detalle_errores,
            upsert=resultado,
            reintentos=reintentos,
            duracion_ms=duracion_ms,
        )
        logger.info(
            "sync(%s): leidos=%d insertados=%d actualizados=%d errores=%d duracion_ms=%.1f",
            connector_id,
            resumen["leidos"],
            resumen["insertados"],
            resumen["actualizados"],
            resumen["errores"],
            duracion_ms,
        )
        return resumen

    def sync_all(self) -> dict[str, Any]:
        """Sincroniza todos los conectores configurados."""
        resultados = [self.sync(connector_id) for connector_id in self._registry.ids()]
        return {
            "conectores": resultados,
            "insertados": sum(item["insertados"] for item in resultados),
            "actualizados": sum(item["actualizados"] for item in resultados),
            "errores": sum(item["errores"] for item in resultados),
        }

    def _resumen(
        self,
        connector_id: str,
        *,
        leidos: int,
        errores_transformacion: int,
        detalle_errores: list[dict[str, Any]],
        upsert: Any,
        reintentos: dict[str, Any],
        duracion_ms: float,
    ) -> dict[str, Any]:
        """Construye el resumen {leidos, insertados, actualizados, errores}.

        ``insertados``/``actualizados`` se reportan como el máximo entre
        backends para no doblar la cuenta (los dos backends reflejan la misma
        fila); el detalle por backend va en ``central``.
        """
        insertados = max(
            (stats.insertados for stats in upsert.por_backend.values()), default=0
        )
        actualizados = max(
            (stats.actualizados for stats in upsert.por_backend.values()), default=0
        )
        errores = errores_transformacion + len(upsert.errores)
        return {
            "conector": connector_id,
            "leidos": leidos,
            "insertados": insertados,
            "actualizados": actualizados,
            "errores": errores,
            "errores_transformacion": errores_transformacion,
            "detalle_errores": detalle_errores[:20],
            "central": {
                backend: {
                    "insertados": stats.insertados,
                    "actualizados": stats.actualizados,
                    "errores": stats.errores,
                }
                for backend, stats in upsert.por_backend.items()
            },
            "errores_central": upsert.errores,
            "pendientes_reintento": upsert.pendientes_reintento,
            "reintentos": {backend: stats.__dict__ for backend, stats in reintentos.items()},
            "duracion_ms": duracion_ms,
        }
