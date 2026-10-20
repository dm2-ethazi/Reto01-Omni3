"""Registro de conectores: carga la configuración y arranca la JVM una sola vez."""

from __future__ import annotations

import logging
import os
import threading
from collections.abc import Mapping
from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeout
from pathlib import Path
from typing import Any

import jpype
import yaml

from app.core.exceptions import NoEncontradoError, ValidacionError
from app.data_access.base import ConnectorSpec, DataSource
from app.data_access.jdbc_connector import JdbcConnector

logger = logging.getLogger(__name__)

_JVM_LOCK = threading.Lock()
_JVM_STARTED = False


def start_jvm(jars: list[Path], timeout_seconds: int = 10) -> bool:
    """Arranca la JVM del proceso con **todos** los jars en el classpath.

    Devuelve ``True`` si la JVM se arrancó en esta llamada; ``False`` si ya
    estaba arrancada (nunca se reinicia dentro del mismo proceso).
    """
    global _JVM_STARTED
    with _JVM_LOCK:
        if jpype.isJVMStarted() or _JVM_STARTED:
            logger.info("La JVM ya estaba arrancada; se reutiliza (classpath compartido).")
            return False
        classpath = os.pathsep.join(str(jar) for jar in jars)
        logger.info(
            "Arrancando JVM con %d jars (timeout=%ss).", len(jars), timeout_seconds
        )
        jpype.startJVM(
            "-ea",
            jvmpath=jpype.getDefaultJVMPath(),
            classpath=classpath,
            convertStrings=True,
            ignoreUnrecognized=True,
        )
        _JVM_STARTED = True
        return True


class ConnectorRegistry:
    """Catálogo de conectores disponible para toda la aplicación."""

    def __init__(
        self,
        specs: list[ConnectorSpec],
        *,
        drivers_dir: Path,
        jvm_iniciada: bool,
        avisos: list[str] | None = None,
    ) -> None:
        self._specs = {spec.id: spec for spec in specs}
        self._datasources: dict[str, DataSource] = {
            spec.id: JdbcConnector(spec) for spec in specs
        }
        self._drivers_dir = drivers_dir
        self._jvm_iniciada = jvm_iniciada
        self._avisos = list(avisos or [])

    # ------------------------------------------------------------------ #
    # Carga
    # ------------------------------------------------------------------ #
    @classmethod
    def load(
        cls,
        config_path: Path,
        drivers_dir: Path,
        environ: Mapping[str, str] | None = None,
    ) -> ConnectorRegistry:
        """Lee ``connectors.yaml``, resuelve credenciales por variable de
        entorno y arranca la JVM con los jars de todos los conectores."""
        env = environ if environ is not None else os.environ
        if not config_path.exists():
            raise ValidacionError(
                f"No existe el fichero de conectores: {config_path}",
                {"solucion": "copia config/connectors.example.yaml a config/connectors.yaml"},
            )

        raw = yaml.safe_load(config_path.read_text(encoding="utf-8")) or {}
        conectores = raw.get("connectors") or {}
        if not conectores:
            raise ValidacionError("El fichero de conectores no define ningún conector")

        backend_dir = drivers_dir.parent
        avisos: list[str] = []
        specs: list[ConnectorSpec] = []
        jars: list[Path] = []

        for cid, cfg in conectores.items():
            jar_paths = tuple((backend_dir / j).resolve() for j in cfg.get("jars", []))
            for jar in jar_paths:
                if jar.exists() and jar not in jars:
                    jars.append(jar)
                elif not jar.exists():
                    avisos.append(f"{cid}: falta el driver {jar.name}")

            specs.append(
                ConnectorSpec(
                    id=str(cid),
                    nombre=str(cfg.get("nombre", cid)),
                    driver_class=str(cfg["driver_class"]),
                    url=str(cfg["url"]),
                    jars=jar_paths,
                    user=env.get(str(cfg.get("env_user", "")), ""),
                    password=env.get(str(cfg.get("env_password", "")), ""),
                    dialect=str(cfg.get("dialect", "generico")),
                    table=str(cfg.get("table", "ESTUDIANTE")),
                    columns=dict(cfg.get("columns") or {}),
                    page_size=int(cfg.get("page_size", 50)),
                )
            )

        for aviso in avisos:
            logger.warning("Registry: %s", aviso)

        jvm_iniciada = False
        if jars:
            try:
                jvm_iniciada = start_jvm(jars)
            except Exception as exc:
                avisos.append(f"No se pudo arrancar la JVM: {exc}")
                logger.error("No se pudo arrancar la JVM: %s", exc, exc_info=True)
        else:
            avisos.append("No hay ningún driver JDBC en backend/drivers")
            logger.warning("No hay ningún driver JDBC en backend/drivers")

        return cls(specs, drivers_dir=drivers_dir, jvm_iniciada=jvm_iniciada, avisos=avisos)

    # ------------------------------------------------------------------ #
    # Uso
    # ------------------------------------------------------------------ #
    def get(self, connector_id: str) -> DataSource:
        """Devuelve el conector o lanza 404."""
        try:
            return self._datasources[connector_id]
        except KeyError as exc:
            raise NoEncontradoError(
                f"Conector desconocido: {connector_id!r}",
                {"conectores": sorted(self._datasources)},
            ) from exc

    def ids(self) -> list[str]:
        """Identificadores configurados."""
        return sorted(self._datasources)

    def items(self) -> list[DataSource]:
        """Todos los conectores."""
        return list(self._datasources.values())

    @property
    def avisos(self) -> list[str]:
        """Avisos detectados al cargar la configuración."""
        return list(self._avisos)

    @property
    def jvm_iniciada(self) -> bool:
        """Indica si la JVM quedó arrancada al iniciar la aplicación."""
        return self._jvm_iniciada

    def ping(self, connector_id: str, timeout: float = 5.0) -> bool:
        """Ping con límite de tiempo para no bloquear el health check."""
        datasource = self.get(connector_id)
        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(datasource.ping)
            try:
                return bool(future.result(timeout=timeout))
            except FutureTimeout:
                logger.warning("ping(%s) superó el timeout de %.1fs", connector_id, timeout)
                return False
            except Exception:
                return False

    def status(self, timeout: float = 5.0) -> dict[str, Any]:
        """Estado de todos los conectores para ``GET /health`` y ``/connectors``."""
        resultado: dict[str, Any] = {}
        for datasource in self.items():
            spec = datasource.spec
            try:
                ok = self.ping(datasource.id, timeout=timeout)
            except Exception as exc:  # pragma: no cover - get() ya valida
                ok, detalle = False, str(exc)
            else:
                detalle = "disponible" if ok else "sin respuesta"
            resultado[datasource.id] = {
                "id": datasource.id,
                "nombre": spec.nombre,
                "dialect": spec.dialect,
                "url": spec.url,
                "disponible": ok,
                "detalle": detalle,
            }
        return resultado
