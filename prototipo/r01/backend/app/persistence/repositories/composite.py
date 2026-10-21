"""Repositorio compuesto: escritura en la central doble con fallos parciales."""

from __future__ import annotations

import logging
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from typing import Any

from app.persistence.repositories.base import CentralRepository, UpsertStats
from app.persistence.repositories.retry_queue import SqliteRetryQueue

logger = logging.getLogger(__name__)


@dataclass
class CompositeUpsertResult:
    """Resultado agregado de un upsert en todos los backends de la central."""

    por_backend: dict[str, UpsertStats] = field(default_factory=dict)
    pendientes_reintento: int = 0
    errores: list[str] = field(default_factory=list)

    @property
    def insertados(self) -> int:
        """Suma de filas insertadas en todos los backends."""
        return sum(stats.insertados for stats in self.por_backend.values())

    @property
    def actualizados(self) -> int:
        """Suma de filas actualizadas en todos los backends."""
        return sum(stats.actualizados for stats in self.por_backend.values())


class CompositeCentralRepository:
    """Agrupa los repositorios de la central (MariaDB + SQLite).

    * La escritura se intenta en **todos** los backends.
    * Si uno falla, el error se registra y las filas se marcan en la cola de
      reintentos (no se finge una transacción distribuida).
    * Las lecturas usan el primer backend que responda, con MariaDB primero.
    """

    def __init__(
        self,
        repos: Sequence[CentralRepository],
        retry_queue: SqliteRetryQueue,
    ) -> None:
        self._repos = list(repos)
        self._retry_queue = retry_queue

    @property
    def repos(self) -> list[CentralRepository]:
        """Backends configurados."""
        return list(self._repos)

    def upsert(self, rows: Sequence[Mapping[str, Any]]) -> CompositeUpsertResult:
        """Escribe en todos los backends y deja traza de los fallos."""
        result = CompositeUpsertResult()
        for repo in self._repos:
            try:
                stats = repo.upsert(rows)
                result.por_backend[repo.name] = stats
            except Exception as exc:
                logger.error("Fallo al escribir en %s: %s", repo.name, exc, exc_info=True)
                result.errores.append(f"{repo.name}: {exc}")
                result.por_backend[repo.name] = UpsertStats(errores=len(rows))
                result.pendientes_reintento += self._retry_queue.mark(
                    repo.name, rows, str(exc)
                )
        return result

    def retry_pending(self) -> dict[str, UpsertStats]:
        """Reintenta las filas marcadas en la cola para cada backend."""
        resumen: dict[str, UpsertStats] = {}
        for repo in self._repos:
            pendientes = self._retry_queue.pending(repo.name)
            if not pendientes:
                resumen[repo.name] = UpsertStats()
                continue
            stats = UpsertStats()
            for fila in pendientes:
                try:
                    parcial = repo.upsert([fila])
                    stats.insertados += parcial.insertados
                    stats.actualizados += parcial.actualizados
                    self._retry_queue.resolve(repo.name, fila["origen"], fila["id_origen"])
                except Exception as exc:
                    logger.warning(
                        "Reintento fallido en %s para %s/%s: %s",
                        repo.name,
                        fila["origen"],
                        fila["id_origen"],
                        exc,
                    )
                    stats.errores += 1
            resumen[repo.name] = stats
        return resumen

    def pendientes(self) -> int:
        """Filas pendientes de reintento en toda la central."""
        return self._retry_queue.count()

    def _read_repo(self) -> CentralRepository:
        """Primer backend operativo para lecturas (MariaDB primero)."""
        ultimo_error: Exception | None = None
        for repo in self._repos:
            try:
                if repo.ping():
                    return repo
            except Exception as exc:  # pragma: no cover - ping ya captura
                ultimo_error = exc
        raise RuntimeError(f"Ningún backend de la central responde: {ultimo_error}")

    def search(
        self,
        *,
        nombre: str | None = None,
        dni: str | None = None,
        offset: int = 0,
        limit: int = 50,
    ) -> tuple[list[dict[str, Any]], str]:
        """Consulta la central y devuelve (filas, backend_utilizado)."""
        repo = self._read_repo()
        return repo.search(nombre=nombre, dni=dni, offset=offset, limit=limit), repo.name

    def get_by_dni(self, dni: str) -> tuple[dict[str, Any] | None, str]:
        """Consulta por DNI y devuelve (fila, backend_utilizado)."""
        repo = self._read_repo()
        return repo.get_by_dni(dni), repo.name

    def count(
        self, *, nombre: str | None = None, dni: str | None = None
    ) -> int:
        """Total de filas filtradas en el backend de lectura."""
        return self._read_repo().count(nombre=nombre, dni=dni)

    def counts_and_hashes(self) -> dict[str, Any]:
        """Concentrado de conteos y huellas por backend, para /central/consistencia."""
        detalle: dict[str, Any] = {}
        for repo in self._repos:
            try:
                detalle[repo.name] = {
                    "disponible": True,
                    "conteos": repo.count_by_origen(),
                    "hashes": repo.hash_by_origen(),
                }
            except Exception as exc:
                detalle[repo.name] = {"disponible": False, "error": str(exc)}

        nombres = [name for name, info in detalle.items() if info.get("disponible")]
        coincide = True
        if len(nombres) >= 2:
            base = detalle[nombres[0]]
            coincide = all(
                detalle[nombre]["conteos"] == base["conteos"]
                and detalle[nombre]["hashes"] == base["hashes"]
                for nombre in nombres[1:]
            )
        return {
            "coincide": coincide,
            "backends": detalle,
            "pendientes_reintento": self.pendientes(),
        }
