"""Crea las tablas compatibles con api/ y añade ejemplos sin borrar datos.

Ejecutar después de docker compose up -d, con api/.env configurado.
También admite --env-file para un entorno de comprobación independiente.
"""

import argparse
import sys
import time
from contextlib import ExitStack, contextmanager
from pathlib import Path


@contextmanager
def esperar_conexion(repo, segundos):
    limite = time.monotonic() + segundos
    with ExitStack() as stack:
        while True:
            try:
                db = stack.enter_context(repo._conexion())
                break
            except Exception:
                if time.monotonic() >= limite:
                    raise
                time.sleep(2)
        yield db


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file")
    parser.add_argument("--wait-seconds", type=float, default=180)
    args = parser.parse_args()
    if args.env_file:
        from dotenv import load_dotenv

        load_dotenv(args.env_file, override=True)
    from proyecto_reto1.db.central_mariadb import CentralMariaDB
    from proyecto_reto1.db.factory import obtener_repositorios

    root = Path(__file__).resolve().parents[1]
    nombres = {
        "Ravenclaw": "oracle",
        "Hufflepuff": "h2",
        "Slytherin": "hsqldb",
        "Gryffindor": "derby",
    }
    fallos = 0
    repos = [r for r in obtener_repositorios() if not isinstance(r, CentralMariaDB)]
    if not repos:
        print(
            "No hay fuentes activas; configura DB_ENGINES con los cuatro orígenes",
            file=sys.stderr,
        )
        return 1
    for repo in repos:
        nombre = nombres[repo.casa]
        if nombre == "derby" and ";create=true" not in repo.url:
            repo.url += ";create=true"
        try:
            with esperar_conexion(repo, args.wait_seconds) as db:
                cursor = db.cursor()
                try:
                    if nombre == "oracle":
                        cursor.execute(
                            "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME='ALUMNOS'"
                        )
                        existe = cursor.fetchone()[0] > 0
                    else:
                        metadata = db.jconn.getMetaData().getTables(
                            None, None, "ALUMNOS", None
                        )
                        try:
                            existe = metadata.next()
                        finally:
                            metadata.close()
                    sql = (root / "database" / "origins" / f"{nombre}.sql").read_text(
                        encoding="utf-8"
                    )
                    sentencias = [
                        "\n".join(
                            l for l in s.splitlines() if not l.strip().startswith("--")
                        ).strip()
                        for s in sql.split(";")
                    ]
                    if not existe:
                        cursor.execute(sentencias[0])
                    # La semilla solo inserta un registro de ejemplo por fuente.
                    identificadores = {
                        "oracle": "10000001A",
                        "h2": "10000002B",
                        "hsqldb": "10000003C",
                        "derby": "10000004D",
                    }
                    parametro = ":id" if nombre == "oracle" else "?"
                    cursor.execute(
                        f"SELECT ID FROM ALUMNOS WHERE ID={parametro}",
                        {"id": identificadores[nombre]}
                        if nombre == "oracle"
                        else (identificadores[nombre],),
                    )
                    if cursor.fetchone() is None:
                        cursor.execute(sentencias[1])
                    db.commit()
                finally:
                    cursor.close()
            print(
                f"{nombre}: preparado; lectura de {len(repo.listar())} registros correcta"
            )
        except Exception as exc:
            print(
                f"{nombre}: FALLO ({type(exc).__name__}); revisa servicio, credenciales y esquema",
                file=sys.stderr,
            )
            fallos += 1
    return 1 if fallos else 0


if __name__ == "__main__":
    raise SystemExit(main())
