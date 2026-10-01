"""Pruebas de la API con orígenes falsos (sin JVM ni Docker)."""

from __future__ import annotations


def test_raiz_y_salud(client):
    raiz = client.get("/")
    assert raiz.status_code == 200
    assert raiz.json()["docs"] == "/docs"

    salud = client.get("/health")
    assert salud.status_code == 200
    cuerpo = salud.json()
    assert cuerpo["status"] == "ok"
    assert cuerpo["central"]["sqlite"]["disponible"] is True
    assert set(cuerpo["conectores"]) == {"derby", "h2"}
    assert cuerpo["jvm"]["arrancada"] is False  # registro falso inyectado


def test_listado_y_estado_de_conectores(client):
    cuerpo = client.get("/connectors").json()["conectores"]
    assert sorted(cuerpo) == ["derby", "h2"]
    assert all(cuerpo[conector]["disponible"] for conector in cuerpo)


def test_listado_paginado_y_filtros(client):
    pagina = client.get("/connectors/derby/estudiantes", params={"limit": 2})
    assert pagina.status_code == 200
    datos = pagina.json()
    assert datos["total"] == 3
    assert len(datos["items"]) == 2
    assert datos["conector"] == "derby"

    por_dni = client.get("/connectors/derby/estudiantes", params={"dni": "23456789D"})
    assert [fila["dni"] for fila in por_dni.json()["items"]] == ["23456789D"]

    por_nombre = client.get("/connectors/derby/estudiantes", params={"nombre": "alicia"})
    assert por_nombre.json()["total"] == 1

    offset = client.get("/connectors/derby/estudiantes", params={"offset": 10, "limit": 5})
    assert offset.json()["items"] == []


def test_conector_desconocido_devuelve_404(client):
    respuesta = client.get("/connectors/postgres/estudiantes")
    assert respuesta.status_code == 404
    error = respuesta.json()["error"]
    assert error["codigo"] == "C-404"
    assert "postgres" in error["mensaje"]


def test_limit_fuera_de_rango_devuelve_422(client):
    respuesta = client.get("/connectors/derby/estudiantes", params={"limit": 0})
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["codigo"] == "C-422"


def test_crud_sobre_un_origen(client):
    alta = client.post(
        "/connectors/h2/estudiantes",
        json={"dni": "99887766x", "nombre": "  Eva   Prueba ", "casa": "Hufflepuff", "patronus": "Tejon"},
    )
    assert alta.status_code == 201
    creado = alta.json()
    assert creado["dni"] == "99887766X"  # normalizado en mayúsculas
    assert creado["nombre"] == "Eva Prueba"  # espacios colapsados

    detalle = client.get(f"/connectors/h2/estudiantes/{creado['id']}")
    assert detalle.status_code == 200
    assert detalle.json()["patronus"] == "Tejon"

    modificado = client.put(
        f"/connectors/h2/estudiantes/{creado['id']}", json={"nombre": "Eva Prueba Actualizada"}
    )
    assert modificado.status_code == 200
    assert modificado.json()["nombre"] == "Eva Prueba Actualizada"

    borrado = client.delete(f"/connectors/h2/estudiantes/{creado['id']}")
    assert borrado.status_code == 204
    assert client.get(f"/connectors/h2/estudiantes/{creado['id']}").status_code == 404

    assert client.delete("/connectors/h2/estudiantes/999").status_code == 404


def test_alta_con_dni_invalido_devuelve_400(client):
    respuesta = client.post(
        "/connectors/derby/estudiantes",
        json={"dni": "no-es-dni", "nombre": "Ana", "casa": "Ravenclaw", "patronus": "Búho"},
    )
    assert respuesta.status_code == 400
    assert respuesta.json()["error"]["codigo"] == "C-400"


def test_alta_con_cuerpo_incompleto_devuelve_422(client):
    respuesta = client.post("/connectors/derby/estudiantes", json={})
    assert respuesta.status_code == 422
    assert respuesta.json()["error"]["codigo"] == "C-422"


def test_sincronizacion_es_idempotente(client):
    primera = client.post("/sync", params={}).json()
    assert primera["insertados"] == 5  # 3 de derby + 2 de h2
    assert primera["actualizados"] == 0
    assert primera["errores"] == 0

    segunda = client.post("/sync", params={}).json()
    assert segunda["insertados"] == 0
    assert segunda["actualizados"] == 5
    assert segunda["errores"] == 0


def test_consulta_a_la_central(client):
    client.post("/sync", params={})

    central = client.get("/central/estudiantes", params={"limit": 2})
    assert central.status_code == 200
    datos = central.json()
    assert datos["total"] == 5
    assert len(datos["items"]) == 2
    assert datos["backend"] in {"sqlite", "mariadb"}

    por_dni = client.get("/central/estudiantes/12345678Z")
    assert por_dni.status_code == 200
    assert por_dni.json()["origen"] == "derby"

    assert client.get("/central/estudiantes/00000000A").status_code == 404

    filtrada = client.get("/central/estudiantes", params={"nombre": "bruno"})
    assert filtrada.json()["total"] == 1


def test_consistencia_de_la_central(client):
    client.post("/sync", params={})

    consistencia = client.get("/central/consistencia").json()
    assert consistencia["coincide"] is True
    assert consistencia["pendientes_reintento"] == 0
    assert consistencia["backends"]["sqlite"]["conteos"] == {"derby": 3, "h2": 2}


def test_sincronizar_conector_concreto(client):
    respuesta = client.post("/sync/derby")
    assert respuesta.status_code == 200
    assert respuesta.json()["conector"] == "derby"

    desconocido = client.post("/sync/postgres")
    assert desconocido.status_code == 400
    assert desconocido.json()["error"]["codigo"] == "C-400"
