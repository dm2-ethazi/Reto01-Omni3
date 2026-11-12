"""Carga los alumnos del RAR con DNI y completa los Patronus vacíos."""

import argparse
import csv
import hashlib
import json
import re
import sqlite3
import sys
import time
from contextlib import ExitStack, contextmanager
from functools import lru_cache
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


@lru_cache(maxsize=8)
def cargar_catalogo(path=ROOT / "database/origins/data/patronus.json"):
    """Lee nombres e imágenes proporcionados; no ejecuta ni descarga recursos."""
    datos = json.loads(Path(path).read_text(encoding="utf-8"))["patronus"]
    catalogo, nombres = [], set()
    for fila in datos:
        nombre, url = fila["nom_eng"], fila["url"]
        if not isinstance(nombre, str) or not nombre.strip() or len(nombre) > 60:
            raise ValueError("Catálogo: nombre de Patronus no válido")
        if nombre.casefold() in nombres:
            raise ValueError("Catálogo: Patronus duplicado")
        if not isinstance(url, str) or len(url) > 500 or (url and not url.startswith("https://")):
            raise ValueError("Catálogo: URL no válida")
        nombres.add(nombre.casefold())
        catalogo.append((nombre, url))
    if not catalogo:
        raise ValueError("El catálogo de Patronus está vacío")
    return tuple(sorted(catalogo, key=lambda item: item[0].casefold()))


def asignar_patronus(identificador, catalogo):
    """Mismo UUID y catálogo producen la misma asignación, sin estado aleatorio."""
    indice = int.from_bytes(hashlib.sha256(identificador.casefold().encode()).digest()[:8], "big")
    return catalogo[indice % len(catalogo)][0]


def cargar_semilla(carpeta, catalogo=None):
    """Valida todos los archivos antes de modificar ninguna base."""
    catalogo = cargar_catalogo() if catalogo is None else catalogo
    nombres_catalogo = {nombre.casefold(): nombre for nombre, _ in catalogo}
    alumnos = json.loads((Path(carpeta) / "alumnos.json").read_text(encoding="utf-8"))["alumnos"]
    resultado, equivalencias, vistos, dnis = {}, {}, set(), set()
    for motor, (casa, archivo) in FUENTES.items():
        with (Path(carpeta) / archivo).open(encoding="utf-8-sig", newline="") as f:
            lector = csv.DictReader(f)
            if len(lector.fieldnames or []) != len(COLUMNAS_CSV) or set(lector.fieldnames or []) != COLUMNAS_CSV:
                raise ValueError(f"{archivo}: columnas distintas del formato esperado")
            filas, mapa = [], {}
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
                alumno = alumnos[datos["id"]]
                dni = alumno["id"]
                if not re.fullmatch(r"[0-9]{8}[A-Z]", dni) or dni[-1] != "TRWAGMYFPDXBNJZSQVHLCKE"[int(dni[:8]) % 23]:
                    raise ValueError(f"{archivo}:{numero}: DNI de semilla no válido")
                if dni in dnis:
                    raise ValueError(f"{archivo}:{numero}: DNI duplicado; no se cargan datos")
                if datos["nombre"].split() != " ".join(x for x in (alumno["nombre"], alumno["apellido"]) if x).split():
                    raise ValueError(f"{archivo}:{numero}: el alumno del RAR no coincide con el CSV")
                if datos["casa"] != casa or not 1 <= len(datos["nombre"]) <= 100:
                    raise ValueError(f"{archivo}:{numero}: casa o nombre incorrectos")
                patronus = alumno["patronus"]
                if patronus:
                    patronus = nombres_catalogo.get(patronus.casefold(), patronus)
                row = (dni, alumno["nombre"], alumno["apellido"], alumno["sexo"],
                       patronus or asignar_patronus(datos["id"], catalogo),
                       alumno["imagen"], casa, datos["alias"] or None,
                       datos["especie"] or None, datos["género"] or None,
                       datos["nacimiento"] or None, datos["nacionalidad"] or None,
                       datos["wiki"] or None)
                limites = {"ID": 64, "NOMBRE": 100, "APELLIDO": 100, "PATRONUS": 60, "IMAGEN": 500, **EXTRAS}
                if alumno["sexo"] not in {None, "H", "M", "S"}:
                    raise ValueError(f"{archivo}:{numero}: sexo no válido")
                if any(row[COLUMNAS.index(k)] is not None and
                       len(row[COLUMNAS.index(k)]) > limite for k, limite in limites.items()):
                    raise ValueError(f"{archivo}:{numero}: campo demasiado largo")
                vistos.add(datos["id"])
                dnis.add(dni)
                mapa[datos["id"]] = dni
                filas.append(row)
        if not filas:
            raise ValueError(f"{archivo}: no contiene registros")
        resultado[motor] = filas
        equivalencias[motor] = mapa
    return resultado, equivalencias


def cargar_csv(carpeta, catalogo=None):
    return cargar_semilla(carpeta, catalogo)[0]


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


def migrar_identificadores(cursor, equivalencias, filas, oracle=False):
    """Actualiza semillas UUID anteriores y conserva sus ediciones particulares."""
    cursor.execute("SELECT ID,NOMBRE,APELLIDO,PATRONUS FROM ALUMNOS")
    presentes = {str(f[0]): tuple(None if v is None else str(v) for v in f[1:])
                 for f in cursor.fetchall()}
    destino = {f[0]: f for f in filas}
    cambios = [(anterior, dni) for anterior, dni in equivalencias.items() if anterior in presentes]
    if any(dni in presentes for _, dni in cambios):
        raise ValueError("El DNI de destino ya existe; se conserva el UUID para no pisar datos")
    for anterior, dni in cambios:
        nombre, apellido, patronus = presentes[anterior]
        fila = destino[dni]
        nuevo_nombre, nuevo_apellido = nombre, apellido
        if apellido is None and nombre.split() == " ".join(x for x in fila[1:3] if x).split():
            nuevo_nombre, nuevo_apellido = fila[1:3]
        nuevo_patronus = fila[4] if not patronus or patronus == asignar_patronus(anterior, cargar_catalogo()) else patronus
        ejecutar(cursor, "UPDATE ALUMNOS SET ID=?,NOMBRE=?,APELLIDO=?,PATRONUS=? WHERE ID=? "
                 "AND NOMBRE=? AND (APELLIDO=? OR (APELLIDO IS NULL AND ? IS NULL)) "
                 "AND (PATRONUS=? OR (PATRONUS IS NULL AND ? IS NULL))",
                 (dni, nuevo_nombre, nuevo_apellido, nuevo_patronus, anterior,
                  nombre, apellido, apellido, patronus, patronus), oracle)
        if cursor.rowcount != 1:
            raise ValueError("El alumno se ha editado durante la migración; se revierte la fuente")
    return len(cambios)


def sembrar(db, motor, filas, equivalencias=None):
    if hasattr(db, "jconn"):
        db.jconn.setAutoCommit(False)
    cursor = db.cursor()
    try:
        preparar_tabla(db, cursor, motor)
        migrados = migrar_identificadores(cursor, equivalencias or {}, filas, motor == "oracle")
        cursor.execute("SELECT ID,PATRONUS FROM ALUMNOS")
        existentes = {str(f[0]): f[1] for f in cursor.fetchall()}
        nuevas = [fila for fila in filas if fila[0] not in existentes]
        if nuevas:
            if motor == "oracle":
                parametros = ",".join(f":p{i}" for i in range(len(COLUMNAS)))
                valores = [{f"p{i}": v for i, v in enumerate(f)} for f in nuevas]
            else:
                parametros = ",".join("?" for _ in COLUMNAS)
                valores = nuevas
            cursor.executemany(f"INSERT INTO ALUMNOS ({','.join(COLUMNAS)}) VALUES ({parametros})", valores)
        completados = 0
        for fila in filas:
            if fila[0] in existentes and not str(existentes[fila[0]] or "").strip():
                # La condición también protege una edición entre lectura y UPDATE.
                ejecutar(cursor, "UPDATE ALUMNOS SET PATRONUS=? WHERE ID=? AND "
                         "(PATRONUS IS NULL OR TRIM(PATRONUS)='' OR TRIM(PATRONUS) IS NULL)",
                         (fila[4], fila[0]), motor == "oracle")
                completados += max(0, cursor.rowcount)
            if fila[3] == "S":
                ejecutar(cursor, "UPDATE ALUMNOS SET SEXO=? WHERE ID=? AND SEXO IS NULL",
                         ("S", fila[0]), motor == "oracle")
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
        return len(nuevas), len(filas) - len(nuevas), retirados, completados, migrados
    except BaseException:
        db.rollback()
        raise
    finally:
        cursor.close()


def sembrar_catalogo(maria, catalogo):
    """Completa PATRONUS_IMG, respetando URLs que ya se hayan personalizado."""
    with maria._conexion() as db:
        try:
            with db.cursor() as cursor:
                cursor.execute("SELECT NOM_ENG,URL FROM PATRONUS_IMG")
                existentes = {nombre.casefold(): (nombre, url) for nombre, url in cursor.fetchall()}
                for nombre, url in catalogo:
                    actual = existentes.get(nombre.casefold())
                    if actual is None:
                        cursor.execute("INSERT INTO PATRONUS_IMG (NOM_ENG,URL) VALUES (%s,%s)", (nombre, url))
                    elif url and not str(actual[1] or "").strip():
                        cursor.execute("UPDATE PATRONUS_IMG SET URL=%s WHERE NOM_ENG=%s "
                                       "AND (URL IS NULL OR TRIM(URL)='')", (url, actual[0]))
            db.commit()
        except BaseException:
            db.rollback()
            raise


def comprobar_migracion(maria, sqlite_path, equivalencias):
    """No cambiar claves con refrescos pendientes ni colisiones con altas nuevas."""
    antiguos = {uuid for mapa in equivalencias.values() for uuid in mapa}
    destinos = {dni: motor for motor, mapa in equivalencias.items() for dni in mapa.values()}
    if maria:
        for alumno in maria.listar_central():
            if alumno.id in destinos and (alumno.tipo != "HISTORICO" or alumno.origen != destinos[alumno.id]):
                raise ValueError("Un DNI ficticio ya pertenece a otro alumno en MariaDB")
    if not Path(sqlite_path).is_file():
        return
    with sqlite3.connect(sqlite_path) as db:
        tablas = {f[0] for f in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if "OUTBOX" in tablas:
            if any(f[0] in antiguos for f in db.execute("SELECT ALUMNO_ID FROM OUTBOX")):
                raise ValueError("Hay refrescos de UUID pendientes o fallidos; resolverlos antes de migrar")
        if "ALUMNOS" in tablas:
            for id, tipo, origen in db.execute("SELECT ID,TIPO,ORIGEN FROM ALUMNOS"):
                if id in destinos and (tipo != "HISTORICO" or origen != destinos[id]):
                    raise ValueError("Un DNI ficticio ya pertenece a otro alumno en SQLite")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file")
    parser.add_argument("--data-dir", type=Path, default=ROOT / "database/origins/data")
    parser.add_argument("--wait-seconds", type=float, default=180)
    parser.add_argument("--retired-ids-file", type=Path,
                        help="Escribe UUID anteriores para desactivar sus copias mediante POST /sync")
    args = parser.parse_args()
    if args.env_file:
        from dotenv import load_dotenv
        load_dotenv(args.env_file, override=True)
    try:
        catalogo = cargar_catalogo()
        datos, equivalencias = cargar_semilla(args.data_dir, catalogo)
    except (ValueError, OSError, KeyError) as exc:
        print(f"CSV: {exc}", file=sys.stderr, flush=True)
        return 1
    from proyecto_reto1.db.central_mariadb import CentralMariaDB
    from proyecto_reto1.db.factory import obtener_repositorios
    from proyecto_reto1.config import settings
    repos = obtener_repositorios()
    maria = next((r for r in repos if isinstance(r, CentralMariaDB)), None)
    if maria:
        try:
            with esperar_conexion(maria, args.wait_seconds, "mariadb"):
                pass
            comprobar_migracion(maria, settings.sqlite_path, equivalencias)
            nombres = dict(catalogo)
            for filas in datos.values():
                for fila in filas:
                    nombres.setdefault(fila[4], "")
            sembrar_catalogo(maria, tuple(nombres.items()))
            print(f"patronus: catálogo de {len(nombres)} opciones preparado", flush=True)
        except ValueError as exc:
            print(f"mariadb: {exc}", file=sys.stderr, flush=True)
            return 1
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
        print(f"{motor}: preparando {len(datos[motor])} alumnos del RAR...", flush=True)
        try:
            with esperar_conexion(repo, args.wait_seconds, motor) as db:
                nuevos, existentes, retirados, completados, migrados = sembrar(
                    db, motor, datos[motor], equivalencias[motor])
            print(f"{motor}: insertados={nuevos}; ya existentes={existentes}; "
                  f"UUID migrados a DNI={migrados}; patronus completados={completados}; "
                  f"ejemplos antiguos retirados={retirados}; "
                  f"lectura={len(repo.listar())}", flush=True)
        except Exception as exc:
            print(f"{motor}: FALLO ({type(exc).__name__}); revisa conexión, credenciales y esquema", file=sys.stderr, flush=True)
            fallos += 1
    if not fallos and args.retired_ids_file:
        args.retired_ids_file.parent.mkdir(parents=True, exist_ok=True)
        args.retired_ids_file.write_text(json.dumps(sorted(
            uuid for mapa in equivalencias.values() for uuid in mapa)), encoding="utf-8")
    return 1 if fallos else 0


if __name__ == "__main__":
    raise SystemExit(main())
