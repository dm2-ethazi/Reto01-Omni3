"""Demostración HTTP y comprobación de ambas centrales.

Usar únicamente con datos de demo: crea un alumno y conserva su baja lógica.
Con --compose-project detiene y recupera MariaDB de ese proyecto para probar
el fallback y los reintentos automáticos. No borra contenedores ni volúmenes.
"""

import argparse
import json
import secrets
import subprocess
import time
from pathlib import Path

import httpx
from dotenv import load_dotenv


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--env-file", default="api/.env")
    parser.add_argument(
        "--compose-project",
        help="Activa la prueba de caída en el proyecto Docker indicado",
    )
    parser.add_argument(
        "--compose-env-file",
        default=".env",
        help="Credenciales del Compose, por defecto .env raíz",
    )
    parser.add_argument("--report", default="api/data/demo-adat.json")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    env_path = Path(args.env_file).resolve()
    load_dotenv(env_path, override=True)
    from proyecto_reto1.config import settings
    from proyecto_reto1.db.central_mariadb import CentralMariaDB
    from proyecto_reto1.db.sqlite import CentralSQLite

    maria = CentralMariaDB(
        settings.mariadb_host,
        settings.mariadb_port,
        settings.mariadb_database,
        settings.mariadb_user,
        settings.mariadb_password,
    )
    local = CentralSQLite(settings.sqlite_path)
    informe = {"reto": "R01", "comprobaciones": []}

    def comprobar(condicion, texto):
        if not condicion:
            raise AssertionError(texto)
        informe["comprobaciones"].append(texto)
        print("OK:", texto)

    def compose(accion, servicio="mariadb"):
        subprocess.run(
            [
                "docker",
                "compose",
                "--env-file",
                str(Path(args.compose_env_file).resolve()),
                "-p",
                args.compose_project,
                "-f",
                str(root / "compose.yaml"),
                accion,
                servicio,
            ],
            check=True,
            capture_output=True,
        )

    parados = set()
    with httpx.Client(
        base_url=args.base_url,
        timeout=45,
        headers={"request-id": "demo-adat-" + secrets.token_hex(6)},
    ) as api:
        estado = api.get("/health")
        comprobar(
            estado.status_code == 200
            and estado.json()["reto"] == "R01"
            and estado.json()["status"] == "ok",
            "Estado R01 con centrales y conectores disponibles",
        )
        seleccion = []
        for origen in ("oracle", "h2", "hsqldb", "derby"):
            fuente = api.get(f"/connectors/{origen}/alumnos")
            comprobar(
                fuente.status_code == 200 and len(fuente.json()) > 0,
                f"Lectura real del conector {origen}",
            )
            seleccion.append(fuente.json()[0]["id"])
        sync = api.post("/sync", json={"ids": seleccion})
        comprobar(
            sync.status_code == 200 and sync.json()["pendientes"] == 0,
            "Importación selectiva sin tareas pendientes",
        )
        espejo = {a.id: a for a in maria.listar_central()}
        with local.conexion() as db:
            for dni in seleccion:
                central = api.get(f"/alumnos/{dni}").json()
                sqlite = local.obtener(db, dni)
                comprobar(
                    espejo[dni].nombre == sqlite.nombre == central["nombre"]
                    and espejo[dni].version == sqlite.version == central["version"],
                    f"Dato de {central['origen']} guardado en MariaDB y SQLite y consultado por API",
                )
        comprobar(
            api.post(
                "/alumnos", json={"id": "90000000A", "nombre": "Sin Patronus"}
            ).status_code
            == 422,
            "Alta sin Patronus rechazada con 422",
        )
        dni = str(40000000 + secrets.randbelow(10000000)) + "A"
        alta = api.post(
            "/alumnos", json={"id": dni, "nombre": "Demo ADAT", "patronus": "Otter"}
        )
        comprobar(alta.status_code == 201, "Alta válida conservada")
        ruta = f"/alumnos/{dni}"
        comprobar(
            api.put(ruta, json={"version": 1, "patronus": "Inexistente"}).status_code
            == 422,
            "PUT valida el catálogo de Patronus",
        )
        comprobar(
            api.put(ruta, json={"version": 1, "nombre": "Edición ADAT"}).status_code
            == 200,
            "Edición con versión vigente aceptada",
        )
        comprobar(
            api.put(ruta, json={"version": 1, "nombre": "Edición antigua"}).status_code
            == 409,
            "Edición obsoleta rechazada con 409",
        )
        try:
            if args.compose_project:
                # Un origen caído rechaza la escritura y nunca la ejecuta más tarde.
                historico = seleccion[1]
                ruta_historico = f"/alumnos/{historico}"
                previo = api.get(ruta_historico).json()
                compose("stop", "h2")
                parados.add("h2")
                comprobar(
                    api.put(
                        ruta_historico,
                        json={"version": previo["version"], "nombre": "No aplicar"},
                    ).status_code
                    == 503,
                    "Origen caído: edición rechazada con 503",
                )
                comprobar(
                    not api.get("/admin/refresh-queue").json(),
                    "La escritura rechazada no queda pendiente",
                )
                compose("start", "h2")
                parados.remove("h2")
                limite = time.monotonic() + 30
                while time.monotonic() < limite:
                    if api.get("/connectors/h2/alumnos").status_code == 200:
                        break
                    time.sleep(1)
                api.post("/admin/retry")
                comprobar(
                    api.get("/connectors/h2/alumnos").json()[0]["nombre"]
                    == previo["nombre"],
                    "Al recuperar el origen no se aplica la edición rechazada",
                )
                compose("stop")
                parados.add("mariadb")
                lectura = api.get(ruta)
                comprobar(
                    lectura.status_code == 200
                    and lectura.json()["desde_cache"]
                    and lectura.headers["X-Data-Source"] == "sqlite-cache",
                    "Consulta SQLite identificada como caché con MariaDB detenida",
                )
                alta_caida = {
                    "id": str(50000000 + secrets.randbelow(10000000)) + "A",
                    "nombre": "No aceptar",
                    "patronus": "Otter",
                }
                comprobar(
                    api.post("/alumnos", json=alta_caida).status_code == 503,
                    "Alta con MariaDB caída rechazada con 503",
                )
                comprobar(
                    api.put(
                        ruta, json={"version": 2, "nombre": "No aceptar"}
                    ).status_code
                    == 503,
                    "Edición de nuevo rechazada con MariaDB caída",
                )
                cambio = api.put(
                    ruta_historico,
                    json={
                        "version": previo["version"],
                        "nombre": previo["nombre"] + " demo",
                    },
                )
                comprobar(
                    cambio.status_code == 200
                    and cambio.json()["sincronizacion"] == "PENDING",
                    "Histórico confirmado en su origen y refresco pendiente de MariaDB",
                )
                comprobar(
                    api.get("/health").json()["status"] == "degraded",
                    "Estado degradado durante la caída",
                )
                # El worker es el que agota cinco intentos; no se sustituyen por
                # cinco órdenes de escritura ni por resets administrativos.
                limite = time.monotonic() + 100
                tarea = None
                while time.monotonic() < limite:
                    cola = api.get("/admin/refresh-queue").json()
                    if cola and cola[0]["STATUS"] == "FAILED":
                        tarea = cola[0]
                        break
                    time.sleep(1)
                comprobar(
                    tarea is not None and tarea["ATTEMPTS"] == 5,
                    "Cinco intentos automáticos dejan FAILED persistente",
                )
                compose("start")
                parados.remove("mariadb")
                limite = time.monotonic() + 30
                while time.monotonic() < limite:
                    if api.get("/admin/coherence").status_code == 200:
                        break
                    time.sleep(1)
                discrepancia = api.get("/admin/coherence").json()
                comprobar(
                    discrepancia["completo"] and not discrepancia["coinciden"],
                    "Coherencia detecta divergencia real entre las copias",
                )
                comprobar(
                    discrepancia["centrales"]["mariadb"]["registros"]
                    == discrepancia["centrales"]["sqlite"]["registros"]
                    and discrepancia["centrales"]["mariadb"]["huella"]
                    != discrepancia["centrales"]["sqlite"]["huella"],
                    "Las huellas detectan cambios aunque coincidan los conteos",
                )
                api.post(
                    f"/admin/refresh-queue/{tarea['SEQ']}/retry"
                ).raise_for_status()
                comprobar(
                    not api.get("/admin/refresh-queue").json(),
                    "Relanzamiento administrativo de FAILED sin duplicados",
                )
                comprobar(
                    api.get("/admin/coherence").json()["coinciden"],
                    "Las copias convergen tras recuperar el destino",
                )
                comprobar(
                    maria.obtener_central(alta_caida["id"]) is None,
                    "El alta rechazada no se ejecutó al recuperar MariaDB",
                )
                comprobar(
                    api.get(ruta).json()["nombre"] == "Edición ADAT",
                    "La edición rechazada tampoco se ejecutó después",
                )
                vigente_historico = api.get(ruta_historico).json()["version"]
                api.put(
                    ruta_historico,
                    json={"version": vigente_historico, "nombre": previo["nombre"]},
                ).raise_for_status()
            vigente = api.get(ruta).json()["version"]
            comprobar(
                api.delete(ruta, headers={"If-Match": f'"{vigente}"'}).status_code
                == 204,
                "Baja lógica aceptada",
            )
            comprobar(
                api.get(ruta).status_code == 404, "La consulta habitual excluye la baja"
            )
            remoto = next(a for a in maria.listar_central() if a.id == dni)
            with local.conexion() as db:
                sqlite = local.obtener(db, dni)
                comprobar(
                    not remoto.activo
                    and not sqlite.activo
                    and remoto.version == sqlite.version,
                    "Registro inactivo conservado en ambas centrales",
                )
        finally:
            for servicio in parados:
                compose("start", servicio)
    informe["resultado"] = "correcto"
    destino = Path(args.report)
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_text(
        json.dumps(informe, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("Informe:", destino.resolve())


if __name__ == "__main__":
    main()
