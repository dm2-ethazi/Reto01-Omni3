"""Carga los CSV históricos sin pisar ediciones ni inventar DNI o Patronus."""

import argparse
import csv
import sys
import time
from contextlib import ExitStack, contextmanager
from pathlib import Path
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]
FUENTES = {
    "oracle": ("Ravenclaw", "ravenclaw.csv"),
    "h2": ("Hufflepuff", "hufflepuff.csv"),
    "hsqldb": ("Slytherin", "slytherin.csv"),
    "derby": ("Gryffindor", "gryffindor.csv"),
}
COLUMNAS_CSV = {"id", "nombre", "casa", "alias", "especie", "género",
                "nacimiento", "nacionalidad", "imagen", "wiki"}
EXTRAS = {"CASA": 30, "ALIAS": 2000, "ESPECIE": 200, "GENERO": 100,
          "NACIMIENTO": 200, "NACIONALIDAD": 200, "WIKI": 500}
COLUMNAS = ("ID", "NOMBRE", "APELLIDO", "SEXO", "PATRONUS", "IMAGEN", *EXTRAS)
# Solo se retiran los ejemplos anteriores cuyo contenido sigue intacto.
EJEMPLOS = {
    "oracle": ("10000001A", "Luna", "Lovegood", "M", "Doe", None),
    "h2": ("10000002B", "Cedric", "Diggory", "H", None, None),
    "hsqldb": ("10000003C", "Severus", "Snape", "H", "Doe", None),
    "derby": ("10000004D", "Harry", "Potter", "H", "Stag", None),
}


def cargar_csv(carpeta):
    """Valida todos los archivos antes de modificar ninguna base."""
    resultado, vistos = {}, set()
    for motor, (casa, archivo) in FUENTES.items():
        with (Path(carpeta) / archivo).open(encoding="utf-8-sig", newline="") as f:
            lector = csv.DictReader(f)
            if len(lector.fieldnames or []) != len(COLUMNAS_CSV) or set(lector.fieldnames or []) != COLUMNAS_CSV:
                raise ValueError(f"{archivo}: columnas distintas del formato esperado")
            filas = []
            for numero, fila in enumerate(lector, 2):
                if None in fila or any(v is None for v in fila.values()):
                    raise ValueError(f"{archivo}:{numero}: fila incompleta")
                datos = {k: v.strip() for k, v in fila.items()}
                try:
                    if str(UUID(datos["id"])) != datos["id"]:
                        raise ValueError
                except ValueError as exc:
                    raise ValueError(f"{archivo}:{numero}: UUID no canónico") from exc
                if datos["id"] in vistos:
                    raise ValueError(f"{archivo}:{numero}: identificador duplicado")
                if datos["casa"] != casa or not 1 <= len(datos["nombre"]) <= 100:
                    raise ValueError(f"{archivo}:{numero}: casa o nombre incorrectos")
                genero = datos["género"].casefold()
                sexo = "H" if genero in {"male", "males"} else (
                    "M" if genero in {"female", "females"} else ("S" if genero else None)
                )
                row = (datos["id"], datos["nombre"], None, sexo, None,
                       datos["imagen"] or None, casa, datos["alias"] or None,
                       datos["especie"] or None, datos["género"] or None,
                       datos["nacimiento"] or None, datos["nacionalidad"] or None,
                       datos["wiki"] or None)
                limites = {"ID": 64, "NOMBRE": 100, "IMAGEN": 500, **EXTRAS}
                if any(row[COLUMNAS.index(k)] is not None and
                       len(row[COLUMNAS.index(k)]) > limite for k, limite in limites.items()):
                    raise ValueError(f"{archivo}:{numero}: campo demasiado largo")
                vistos.add(datos["id"])
                filas.append(row)
        if not filas:
            raise ValueError(f"{archivo}: no contiene registros")
        resultado[motor] = filas
    return resultado


def autenticacion_invalida(exc):
    """Distingue claves incorrectas de un servidor que aún está arrancando."""
    for detalle in (exc, *getattr(exc, "args", ())):
        if getattr(detalle, "code", None) in {1017, 28000, 28001}:
            return True
        if isinstance(detalle, int) and detalle in {1044, 1045}:
            return True
        if hasattr(detalle, "getSQLState") and str(detalle.getSQLState()) == "28000":
            return True
    texto = str(exc).upper()
    return any(c in texto for c in ("ORA-01017", "ORA-28000", "WRONG USER NAME OR PASSWORD"))


@contextmanager
def esperar_conexion(repo, segundos, nombre):
    inicio = time.monotonic()
    aviso = inicio
    with ExitStack() as stack:
        while True:
            try:
                db = stack.enter_context(repo._conexion())
                break
            except Exception as exc:
                if autenticacion_invalida(exc):
                    raise ValueError(
                        f"{nombre}: credenciales rechazadas; reutiliza la configuración de "
                        "la instalación existente. Cambiar el .env no cambia las claves del volumen."
                    ) from exc
                ahora = time.monotonic()
                if ahora - inicio >= segundos:
                    raise
                if ahora >= aviso:
                    print(f"{nombre}: esperando conexión ({int(ahora-inicio)} s; "
                          f"{type(exc).__name__})", flush=True)
                    aviso = ahora + 10
                time.sleep(min(2, max(0, segundos - (ahora - inicio))))
        yield db


def ejecutar(cursor, sql, valores, oracle):
    if oracle:
        partes = sql.split("?")
        sql = partes[0] + "".join(f":p{i}" + p for i, p in enumerate(partes[1:]))
        cursor.execute(sql, {f"p{i}": v for i, v in enumerate(valores)})
    else:
        cursor.execute(sql, tuple(valores))


def preparar_tabla(db, cursor, motor):
    if motor == "oracle":
        cursor.execute("SELECT COLUMN_NAME,CHAR_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME='ALUMNOS'")
        columnas = {str(k): int(v or 0) for k, v in cursor.fetchall()}
    else:
        metadata = db.jconn.getMetaData().getColumns(None, None, "ALUMNOS", None)
        columnas = {}
        try:
            while metadata.next():
                columnas[str(metadata.getString("COLUMN_NAME"))] = metadata.getInt("COLUMN_SIZE")
        finally:
            metadata.close()
    if not columnas:
        sql = (ROOT / "database/origins" / f"{motor}.sql").read_text(encoding="utf-8")
        cursor.execute(sql.strip().rstrip(";"))
        return
    if columnas.get("ID", 0) < 64:
        if motor == "oracle":
            cursor.execute("ALTER TABLE ALUMNOS MODIFY (ID VARCHAR2(64))")
        elif motor == "derby":
            cursor.execute("ALTER TABLE ALUMNOS ALTER COLUMN ID SET DATA TYPE VARCHAR(64)")
        else:
            cursor.execute("ALTER TABLE ALUMNOS ALTER COLUMN ID VARCHAR(64)")
    for campo, longitud in EXTRAS.items():
        if campo not in columnas:
            tipo = "VARCHAR2" if motor == "oracle" else "VARCHAR"
            cursor.execute(f"ALTER TABLE ALUMNOS ADD {campo} {tipo}({longitud})")


def sembrar(db, motor, filas):
    if hasattr(db, "jconn"):
        db.jconn.setAutoCommit(False)
    cursor = db.cursor()
    try:
        preparar_tabla(db, cursor, motor)
        cursor.execute("SELECT ID FROM ALUMNOS")
        existentes = {str(f[0]) for f in cursor.fetchall()}
        nuevas = [fila for fila in filas if fila[0] not in existentes]
        if nuevas:
            if motor == "oracle":
                parametros = ",".join(f":p{i}" for i in range(len(COLUMNAS)))
                valores = [{f"p{i}": v for i, v in enumerate(f)} for f in nuevas]
            else:
                parametros = ",".join("?" for _ in COLUMNAS)
                valores = nuevas
            cursor.executemany(f"INSERT INTO ALUMNOS ({','.join(COLUMNAS)}) VALUES ({parametros})", valores)
        ejemplo = EJEMPLOS[motor]
        condicion = "ID=?" + "".join(
            f" AND ({c}=? OR ({c} IS NULL AND ? IS NULL))" for c in COLUMNAS[1:6]
        )
        condicion += "".join(f" AND {c} IS NULL" for c in EXTRAS)
        previos = [ejemplo[0]]
        for valor in ejemplo[1:]:
            previos.extend((valor, valor))
        ejecutar(cursor, "DELETE FROM ALUMNOS WHERE " + condicion, previos, motor == "oracle")
        retirados = cursor.rowcount
        db.commit()
        return len(nuevas), len(filas) - len(nuevas), retirados
    except BaseException:
        db.rollback()
        raise
    finally:
        cursor.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file")
    parser.add_argument("--data-dir", type=Path, default=ROOT / "database/origins/data")
    parser.add_argument("--wait-seconds", type=float, default=180)
    args = parser.parse_args()
    if args.env_file:
        from dotenv import load_dotenv
        load_dotenv(args.env_file, override=True)
    try:
        datos = cargar_csv(args.data_dir)
    except (ValueError, OSError) as exc:
        print(f"CSV: {exc}", file=sys.stderr, flush=True)
        return 1
    from proyecto_reto1.db.central_mariadb import CentralMariaDB
    from proyecto_reto1.db.factory import obtener_repositorios
    repos = obtener_repositorios()
    maria = next((r for r in repos if isinstance(r, CentralMariaDB)), None)
    if maria:
        try:
            with esperar_conexion(maria, args.wait_seconds, "mariadb"):
                pass
        except Exception as exc:
            print(f"mariadb: FALLO ({type(exc).__name__}); revisa las credenciales de la instalación", file=sys.stderr, flush=True)
            return 1
    nombres = {casa: motor for motor, (casa, _) in FUENTES.items()}
    fuentes = [r for r in repos if not isinstance(r, CentralMariaDB)]
    if not fuentes:
        print("No hay fuentes históricas configuradas", file=sys.stderr)
        return 1
    fallos = 0
    for repo in fuentes:
        motor = nombres[repo.casa]
        if motor == "derby" and ";create=true" not in repo.url:
            repo.url += ";create=true"
        print(f"{motor}: preparando {len(datos[motor])} registros del CSV...", flush=True)
        try:
            with esperar_conexion(repo, args.wait_seconds, motor) as db:
                nuevos, existentes, retirados = sembrar(db, motor, datos[motor])
            print(f"{motor}: insertados={nuevos}; ya existentes={existentes}; "
                  f"ejemplos antiguos retirados={retirados}; lectura={len(repo.listar())}", flush=True)
        except Exception as exc:
            print(f"{motor}: FALLO ({type(exc).__name__}); revisa conexión, credenciales y esquema", file=sys.stderr, flush=True)
            fallos += 1
    return 1 if fallos else 0


if __name__ == "__main__":
    raise SystemExit(main())
