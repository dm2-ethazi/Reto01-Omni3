"""Pruebas de integración contra los contenedores reales (Docker).

Se omiten automáticamente si los servicios no están levantados, para que la
suite unitaria pueda ejecutarse en cualquier máquina.
"""

from __future__ import annotations

import socket

import pytest

from app.core.config import get_settings, runtime_env

pytestmark = pytest.mark.integration

PUERTOS = {
    "derby": 1527,
    "hsqldb": 9001,
    "h2": 9092,
    "oracle": 1521,
}


def _responde(puerto: int, host: str = "localhost") -> bool:
    try:
        with socket.create_connection((host, puerto), timeout=1.5):
            return True
    except OSError:
        return False


@pytest.fixture(scope="module")
def registry():
    """Registro real cargado desde config/connectors.yaml (arranca la JVM)."""
    from app.data_access.registry import ConnectorRegistry

    settings = get_settings()
    return ConnectorRegistry.load(
        settings.connectors_config,
        settings.drivers_dir,
        environ=runtime_env(),
    )


@pytest.mark.parametrize("conector", sorted(PUERTOS))
def test_origen_responde(registry, conector):
    if not _responde(PUERTOS[conector]):
        pytest.skip(f"contenedor {conector} no disponible")
    assert registry.ping(conector)


def test_jvm_arranca_una_sola_vez(registry):
    if not any(_responde(puerto) for puerto in PUERTOS.values()):
        pytest.skip("ningún contenedor disponible")
    assert registry.jvm_iniciada is True


@pytest.mark.parametrize("conector", sorted(PUERTOS))
def test_semilla_de_diez_estudiantes(registry, conector):
    if not _responde(PUERTOS[conector]):
        pytest.skip(f"contenedor {conector} no disponible")
    datasource = registry.get(conector)
    assert datasource.count() == 10
    pagina = datasource.list(offset=0, limit=5)
    assert len(pagina) == 5
    assert {"id", "dni", "nombre", "casa", "patronus"} <= set(pagina[0])
    assert all(len(fila["dni"]) == 9 for fila in pagina)


@pytest.mark.parametrize("conector", sorted(PUERTOS))
def test_crud_sobre_el_origen_real(registry, conector):
    if not _responde(PUERTOS[conector]):
        pytest.skip(f"contenedor {conector} no disponible")
    datasource = registry.get(conector)
    creada = datasource.create(
        {"dni": "55443322T", "nombre": "Prueba E2E", "casa": "Hufflepuff", "patronus": "Tejon"}
    )
    try:
        assert datasource.get(creada["id"])["dni"] == "55443322T"
        actualizada = datasource.update(creada["id"], {"nombre": "Prueba E2E v2"})
        assert actualizada["nombre"] == "Prueba E2E v2"
    finally:
        assert datasource.delete(creada["id"]) is True
    assert datasource.get(creada["id"]) is None
