"""Utilidades compartidas: orígenes falsos y aplicación de prueba.

Las pruebas de la API no arrancan la JVM ni dependen de Docker: se inyecta
un registro de conectores falso mediante el parámetro ``registry`` de
``create_app``. La suite de integración real sí usa los contenedores.
"""

from __future__ import annotations

from typing import Any

import pytest

from app.core.config import Settings
from app.core.exceptions import NoEncontradoError
from app.data_access.base import ConnectorSpec, DataSource
from app.main import create_app

FILAS: dict[str, list[dict[str, Any]]] = {
    "derby": [
        {"id": 1, "dni": "12345678Z", "nombre": "Alicia Torres", "casa": "Gryffindor", "patronus": "Leon"},
        {"id": 2, "dni": "23456789D", "nombre": "Bruno Castillo", "casa": "Slytherin", "patronus": "Serpiente"},
        {"id": 3, "dni": "34567890V", "nombre": "Carla Mendez", "casa": "Hufflepuff", "patronus": "Tejon"},
    ],
    "h2": [
        {"id": 1, "dni": "21212121A", "nombre": "Unai Ferrer", "casa": "Ravenclaw", "patronus": "Aguila"},
        {"id": 2, "dni": "11223344B", "nombre": "Javier Mora", "casa": "Gryffindor", "patronus": "Ciervo"},
    ],
}


def _spec(conector_id: str) -> ConnectorSpec:
    return ConnectorSpec(
        id=conector_id,
        nombre=f"Origen de prueba {conector_id}",
        driver_class="org.h2.Driver",
        url=f"jdbc:h2:mem:{conector_id}",
        jars=(),
        user="",
        password="",
        dialect="h2",
        table="ESTUDIANTE",
        columns={"id": "ID", "dni": "DNI", "nombre": "NOMBRE", "casa": "CASA", "patronus": "PATRONUS"},
        page_size=50,
    )


class FakeDataSource(DataSource):
    """Origen en memoria con la misma semántica que el conector JDBC real."""

    def __init__(self, conector_id: str, filas: list[dict[str, Any]] | None = None) -> None:
        self._spec = _spec(conector_id)
        self._filas = [dict(fila) for fila in (filas if filas is not None else FILAS.get(conector_id, []))]
        self._siguiente = max((fila["id"] for fila in self._filas), default=0) + 1
        self.disponible = True

    @property
    def spec(self) -> ConnectorSpec:
        return self._spec

    @property
    def id(self) -> str:
        return self._spec.id

    def ping(self) -> bool:
        return self.disponible

    @staticmethod
    def _coincide(fila: dict[str, Any], filters: dict[str, Any] | None) -> bool:
        filtros = filters or {}
        if filtros.get("dni") and fila["dni"].upper() != filtros["dni"].upper():
            return False
        if filtros.get("nombre") and filtros["nombre"].lower() not in fila["nombre"].lower():
            return False
        if filtros.get("id"):
            return int(filtros["id"]) == fila["id"]
        return True

    def list(
        self,
        *,
        offset: int = 0,
        limit: int = 50,
        filters: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        seleccion = [fila for fila in self._filas if self._coincide(fila, filters)]
        return [dict(fila) for fila in seleccion[offset : offset + limit]]

    def count(self, filters: dict[str, Any] | None = None) -> int:
        return sum(1 for fila in self._filas if self._coincide(fila, filters))

    def get(self, row_id: int) -> dict[str, Any] | None:
        for fila in self._filas:
            if fila["id"] == row_id:
                return dict(fila)
        return None

    def create(self, data: dict[str, Any]) -> dict[str, Any]:
        fila = {"id": self._siguiente, **data}
        self._siguiente += 1
        self._filas.append(fila)
        return dict(fila)

    def update(self, row_id: int, data: dict[str, Any]) -> dict[str, Any] | None:
        for fila in self._filas:
            if fila["id"] == row_id:
                fila.update(data)
                return dict(fila)
        return None

    def delete(self, row_id: int) -> bool:
        for indice, fila in enumerate(self._filas):
            if fila["id"] == row_id:
                self._filas.pop(indice)
                return True
        return False


class FakeRegistry:
    """Registro mínimo con la misma superficie que ``ConnectorRegistry``."""

    def __init__(self, datasources: list[DataSource]) -> None:
        self._datasources = {datasource.id: datasource for datasource in datasources}
        self.avisos: list[str] = []
        self.jvm_iniciada = False

    def get(self, connector_id: str) -> DataSource:
        try:
            return self._datasources[connector_id]
        except KeyError as exc:
            raise NoEncontradoError(
                f"Conector desconocido: {connector_id!r}",
                {"conectores": sorted(self._datasources)},
            ) from exc

    def ids(self) -> list[str]:
        return sorted(self._datasources)

    def items(self) -> list[DataSource]:
        return list(self._datasources.values())

    def ping(self, connector_id: str, timeout: float = 5.0) -> bool:
        return bool(self.get(connector_id).ping())

    def status(self, timeout: float = 5.0) -> dict[str, Any]:
        return {
            datasource.id: {
                "id": datasource.id,
                "nombre": datasource.spec.nombre,
                "dialect": datasource.spec.dialect,
                "url": datasource.spec.url,
                "disponible": datasource.ping(),
                "detalle": "disponible" if datasource.ping() else "sin respuesta",
            }
            for datasource in self.items()
        }


@pytest.fixture
def fake_registry() -> FakeRegistry:
    """Dos orígenes falsos: ``derby`` (3 filas) y ``h2`` (2 filas)."""
    return FakeRegistry([FakeDataSource("derby"), FakeDataSource("h2")])


@pytest.fixture
def app_settings(tmp_path) -> Settings:
    """Ajustes de prueba: SQLite temporal y central MariaDB deshabilitada."""
    return Settings(
        sqlite_central_path=tmp_path / "central.db",
        mariadb_enabled=False,
        log_dir=tmp_path / "logs",
        environment="test",
    )


@pytest.fixture
def client(app_settings, fake_registry):
    """Cliente de prueba de la API con los conectores falsos."""
    from fastapi.testclient import TestClient

    app = create_app(settings=app_settings, registry=fake_registry)
    with TestClient(app) as test_client:
        yield test_client
