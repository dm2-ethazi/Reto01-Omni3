"""Tests de integración de los requisitos del Reto R01.

Cubre los seis puntos corregidos en la revisión:

1. Baja lógica: el registro se conserva marcado como inactivo.
2. Respaldo central en SQLite.
3. Sincronización con reintentos (`pending_sync`).
4. Control de concurrencia optimista (409 con versión actual).
5. Patronus obligatorio en altas.
6. `GET /status` con la referencia «R01».

Requiere la MariaDB de Docker Compose (servicio `mariadb`, contenedor
`r01-mariadb`).
"""

import sqlite3
import time

import pymysql

from .conftest import DOCKER_OK

DNIS_PRUEBA: list[str] = []

# Credenciales locales de la demo (ver api/.env.local.example).
_CREDENCIALES = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "example",
    "password": "example",
    "database": "r01_central",
    "charset": "utf8mb4",
    "connect_timeout": 5,
}


def _conexion_mariadb():
    """Abre una conexión directa a MariaDB para verificar la persistencia."""
    return pymysql.connect(**_CREDENCIALES)


def _crear_alumno(cliente, dni: str, nombre: str = "Hermione", patronus: str = "Otter"):
    """Helper que limpia restos de ejecuciones anteriores, crea el alumno y lo registra.

    Si MariaDB está caído (pruebas de degradación), la limpieza previa se salta.
    """
    try:
        with _conexion_mariadb() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("DELETE FROM ALUMNOS WHERE ID = %s", (dni,))
                conexion.commit()
    except Exception:
        pass  # MariaDB caído en pruebas de respaldo/colado: limpiar solo SQLite.

    import sqlite3

    from proyecto_reto1.db.factory import obtener_sincronizador

    ruta_sqlite = obtener_sincronizador().respaldo.ruta
    conexion_sqlite = sqlite3.connect(ruta_sqlite)
    with conexion_sqlite:
        conexion_sqlite.execute("DELETE FROM alumnos WHERE id = ?", (dni,))
    conexion_sqlite.close()

    respuesta = cliente.post(
        "/alumnos",
        json={
            "id": dni,
            "nombre": nombre,
            "apellido": "Granger",
            "sexo": "M",
            "patronus": patronus,
        },
    )
    if respuesta.status_code == 201:
        DNIS_PRUEBA.append(dni)
    return respuesta


def _version_de(cliente, dni: str) -> int:
    """Devuelve la versión actual de un alumno leyéndolo por GET."""
    return cliente.get(f"/alumnos/{dni}").json()["version"]


def _limpiar():
    """Elimina de MariaDB y del respaldo SQLite todos los DNIS de las pruebas.

    Como en algunas pruebas MariaDB acaba de arrancar, el borrado reintenta
    unos segundos antes de rendirse.
    """
    from proyecto_reto1.db.factory import obtener_sincronizador

    sincronizador = obtener_sincronizador()
    if DNIS_PRUEBA:
        deadline = time.time() + 60
        while True:
            try:
                with _conexion_mariadb() as conexion:
                    with conexion.cursor() as cursor:
                        for dni in DNIS_PRUEBA:
                            cursor.execute("DELETE FROM ALUMNOS WHERE ID = %s", (dni,))
                        conexion.commit()
                break
            except Exception:
                if time.time() > deadline:
                    raise
                time.sleep(2)  # MariaDB aún arrancando; se reintenta.
        # pending_sync vive en SQLite (respaldo), no en MariaDB.
        conexion_sqlite = sqlite3.connect(sincronizador.respaldo.ruta)
        with conexion_sqlite:
            for dni in DNIS_PRUEBA:
                conexion_sqlite.execute("DELETE FROM alumnos WHERE id = ?", (dni,))
            conexion_sqlite.execute("DELETE FROM pending_sync")
        conexion_sqlite.close()
    DNIS_PRUEBA.clear()


# --------------------------------------------------------------------------- #
#  6. GET /status con «R01»                                                   #
# --------------------------------------------------------------------------- #


def test_status_devuelve_reto_r01(cliente):
    """GET /status informa del reto, bases de datos, pendientes y fecha."""
    respuesta = cliente.get("/status")
    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["reto"] == "R01"
    assert cuerpo["mariadb"] in ("up", "down")
    assert cuerpo["sqlite"] in ("up", "down")
    assert isinstance(cuerpo["operaciones_pendientes"], int)
    assert isinstance(cuerpo["operaciones_fallidas"], int)
    assert cuerpo["fecha_hora"]
    if DOCKER_OK:
        assert cuerpo["mariadb"] == "up"
        assert cuerpo["sqlite"] == "up"
        assert cuerpo["estado"] == "ok"


# --------------------------------------------------------------------------- #
#  5. Patronus obligatorio en altas                                           #
# --------------------------------------------------------------------------- #


def test_alta_rechazada_sin_patronus(cliente):
    """Un alta sin patronus se rechaza con 422 y mensaje claro."""
    respuesta = cliente.post(
        "/alumnos", json={"id": "T9000001A", "nombre": "Sin", "patronus": None}
    )
    assert respuesta.status_code == 422
    assert "patronus" in respuesta.text.lower()


def test_alta_rechazada_con_patronus_vacio(cliente):
    """Un patronus en blanco también se rechaza (422)."""
    respuesta = cliente.post(
        "/alumnos", json={"id": "T9000002B", "nombre": "Vacio", "patronus": "   "}
    )
    assert respuesta.status_code == 422


def test_alta_correcta_con_patronus(cliente):
    """Un alta válida devuelve 201, activo=true y version=1."""
    respuesta = _crear_alumno(cliente, "T9000003C", "Harry", "Stag")
    assert respuesta.status_code == 201
    cuerpo = respuesta.json()
    assert cuerpo["activo"] is True
    assert cuerpo["version"] == 1
    assert cuerpo["patronus"] == "Stag"
    _limpiar()


# --------------------------------------------------------------------------- #
#  1. Baja lógica conserva el registro                                        #
# --------------------------------------------------------------------------- #


def test_baja_logica_conserva_el_registro(cliente):
    """DELETE marca activo=false sin borrar la fila (verificado en SQL)."""
    assert _crear_alumno(cliente, "T9000010A").status_code == 201
    version = _version_de(cliente, "T9000010A")

    respuesta = cliente.delete("/alumnos/T9000010A", headers={"If-Match": str(version)})
    assert respuesta.status_code == 204

    # El registro sigue en la tabla, marcado como inactivo con fecha de baja.
    with _conexion_mariadb() as conexion:
        with conexion.cursor() as cursor:
            cursor.execute(
                "SELECT ACTIVO, FECHA_BAJA, VERSION FROM ALUMNOS WHERE ID = %s",
                ("T9000010A",),
            )
            activo, fecha_baja, version_bd = cursor.fetchone()
    assert activo == 0
    assert fecha_baja is not None
    assert version_bd == version + 1

    # Por defecto no aparece; con incluir_inactivos sí.
    assert cliente.get("/alumnos/T9000010A").status_code == 404
    incluidos = cliente.get("/alumnos/T9000010A?incluir_inactivos=true")
    assert incluidos.status_code == 200
    assert incluidos.json()["activo"] is False
    assert incluidos.json()["fecha_baja"] is not None

    listado = cliente.get("/alumnos").json()
    assert all(a["id"] != "T9000010A" for a in listado)
    con_inactivos = cliente.get("/alumnos?incluir_inactivos=true").json()
    assert any(a["id"] == "T9000010A" and a["activo"] is False for a in con_inactivos)
    _limpiar()


def test_baja_logica_es_idempotente(cliente):
    """Repetir la baja sobre un alumno ya inactivo devuelve 204 sin tocar nada."""
    assert _crear_alumno(cliente, "T9000011B").status_code == 201
    version = _version_de(cliente, "T9000011B")
    assert cliente.delete("/alumnos/T9000011B", headers={"If-Match": str(version)}).status_code == 204

    # Misma versión otra vez: responde 204 aunque ya esté inactivo.
    assert cliente.delete("/alumnos/T9000011B", headers={"If-Match": str(version)}).status_code == 204
    _limpiar()


def test_baja_inexistente_devuelve_404(cliente):
    """Dar de baja un DNI inexistente responde 404."""
    respuesta = cliente.delete("/alumnos/T9999999Z", headers={"If-Match": "1"})
    assert respuesta.status_code == 404


# --------------------------------------------------------------------------- #
#  4. Control de concurrencia optimista                                       #
# --------------------------------------------------------------------------- #


def test_edicion_sin_version_devuelve_428(cliente):
    """PUT/DELETE sin versión (If-Match ni body) responden 428 Precondition Required."""
    assert _crear_alumno(cliente, "T9000020A").status_code == 201
    assert cliente.put("/alumnos/T9000020A", json={"nombre": "SinVersion"}).status_code == 428
    assert cliente.delete("/alumnos/T9000020A").status_code == 428
    _limpiar()


def test_edicion_con_version_desactualizada_devuelve_409(cliente):
    """Si la versión no coincide, responde 409 con la versión actual."""
    assert _crear_alumno(cliente, "T9000021B").status_code == 201
    version_real = _version_de(cliente, "T9000021B")

    respuesta = cliente.put(
        "/alumnos/T9000021B",
        json={"nombre": "Cambiado", "version": version_real + 5},
    )
    assert respuesta.status_code == 409
    detalle = respuesta.json()["detail"]
    assert detalle["version_actual"] == version_real

    # Con la versión correcta sí se aplica y la versión sube.
    ok = cliente.put(
        "/alumnos/T9000021B",
        json={"nombre": "Cambiado", "version": version_real},
    )
    assert ok.status_code == 200
    assert ok.json()["nombre"] == "Cambiado"
    assert ok.json()["version"] == version_real + 1
    _limpiar()


def test_edicion_con_if_match(cliente):
    """La versión también puede enviarse en la cabecera If-Match."""
    assert _crear_alumno(cliente, "T9000022C").status_code == 201
    version = _version_de(cliente, "T9000022C")

    ok = cliente.put(
        "/alumnos/T9000022C",
        json={"apellido": "Potter"},
        headers={"If-Match": f'"{version}"'},
    )
    assert ok.status_code == 200
    assert ok.json()["apellido"] == "Potter"

    conflicto = cliente.put(
        "/alumnos/T9000022C",
        json={"apellido": "Otro"},
        headers={"If-Match": str(version)},  # versión ya superada
    )
    assert conflicto.status_code == 409
    _limpiar()


def test_edicion_de_baja_logica_tambien_controla_version(cliente):
    """La baja lógica también aplica el control de versión (409 si no coincide)."""
    assert _crear_alumno(cliente, "T9000023D").status_code == 201
    version = _version_de(cliente, "T9000023D")
    respuesta = cliente.delete("/alumnos/T9000023D", headers={"If-Match": str(version + 9)})
    assert respuesta.status_code == 409
    assert respuesta.json()["detail"]["version_actual"] == version
    _limpiar()


# --------------------------------------------------------------------------- #
#  2. Lecturas desde SQLite con MariaDB caída                                 #
# --------------------------------------------------------------------------- #


def test_lectura_desde_respaldo_con_mariadb_caido(cliente, mariadb_pausada):
    """Con MariaDB pausado, las lecturas se sirven desde SQLite."""
    assert _crear_alumno(cliente, "T9000030A", "Respaldo").status_code == 201
    version = _version_de(cliente, "T9000030A")
    assert cliente.delete("/alumnos/T9000030A", headers={"If-Match": str(version)}).status_code == 204
    mariadb_pausada  # MariaDB sigue pausado por la fixture

    listado = cliente.get("/alumnos?incluir_inactivos=true")
    assert listado.status_code == 200
    assert listado.headers.get("X-Data-Source") == "sqlite"
    assert any(a["id"] == "T9000030A" for a in listado.json())
    # Por defecto (solo activos) tampoco aparece, igual que con MariaDB arriba.
    activos = cliente.get("/alumnos")
    assert activos.headers.get("X-Data-Source") == "sqlite"
    assert all(a["id"] != "T9000030A" for a in activos.json())

    detalle = cliente.get("/alumnos/T9000030A?incluir_inactivos=true")
    assert detalle.status_code == 200
    assert detalle.headers.get("X-Data-Source") == "sqlite"
    assert detalle.json()["activo"] is False  # la baja lógica también se respaldó

    estado = cliente.get("/status").json()
    assert estado["mariadb"] == "down"
    assert estado["sqlite"] == "up"
    assert estado["estado"] == "degradado"

    # Se restaura MariaDB antes de limpiar (la limpieza toca la base central).
    from .conftest import despausar_mariadb

    despausar_mariadb()
    _limpiar()


# --------------------------------------------------------------------------- #
#  3. Sincronización con reintentos                                           #
# --------------------------------------------------------------------------- #


def test_escritura_con_mariadb_caido_se_encola_y_se_sincroniza(cliente, mariadb_pausada):
    """Con MariaDB caído el alta se encola y, al recuperarse, se aplica."""
    # 1) Alta con MariaDB caído: se guarda en SQLite y se encola.
    respuesta = _crear_alumno(cliente, "T9000040A", "Cola")
    assert respuesta.status_code == 201
    assert respuesta.headers.get("X-Pending-Sync") == "true"

    estado = cliente.get("/status").json()
    assert estado["operaciones_pendientes"] >= 1

    # 2) Recuperar MariaDB (la fixture la despausa al terminar el test... usamos
    #    la pausa manual para controlar el momento exacto).
    from .conftest import despausar_mariadb

    despausar_mariadb()

    # 3) Esperar a que el worker reintente y aplique la operación (MariaDB
    #    acaba de arrancar, así que se toleran errores transitorios).
    deadline = time.time() + 90
    aplicado = False
    while time.time() < deadline:
        try:
            with _conexion_mariadb() as conexion:
                with conexion.cursor() as cursor:
                    cursor.execute("SELECT ACTIVO FROM ALUMNOS WHERE ID = %s", ("T9000040A",))
                    fila = cursor.fetchone()
            if fila is not None and fila[0] == 1:
                aplicado = True
                break
        except Exception:
            pass  # MariaDB aún arrancando; se reintenta en la siguiente vuelta.
        time.sleep(2)
    assert aplicado, "La operación encolada no se aplicó en MariaDB tras la recuperación"

    # 4) La cola queda limpia y /status no muestra pendientes.
    deadline = time.time() + 30
    while time.time() < deadline:
        estado = cliente.get("/status").json()
        if estado["operaciones_pendientes"] == 0:
            break
        time.sleep(1)
    assert estado["operaciones_pendientes"] == 0
    _limpiar()


def test_baja_con_mariadb_caido_se_encola_y_se_sincroniza(cliente, mariadb_pausada):
    """Con MariaDB caído, la baja lógica se encola y se aplica al recuperarse."""
    assert _crear_alumno(cliente, "T9000041B", "BajaCola").status_code == 201
    version = _version_de(cliente, "T9000041B")

    respuesta = cliente.delete("/alumnos/T9000041B", headers={"If-Match": str(version)})
    assert respuesta.status_code == 204
    assert respuesta.headers.get("X-Pending-Sync") == "true"

    from .conftest import despausar_mariadb

    despausar_mariadb()

    deadline = time.time() + 90
    aplicado = False
    while time.time() < deadline:
        try:
            with _conexion_mariadb() as conexion:
                with conexion.cursor() as cursor:
                    cursor.execute("SELECT ACTIVO FROM ALUMNOS WHERE ID = %s", ("T9000041B",))
                    fila = cursor.fetchone()
            if fila is not None and fila[0] == 0:
                aplicado = True
                break
        except Exception:
            pass  # MariaDB aún arrancando; se reintenta en la siguiente vuelta.
        time.sleep(2)
    assert aplicado, "La baja encolada no se aplicó en MariaDB tras la recuperación"
    _limpiar()
