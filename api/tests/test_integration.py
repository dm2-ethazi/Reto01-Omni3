"""Pruebas optativas contra los cinco motores reales; sin mocks de conexiones.

Solo ejecutar en bases de demostración: crea un alumno nuevo y lo da de baja,
y edita/restaura el nombre de las semillas de cada origen.
"""

import os
import secrets
import importlib.util
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from proyecto_reto1.db.base import AlumnoActualizacion
from proyecto_reto1.db.sqlite import CentralSQLite
from proyecto_reto1.main import construir_servicio, crear_app
from proyecto_reto1.services.alumnos import ServicioAlumnos

pytestmark = [
    pytest.mark.integration,
    pytest.mark.skipif(
        os.environ.get("R01_INTEGRATION") != "1",
        reason="requiere R01_INTEGRATION=1 y cinco bases de demo",
    ),
]


@pytest.fixture
def real():
    servicio = construir_servicio()
    # Usar la misma central local que la API del entorno. Un SQLite temporal
    # independiente contra la misma MariaDB simularía dos instalaciones sin
    # coordinación y dejaría versiones incompatibles para la demo posterior.
    assert set(servicio.fuentes) == {"oracle", "h2", "hsqldb", "derby"}
    assert servicio.maria is not None
    with TestClient(crear_app(servicio, reintentos=False)) as cliente:
        yield cliente, servicio


def test_cuatro_fuentes_dos_centrales_y_reinicio_real(real):
    cliente, servicio = real
    estado = cliente.get("/health").json()
    assert estado["reto"] == "R01" and estado["status"] == "ok", estado
    ids = {}
    for nombre in servicio.fuentes:
        respuesta = cliente.get(f"/connectors/{nombre}/alumnos")
        assert respuesta.status_code == 200 and respuesta.json()
        ids[nombre] = respuesta.json()[0]["id"]
    resultado = cliente.post("/sync", json={"ids": list(ids.values())})
    assert resultado.status_code == 200
    assert resultado.json()["pendientes"] == 0, resultado.json()
    espejo = {a.id: a for a in servicio.maria.listar_central()}
    for nombre, dni in ids.items():
        local = cliente.get(f"/alumnos/{dni}").json()
        assert local["origen"] == nombre
        assert espejo[dni].nombre == local["nombre"]
        assert espejo[dni].version == local["version"]
    reinicio = ServicioAlumnos(
        CentralSQLite(servicio.local.path), servicio.maria, servicio.fuentes
    )
    with TestClient(crear_app(reinicio, reintentos=False)) as otro:
        for dni in ids.values():
            assert otro.get(f"/alumnos/{dni}").status_code == 200


@pytest.mark.parametrize("origen", ["oracle", "h2", "hsqldb", "derby"])
def test_edicion_historica_real_y_conflicto(real, origen):
    cliente, servicio = real
    fuente = servicio.fuentes[origen]
    original = fuente.listar()[0]
    cliente.post("/sync", json={"origenes": [origen], "ids": [original.id]})
    version = cliente.get(f"/alumnos/{original.id}").json()["version"]
    ruta = f"/alumnos/{original.id}"
    try:
        cambio = cliente.put(
            ruta, json={"nombre": original.nombre + " prueba", "version": version}
        )
        assert cambio.status_code == 200, cambio.json()
        assert fuente.obtener(original.id).nombre == original.nombre + " prueba"
        assert (
            cliente.put(
                ruta, json={"nombre": "Obsoleto", "version": version}
            ).status_code
            == 409
        )
        central = next(
            a for a in servicio.maria.listar_central() if a.id == original.id
        )
        assert central.version == version + 1
        fuente.actualizar(
            original.id, AlumnoActualizacion(nombre=original.nombre + " externo")
        )
        conflicto = cliente.put(
            ruta, json={"nombre": "No sobrescribir", "version": version + 1}
        )
        assert conflicto.status_code == 409
        assert fuente.obtener(original.id).nombre == original.nombre + " externo"
        recarga = cliente.post(
            "/sync",
            json={
                "origenes": [origen],
                "ids": [original.id],
                "resolver_conflictos": True,
            },
        )
        assert recarga.json()["pendientes"] == 0
        assert cliente.get(ruta).json()["nombre"] == original.nombre + " externo"
    finally:
        vigente = cliente.get(ruta).json()
        if vigente["nombre"] != original.nombre:
            restauracion = cliente.put(
                ruta, json={"nombre": original.nombre, "version": vigente["version"]}
            )
            assert restauracion.status_code == 200, restauracion.json()


def test_crud_baja_y_migracion_idempotente_reales(real):
    cliente, servicio = real
    dni = str(30000000 + secrets.randbelow(10000000)) + "A"
    alta = cliente.post(
        "/alumnos", json={"id": dni, "nombre": "Prueba ADAT", "patronus": "Otter"}
    )
    assert alta.status_code == 201, alta.json()
    assert (
        cliente.post(
            "/alumnos", json={"id": dni, "nombre": "Duplicado", "patronus": "Otter"}
        ).status_code
        == 409
    )
    servicio.maria.preparar()
    assert (
        cliente.delete(f"/alumnos/{dni}", headers={"If-Match": '"1"'}).status_code
        == 204
    )
    assert cliente.get(f"/alumnos/{dni}").status_code == 404
    central = next(a for a in servicio.maria.listar_central() if a.id == dni)
    assert not central.activo and central.version == 2


def test_seeder_csv_repetido_conserva_edicion_y_metadatos_reales(real):
    cliente, servicio = real
    root = Path(__file__).resolve().parents[2]
    spec = importlib.util.spec_from_file_location("seed_csv", root / "scripts/seed-origins.py")
    seed = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(seed)
    datos = seed.cargar_csv(root / "database/origins/data")
    fuente = servicio.fuentes["h2"]
    row = datos["h2"][0]
    original = fuente.obtener(row[0])
    cliente.post("/sync", json={"origenes": ["h2"], "ids": [original.id]})
    ruta = "/alumnos/" + original.id
    version = cliente.get(ruta).json()["version"]
    try:
        assert cliente.put(ruta, json={"version": version, "nombre": "Edición conservada"}).status_code == 200
        with fuente._conexion() as db:
            nuevos, existentes, retirados = seed.sembrar(db, "h2", datos["h2"])
            cursor = db.cursor()
            cursor.execute("SELECT NOMBRE,GENERO,WIKI FROM ALUMNOS WHERE ID=?", (original.id,))
            guardado = cursor.fetchone()
            cursor.close()
        assert nuevos == retirados == 0 and existentes == 212
        assert guardado[0] == "Edición conservada"
        assert guardado[1:] == (row[9], row[12])
    finally:
        vigente = cliente.get(ruta).json()
        if vigente["nombre"] != original.nombre:
            assert cliente.put(ruta, json={"version": vigente["version"], "nombre": original.nombre}).status_code == 200


def test_saturacion_con_fuentes_reales_rechaza_antes_de_escribir(real, monkeypatch):
    cliente, servicio = real
    servicio.capacidad = 5
    fuente = servicio.fuentes["h2"]
    original = fuente.listar()[0]
    cliente.post("/sync", json={"origenes": ["h2"], "ids": [original.id]})
    version = cliente.get(f"/alumnos/{original.id}").json()["version"]
    reintentar = servicio.reintentar
    creados = []
    monkeypatch.setattr(servicio, "reintentar", lambda **kwargs: {})
    try:
        for n in range(5):
            dni = str(60000000 + secrets.randbelow(10000000)) + "A"
            respuesta = cliente.post(
                "/alumnos",
                json={"id": dni, "nombre": "Perfil de saturación", "patronus": "Otter"},
            )
            assert respuesta.status_code == 201, respuesta.json()
            creados.append(dni)
            if n == 3:
                salud = cliente.get("/health").json()
                assert (
                    salud["status"] == "degraded" and salud["cola"]["ocupacion"] == 0.8
                )
        rechazado = str(70000000 + secrets.randbelow(10000000)) + "A"
        assert (
            cliente.post(
                "/alumnos",
                json={"id": rechazado, "nombre": "No escribir", "patronus": "Otter"},
            ).status_code
            == 503
        )
        assert servicio.maria.obtener_central(rechazado) is None
        assert (
            cliente.put(
                f"/alumnos/{original.id}",
                json={"version": version, "nombre": "No aplicar"},
            ).status_code
            == 503
        )
        assert fuente.obtener(original.id).nombre == original.nombre
    finally:
        monkeypatch.setattr(servicio, "reintentar", reintentar)
        reintentar(forzar=True)
        for dni in creados:
            assert (
                cliente.delete(
                    f"/alumnos/{dni}", headers={"If-Match": '"1"'}
                ).status_code
                == 204
            )
    assert cliente.get("/admin/coherence").json()["coinciden"]
