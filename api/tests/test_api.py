"""Aceptación del feedback y de las condiciones de fallo de la propuesta v6."""

import logging
import sqlite3
import time
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient
from proyecto_reto1.config import settings
from proyecto_reto1.db.base import Alumno, EscrituraIncierta
from proyecto_reto1.db.sqlite import CentralSQLite
from proyecto_reto1.main import FiltroDNI, crear_app
from proyecto_reto1.services.alumnos import ServicioAlumnos
from uvicorn.logging import AccessFormatter

ALTA = {"id": "20000000A", "nombre": "Hermione", "patronus": "Otter"}
RUTA = "/alumnos/20000000A"
HISTORICO = "/alumnos/10000002B"


def test_estado_r01_aliases_y_documentacion(entorno):
    cliente, *_ = entorno
    salud = cliente.get("/health", headers={"request-id": "prueba-r01"})
    assert salud.status_code == 200 and salud.json()["reto"] == "R01"
    assert salud.json()["status"] == "ok"
    assert salud.json()["cola"]["capacidad"] == 5000
    assert salud.headers["request-id"] == "prueba-r01"
    rutas = cliente.get("/openapi.json").json()["paths"]
    assert all(p in rutas for p in ("/sync", "/students", "/admin/coherence"))
    assert cliente.get("/docs").status_code == cliente.get("/redoc").status_code == 200


def test_flujo_cuatro_capas_y_reinicio_sin_bases_remotas(entorno):
    cliente, servicio, maria, fuente = entorno
    assert cliente.get("/alumnos").json() == []
    assert len(cliente.get("/connectors/h2/alumnos").json()) == 1
    sync = cliente.post("/sync", json={"origenes": ["h2"], "ids": ["10000002B"]})
    assert sync.status_code == 200 and sync.json()["fuentes"]["h2"]["importados"] == 1
    assert sync.json()["pendientes"] == 0
    assert maria.listar_central()[0].id == "10000002B"
    assert cliente.get("/admin/coherence").json()["coinciden"]
    fuente.disponible = maria.disponible = False
    reinicio = ServicioAlumnos(
        CentralSQLite(servicio.local.path), maria, {"h2": fuente}
    )
    with TestClient(crear_app(reinicio, reintentos=False)) as otro:
        respuesta = otro.get(HISTORICO)
        alumno = respuesta.json()
        assert alumno["origen"] == "h2" and alumno["nombre"] == "Cedric"
        assert (
            alumno["desde_cache"]
            and respuesta.headers["X-Data-Source"] == "sqlite-cache"
        )
        assert otro.get("/health").json()["status"] == "degraded"


@pytest.mark.parametrize(
    "cambios",
    [
        {"patronus": None},
        {"patronus": ""},
        {"patronus": "Inventado"},
        {"nombre": "   "},
        {"nombre": "A"},
        {"id": "texto"},
        {"sexo": "X"},
        {"casa": "Gryffindor"},
    ],
)
def test_altas_invalidas(entorno, cambios):
    cliente, *_ = entorno
    assert cliente.post("/alumnos", json={**ALTA, **cambios}).status_code == 422


def test_patronus_obligatorio_duplicados_y_normalizacion(entorno):
    cliente, *_ = entorno
    assert (
        cliente.post("/alumnos", json={"id": ALTA["id"], "nombre": "Harry"}).status_code
        == 422
    )
    respuesta = cliente.post(
        "/students", json={**ALTA, "id": " 20000000a ", "nombre": " Hermione "}
    )
    assert respuesta.status_code == 201
    assert (
        respuesta.json()["id"] == ALTA["id"]
        and respuesta.json()["nombre"] == "Hermione"
    )
    assert respuesta.json()["casa"] is None and not respuesta.json()["desde_cache"]
    assert cliente.post("/alumnos", json=ALTA).status_code == 409
    assert cliente.post("/alumnos", json={**ALTA, "id": "10000002B"}).status_code == 409


@pytest.mark.parametrize("ruta", ["/alumnos", "/students"])
def test_vista_sqlite_lee_copia_y_conserva_origen(entorno, ruta):
    cliente, _, maria, _ = entorno
    assert cliente.post("/alumnos", json=ALTA).status_code == 201
    cliente.post("/sync")
    # Una diferencia deliberada permite comprobar qué base se ha consultado.
    with maria.local.conexion(escritura=True) as db:
        db.execute("UPDATE ALUMNOS SET NOMBRE='Nombre remoto' WHERE ID=?", (ALTA["id"],))
    remoto = cliente.get(ruta).json()
    assert next(a for a in remoto if a["id"] == ALTA["id"])["nombre"] == "Nombre remoto"
    copia = cliente.get(ruta, params={"lectura": "sqlite"})
    assert copia.status_code == 200
    assert copia.headers["X-Data-Source"] == "sqlite-cache"
    assert {a["origen"] for a in copia.json()} == {"mariadb", "h2"}
    assert all(a["desde_cache"] for a in copia.json())
    assert next(a for a in copia.json() if a["id"] == ALTA["id"])["nombre"] == "Hermione"
    assert len(cliente.get(ruta, params={"lectura": "sqlite", "nombre": "Herm"}).json()) == 1
    assert cliente.get(ruta, params={"lectura": "desconocida"}).status_code == 422


def test_put_valida_y_exige_version(entorno):
    cliente, *_ = entorno
    cliente.post("/alumnos", json=ALTA)
    assert cliente.put(RUTA, json={"nombre": "Granger"}).status_code == 428
    for cambio in (
        {"patronus": "Inventado"},
        {"patronus": None},
        {"nombre": "   "},
        {},
    ):
        assert cliente.put(RUTA, json={"version": 1, **cambio}).status_code == 422
    assert (
        cliente.put(
            RUTA, headers={"If-Match": '"1"'}, json={"nombre": "Granger"}
        ).status_code
        == 200
    )
    assert (
        cliente.put(RUTA, json={"version": 1, "nombre": "Perdido"}).status_code == 409
    )
    assert cliente.get(RUTA).json()["nombre"] == "Granger"


def test_uuid_historico_se_conserva_y_se_consulta_en_mayusculas(entorno):
    cliente, servicio, _, fuente = entorno
    identificador = "f0b89f23-fb85-4147-a50b-629f4d26ad73"
    with fuente.local.conexion(escritura=True) as db:
        db.execute("UPDATE ALUMNOS SET ID=?", (identificador,))
    respuesta = cliente.post("/sync", json={"ids": [identificador.upper()]})
    assert respuesta.json()["fuentes"]["h2"]["importados"] == 1
    ruta = "/alumnos/" + identificador.upper()
    assert cliente.get(ruta).json()["id"] == identificador
    assert cliente.get(ruta + "/sync").status_code == 200
    assert cliente.put(ruta, json={"version": 1, "nombre": "Cambio UUID"}).status_code == 200
    assert fuente.obtener(identificador).nombre == "Cambio UUID"
    assert cliente.post("/alumnos", json={**ALTA, "id": identificador}).status_code == 422
    assert cliente.delete(ruta, headers={"If-Match": '"2"'}).status_code == 204
    assert cliente.get(ruta).status_code == 404


def test_dos_ediciones_concurrentes(entorno):
    cliente, *_ = entorno
    cliente.post("/alumnos", json=ALTA)
    with ThreadPoolExecutor(max_workers=2) as pool:
        respuestas = list(
            pool.map(
                lambda n: cliente.put(RUTA, json={"version": 1, "nombre": n}),
                ["Uno", "Dos"],
            )
        )
    assert sorted(r.status_code for r in respuestas) == [200, 409]


def test_baja_logica_nuevo_y_baja_historico(entorno):
    cliente, servicio, maria, fuente = entorno
    cliente.post("/alumnos", json=ALTA)
    assert cliente.delete(RUTA).status_code == 428
    assert cliente.delete(RUTA, headers={"If-Match": '"1"'}).status_code == 204
    assert cliente.get(RUTA).status_code == 404
    for local in (servicio.local, maria.local):
        with local.conexion() as db:
            alumno = local.obtener(db, ALTA["id"])
            assert alumno is not None and not alumno.activo and alumno.version == 2
    assert cliente.post("/alumnos", json=ALTA).status_code == 409
    cliente.post("/sync")
    assert cliente.delete(HISTORICO, headers={"If-Match": '"1"'}).status_code == 204
    assert fuente.obtener("10000002B") is None
    assert cliente.get(HISTORICO).status_code == 404
    assert cliente.get("/admin/coherence").json()["coinciden"]


def test_mariadb_caida_rechaza_altas_y_cambios_de_nuevos(entorno):
    cliente, servicio, maria, _ = entorno
    cliente.post("/alumnos", json=ALTA)
    maria.disponible = False
    assert cliente.get(RUTA).json()["desde_cache"]
    assert cliente.post("/alumnos", json={**ALTA, "id": "20000001B"}).status_code == 503
    assert (
        cliente.put(RUTA, json={"version": 1, "nombre": "No aplicar"}).status_code
        == 503
    )
    assert cliente.delete(RUTA, headers={"If-Match": '"1"'}).status_code == 503
    assert servicio.local.cola() == []
    maria.disponible = True
    cliente.post("/admin/retry")
    assert maria.obtener_central("20000001B") is None
    assert maria.obtener_central(ALTA["id"]).nombre == "Hermione"


def test_fuente_caida_rechaza_sin_escritura_diferida(entorno):
    cliente, servicio, _, fuente = entorno
    cliente.post("/sync")
    fuente.disponible = False
    assert (
        cliente.put(
            HISTORICO, json={"version": 1, "nombre": "Nunca aplicar"}
        ).status_code
        == 503
    )
    assert cliente.delete(HISTORICO, headers={"If-Match": '"1"'}).status_code == 503
    assert servicio.local.cola() == []
    assert cliente.get(HISTORICO).json()["nombre"] == "Cedric"
    fuente.disponible = True
    cliente.post("/admin/retry")
    assert fuente.obtener("10000002B").nombre == "Cedric"


def test_origen_caido_no_bloquea_un_alta_en_mariadb(entorno):
    cliente, _, _, fuente = entorno
    fuente.disponible = False
    assert cliente.post("/alumnos", json=ALTA).status_code == 201


def test_historico_edita_fuente_y_copias_y_detecta_cambio_externo(entorno):
    cliente, _, maria, fuente = entorno
    cliente.post("/sync")
    respuesta = cliente.put(
        HISTORICO, json={"version": 1, "nombre": "Cedric nuevo", "patronus": "Wolf"}
    )
    assert respuesta.status_code == 200
    assert (
        fuente.obtener("10000002B").nombre
        == maria.obtener_central("10000002B").nombre
        == "Cedric nuevo"
    )
    assert respuesta.json()["sincronizacion"] == "SYNCED"
    with fuente._conexion() as db:
        db.execute("UPDATE ALUMNOS SET NOMBRE='Cambio externo'")
    assert (
        cliente.put(
            HISTORICO, json={"version": 2, "nombre": "No sobrescribir"}
        ).status_code
        == 409
    )
    assert fuente.obtener("10000002B").nombre == "Cambio externo"
    assert cliente.get("/admin/refresh-queue").json() == []
    assert cliente.post("/sync").status_code == 200
    assert cliente.get(HISTORICO).json()["nombre"] == "Cambio externo"


def test_respuesta_releida_incluye_cambios_de_un_trigger(entorno):
    cliente, _, _, fuente = entorno
    cliente.post("/sync")
    with fuente._conexion() as db:
        db.execute("""CREATE TRIGGER normaliza AFTER UPDATE OF NOMBRE ON ALUMNOS
                      BEGIN UPDATE ALUMNOS SET NOMBRE=UPPER(NEW.NOMBRE) WHERE ID=NEW.ID; END""")
    respuesta = cliente.put(HISTORICO, json={"version": 1, "nombre": "Cambio real"})
    assert respuesta.status_code == 200
    assert respuesta.json()["nombre"] == "CAMBIO REAL"
    assert cliente.get(HISTORICO).json()["nombre"] == "CAMBIO REAL"


def test_fallo_refresco_cinco_intentos_failed_y_relanzamiento(entorno):
    cliente, servicio, maria, fuente = entorno
    cliente.post("/sync")
    maria.disponible = False
    cambio = cliente.put(HISTORICO, json={"version": 1, "nombre": "Cambio confirmado"})
    assert cambio.status_code == 200 and cambio.json()["sincronizacion"] == "PENDING"
    assert fuente.obtener("10000002B").nombre == "Cambio confirmado"
    for intento in range(2, 6):
        with servicio.local.conexion(escritura=True) as db:
            db.execute("UPDATE OUTBOX SET NEXT_ATTEMPT=0")
        servicio.reintentar()
        tarea = cliente.get("/admin/refresh-queue").json()[0]
        assert tarea["ATTEMPTS"] == intento
    assert tarea["STATUS"] == "FAILED"
    servicio.reintentar(forzar=True)
    assert cliente.get("/admin/refresh-queue").json()[0]["ATTEMPTS"] == 5
    assert cliente.get(HISTORICO + "/sync").json()["sincronizacion"] == "FAILED"
    maria.disponible = True
    assert (
        cliente.post(f"/admin/refresh-queue/{tarea['SEQ']}/retry").json()["pendientes"]
        == 0
    )
    assert cliente.get("/admin/coherence").json()["coinciden"]


def test_reintento_respeta_la_espera_progresiva(entorno):
    cliente, servicio, maria, _ = entorno
    cliente.post("/sync")
    maria.disponible = False
    cliente.put(HISTORICO, json={"version": 1, "nombre": "Confirmado"})
    tarea = servicio.local.cola()[0]
    assert tarea["NEXT_ATTEMPT"] > time.time()
    servicio.reintentar()
    assert servicio.local.cola()[0]["ATTEMPTS"] == 1


def test_cola_persistente_solo_propaga_cambios_ya_confirmados(entorno):
    cliente, servicio, maria, fuente = entorno
    cliente.post("/sync")
    maria.disponible = False
    assert (
        cliente.put(
            HISTORICO, json={"version": 1, "nombre": "Confirmado antes de reiniciar"}
        ).status_code
        == 200
    )
    reinicio = ServicioAlumnos(
        CentralSQLite(servicio.local.path), maria, {"h2": fuente}
    )
    maria.disponible = True
    with TestClient(crear_app(reinicio, reintentos=False)) as otro:
        assert otro.post("/admin/retry").json()["pendientes"] == 0
        assert otro.get("/admin/coherence").json()["coinciden"]
    assert fuente.obtener("10000002B").nombre == "Confirmado antes de reiniciar"


def test_reserva_sin_escritura_se_cancela_sin_ejecutarla(entorno, monkeypatch):
    _, servicio, maria, _ = entorno
    nuevo = Alumno(**ALTA, tipo="NUEVO", origen="mariadb")
    servicio._reservar("mariadb", nuevo, None, "CREATE")
    with servicio.local.conexion(escritura=True) as db:
        db.execute("UPDATE OUTBOX SET CREATED_AT=0")
    monkeypatch.setattr(
        maria,
        "mutar",
        lambda *args: pytest.fail("El worker ha intentado escribir en la principal"),
    )
    assert servicio.reintentar()["cancelados"] == 1
    assert maria.obtener_central(ALTA["id"]) is None and servicio.local.cola() == []


def test_commit_incierto_se_resuelve_por_lectura_sin_repetir_insert(
    entorno, monkeypatch
):
    cliente, servicio, maria, _ = entorno
    mutar = maria.mutar

    def perder_respuesta(previo, nuevo):
        mutar(previo, nuevo)
        raise EscrituraIncierta("corte después del commit")

    monkeypatch.setattr(maria, "mutar", perder_respuesta)
    assert cliente.post("/alumnos", json=ALTA).status_code == 503
    assert maria.obtener_central(ALTA["id"]) is not None
    with servicio.local.conexion(escritura=True) as db:
        db.execute("UPDATE OUTBOX SET CREATED_AT=0,NEXT_ATTEMPT=0")
    monkeypatch.setattr(
        maria, "mutar", lambda *args: pytest.fail("No repetir el INSERT")
    )
    assert servicio.reintentar()["pendientes"] == 0
    assert cliente.get("/admin/coherence").json()["coinciden"]


def test_fallo_confirmando_reserva_tras_commit_es_recuperable(entorno, monkeypatch):
    cliente, servicio, maria, _ = entorno
    confirmar = servicio.local.confirmar

    def fallo(*args):
        raise sqlite3.OperationalError("corte local")

    monkeypatch.setattr(servicio.local, "confirmar", fallo)
    assert cliente.post("/alumnos", json=ALTA).status_code == 503
    assert maria.obtener_central(ALTA["id"]) is not None
    monkeypatch.setattr(servicio.local, "confirmar", confirmar)
    with servicio.local.conexion(escritura=True) as db:
        db.execute("UPDATE OUTBOX SET CREATED_AT=0,NEXT_ATTEMPT=0")
    assert servicio.reintentar()["pendientes"] == 0


def test_averia_del_worker_no_oculta_una_escritura_ya_confirmada(entorno, monkeypatch):
    cliente, servicio, maria, _ = entorno
    reintentar = servicio.reintentar

    def fallo(*args, **kwargs):
        raise sqlite3.OperationalError("avería de la réplica después del commit")

    monkeypatch.setattr(servicio, "reintentar", fallo)
    respuesta = cliente.post("/alumnos", json=ALTA)
    assert (
        respuesta.status_code == 201 and respuesta.json()["sincronizacion"] == "PENDING"
    )
    assert maria.obtener_central(ALTA["id"]) is not None
    monkeypatch.setattr(servicio, "reintentar", reintentar)
    assert cliente.post("/admin/retry").json()["pendientes"] == 0
    assert cliente.get("/admin/coherence").json()["coinciden"]


def test_saturacion_avisa_al_80_y_rechaza_antes_de_tocar_fuentes(entorno, monkeypatch):
    cliente, servicio, maria, fuente = entorno
    cliente.post("/sync")
    servicio.capacidad = 5
    monkeypatch.setattr(servicio, "reintentar", lambda **kwargs: {})
    for n in range(5):
        if n == 3:
            assert cliente.get("/health").json()["status"] == "ok"
        assert (
            cliente.post("/alumnos", json={**ALTA, "id": f"2000000{n}A"}).status_code
            == 201
        )
        if n == 3:
            salud = cliente.get("/health").json()
            assert salud["status"] == "degraded" and salud["cola"]["ocupacion"] == 0.8
    assert cliente.post("/alumnos", json={**ALTA, "id": "20000009A"}).status_code == 503
    assert maria.obtener_central("20000009A") is None
    assert (
        cliente.put(HISTORICO, json={"version": 1, "nombre": "No cambiar"}).status_code
        == 503
    )
    assert cliente.delete(HISTORICO, headers={"If-Match": '"1"'}).status_code == 503
    assert fuente.obtener("10000002B").nombre == "Cedric"


def test_fallo_persistiendo_reserva_devuelve_503_sin_alta(entorno, monkeypatch):
    cliente, servicio, maria, _ = entorno

    def fallo(*args, **kwargs):
        raise sqlite3.OperationalError("sin espacio")

    monkeypatch.setattr(servicio.local, "reservar", fallo)
    respuesta = cliente.post(
        "/alumnos", json=ALTA, headers={"request-id": "fallo-atomico"}
    )
    assert (
        respuesta.status_code == 503
        and respuesta.headers["request-id"] == "fallo-atomico"
    )
    assert maria.obtener_central(ALTA["id"]) is None and servicio.local.cola() == []


def test_mariadb_es_la_principal_y_coherencia_detecta_divergencia(entorno):
    cliente, servicio, _, _ = entorno
    cliente.post("/alumnos", json=ALTA)
    with servicio.local.conexion(escritura=True) as db:
        db.execute("UPDATE ALUMNOS SET NOMBRE='Copia alterada'")
    assert cliente.get(RUTA).json()["nombre"] == "Hermione"
    informe = cliente.get("/admin/coherence").json()
    assert informe["completo"] and not informe["coinciden"]
    assert (
        informe["centrales"]["mariadb"]["registros"]
        == informe["centrales"]["sqlite"]["registros"]
        == 1
    )
    assert (
        informe["centrales"]["mariadb"]["huella"]
        != informe["centrales"]["sqlite"]["huella"]
    )
    assert informe["divergencias"]["distintos"] == [ALTA["id"]]


def test_coherencia_incompleta_no_afirma_igualdad(entorno):
    cliente, _, maria, _ = entorno
    maria.disponible = False
    respuesta = cliente.get("/admin/coherence")
    assert respuesta.status_code == 503
    assert not respuesta.json()["completo"] and not respuesta.json()["coinciden"]


def test_estado_de_sincronizacion_por_registro(entorno):
    cliente, *_ = entorno
    cliente.post("/alumnos", json=ALTA)
    estado = cliente.get(RUTA + "/sync").json()
    assert estado["sincronizacion"] == "SYNCED"
    assert estado["sincronizado_en"].endswith("+00:00")


def test_baja_externa_se_refleja_por_sincronizacion_selectiva(entorno):
    cliente, _, _, fuente = entorno
    cliente.post("/sync")
    with fuente._conexion() as db:
        db.execute("DELETE FROM ALUMNOS")
    assert (
        cliente.put(HISTORICO, json={"version": 1, "nombre": "No existe"}).status_code
        == 409
    )
    respuesta = cliente.post("/sync", json={"ids": ["10000002B"]})
    assert respuesta.json()["fuentes"]["h2"]["bajas_detectadas"] == 1
    assert cliente.get(HISTORICO).status_code == 404


def test_importacion_lenta_no_pisa_edicion_concurrente(entorno, monkeypatch):
    cliente, servicio, _, fuente = entorno
    cliente.post("/sync")
    original = fuente.listar

    def leer():
        snapshot = original()
        servicio.modificar("10000002B", {"nombre": "Edición simultánea"}, 1)
        return snapshot

    monkeypatch.setattr(fuente, "listar", leer)
    assert cliente.post("/sync").json()["fuentes"]["h2"]["conflictos"] == 1
    assert cliente.get(HISTORICO).json()["nombre"] == "Edición simultánea"


def test_worker_reintenta_automaticamente_un_refresco(entorno, monkeypatch):
    _, servicio, maria, _ = entorno
    monkeypatch.setattr(settings, "retry_interval_seconds", 1)
    with TestClient(crear_app(servicio)) as cliente:
        maria.disponible = False
        cliente.post("/sync")
        assert servicio.local.cola()
        maria.disponible = True
        limite = time.monotonic() + 5
        while servicio.local.cola() and time.monotonic() < limite:
            time.sleep(0.1)
        assert servicio.local.cola() == []
        assert maria.obtener_central("10000002B") is not None


def test_migracion_conserva_ordenes_anteriores_sin_ejecutarlas(entorno):
    _, servicio, maria, _ = entorno
    nuevo = Alumno(**ALTA)
    with servicio.local.conexion(escritura=True) as db:
        db.execute(
            "INSERT INTO OUTBOX(TARGET,ALUMNO_ID,PAYLOAD,STATUS) VALUES(?,?,?,'pendiente')",
            ("mariadb", nuevo.id, nuevo.model_dump_json()),
        )
    migrada = CentralSQLite(servicio.local.path)
    assert migrada.cola()[0]["STATUS"] == "FAILED"
    assert migrada.cola()[0]["LAST_ERROR"] == "LEGACY_REVIEW"
    ServicioAlumnos(migrada, maria, {}).reintentar(forzar=True)
    assert maria.obtener_central(ALTA["id"]) is None


def test_logs_no_exponen_dni_y_conservan_formato_uvicorn():
    acceso = logging.LogRecord(
        "uvicorn.access",
        logging.INFO,
        "",
        0,
        '%s - "%s %s HTTP/%s" %d',
        ("127.0.0.1:1234", "GET", RUTA, "1.1", 200),
        None,
    )
    FiltroDNI().filter(acceso)
    texto = AccessFormatter(
        "%(request_line)s %(status_code)s", use_colors=False
    ).format(acceso)
    assert ALTA["id"] not in texto and "20***A" in texto
