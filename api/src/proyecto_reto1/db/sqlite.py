"""Réplica de lectura, seguimiento por alumno y reservas durables de refresco."""

import sqlite3
import time
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path

from .base import Alumno, ColaLlena, Conflicto, Patronus

CAMPOS = (
    "id",
    "nombre",
    "apellido",
    "sexo",
    "patronus",
    "imagen",
    "casa",
    "origen",
    "tipo",
    "version",
    "activo",
    "actualizado_en",
)
ACTIVOS = "('PREPARED','PENDING')"


def instante():
    return datetime.now(timezone.utc).isoformat()


def persistible(alumno):
    """Los indicadores de lectura y sincronización no forman parte del dato replicado."""
    return Alumno(**{c: getattr(alumno, c) for c in CAMPOS})


class CentralSQLite:
    def __init__(self, path: str):
        self.path = path
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.conexion() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS ALUMNOS (
                    ID TEXT PRIMARY KEY, NOMBRE TEXT NOT NULL, APELLIDO TEXT,
                    SEXO TEXT, PATRONUS TEXT, IMAGEN TEXT, CASA TEXT,
                    ORIGEN TEXT NOT NULL, TIPO TEXT NOT NULL, VERSION INTEGER NOT NULL,
                    ACTIVO INTEGER NOT NULL, ACTUALIZADO_EN TEXT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS PATRONUS_IMG (NOM_ENG TEXT PRIMARY KEY, URL TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS OUTBOX (
                    SEQ INTEGER PRIMARY KEY AUTOINCREMENT, TARGET TEXT NOT NULL,
                    ALUMNO_ID TEXT NOT NULL, PAYLOAD TEXT NOT NULL, EXPECTED TEXT,
                    ATTEMPTS INTEGER NOT NULL DEFAULT 0, NEXT_ATTEMPT REAL NOT NULL DEFAULT 0,
                    STATUS TEXT NOT NULL DEFAULT 'PREPARED', LAST_ERROR TEXT,
                    SOURCE TEXT, OPERATION TEXT, CONFIRMED INTEGER NOT NULL DEFAULT 0,
                    CREATED_AT REAL NOT NULL DEFAULT 0, UPDATED_AT TEXT
                );
                CREATE TABLE IF NOT EXISTS SYNC_STATE (
                    ALUMNO_ID TEXT PRIMARY KEY, ESTADO TEXT NOT NULL,
                    VERSION INTEGER NOT NULL, SINCRONIZADO_EN TEXT, ULTIMA_TAREA INTEGER
                );
            """)
            columnas = {f[1] for f in db.execute("PRAGMA table_info(OUTBOX)")}
            for columna, tipo in {
                "SOURCE": "TEXT",
                "OPERATION": "TEXT",
                "CONFIRMED": "INTEGER NOT NULL DEFAULT 0",
                "CREATED_AT": "REAL NOT NULL DEFAULT 0",
                "UPDATED_AT": "TEXT",
            }.items():
                if columna not in columnas:
                    db.execute(f"ALTER TABLE OUTBOX ADD COLUMN {columna} {tipo}")
            # La versión anterior guardaba órdenes de escritura. Preservarlas para
            # revisión, pero jamás ejecutarlas como si fueran refrescos confirmados.
            db.execute(
                "UPDATE OUTBOX SET STATUS='FAILED',LAST_ERROR='LEGACY_REVIEW' WHERE SOURCE IS NULL"
            )
            db.execute(
                "CREATE INDEX IF NOT EXISTS outbox_estado ON OUTBOX(STATUS,NEXT_ATTEMPT,SEQ)"
            )

    @contextmanager
    def conexion(self, escritura=False):
        db = sqlite3.connect(self.path, timeout=30)
        db.row_factory = sqlite3.Row
        try:
            if escritura:
                db.execute("BEGIN IMMEDIATE")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    @staticmethod
    def modelo(fila):
        return (
            Alumno(**{key.lower(): value for key, value in dict(fila).items()})
            if fila
            else None
        )

    @staticmethod
    def obtener(db, dni):
        return CentralSQLite.modelo(
            db.execute("SELECT * FROM ALUMNOS WHERE ID=?", (dni,)).fetchone()
        )

    @staticmethod
    def guardar(db, alumno):
        datos = alumno.model_dump()
        cambios = ",".join(f"{c}=excluded.{c}" for c in CAMPOS if c != "id")
        db.execute(
            f"INSERT INTO ALUMNOS ({','.join(CAMPOS)}) VALUES ({','.join('?' for _ in CAMPOS)}) "
            f"ON CONFLICT(ID) DO UPDATE SET {cambios}",
            tuple(datos[c] for c in CAMPOS),
        )

    def listar_central(self):
        with self.conexion() as db:
            return [
                self.modelo(f) for f in db.execute("SELECT * FROM ALUMNOS ORDER BY ID")
            ]

    def listar(self, nombre=None):
        return [
            a
            for a in self.listar_central()
            if a.activo
            and (not nombre or a.nombre.casefold().startswith(nombre.casefold()))
        ]

    @staticmethod
    def seguimiento(db, alumno, estado, tarea=None, sincronizado=False):
        db.execute(
            """INSERT INTO SYNC_STATE(ALUMNO_ID,ESTADO,VERSION,SINCRONIZADO_EN,ULTIMA_TAREA)
                      VALUES(?,?,?,?,?) ON CONFLICT(ALUMNO_ID) DO UPDATE SET
                      ESTADO=excluded.ESTADO,VERSION=excluded.VERSION,ULTIMA_TAREA=excluded.ULTIMA_TAREA,
                      SINCRONIZADO_EN=COALESCE(excluded.SINCRONIZADO_EN,SYNC_STATE.SINCRONIZADO_EN)""",
            (
                alumno.id,
                estado,
                alumno.version,
                instante() if sincronizado else None,
                tarea,
            ),
        )

    def estado_alumno(self, dni):
        with self.conexion() as db:
            fila = db.execute(
                "SELECT ESTADO,SINCRONIZADO_EN FROM SYNC_STATE WHERE ALUMNO_ID=?",
                (dni,),
            ).fetchone()
            return (fila[0], fila[1]) if fila else ("UNKNOWN", None)

    @staticmethod
    def reservar(db, source, alumno, previo, operacion, capacidad, confirmada=False):
        if (
            db.execute(
                f"SELECT COUNT(*) FROM OUTBOX WHERE STATUS IN {ACTIVOS}"
            ).fetchone()[0]
            >= capacidad
        ):
            raise ColaLlena("La cola de refrescos ha alcanzado su capacidad")
        if db.execute(
            "SELECT 1 FROM OUTBOX WHERE ALUMNO_ID=?", (alumno.id,)
        ).fetchone():
            raise Conflicto(
                "El alumno tiene un refresco pendiente o fallido; revisa administración"
            )
        cache = CentralSQLite.obtener(db, alumno.id)
        if cache is not None and (previo is None or cache.version != previo.version):
            raise Conflicto(
                "La copia ha cambiado durante la operación; recarga antes de repetir"
            )
        estado = "PENDING" if confirmada else "PREPARED"
        cursor = db.execute(
            """INSERT INTO OUTBOX
            (TARGET,ALUMNO_ID,PAYLOAD,EXPECTED,SOURCE,OPERATION,CONFIRMED,CREATED_AT,UPDATED_AT,STATUS)
            VALUES('centrales',?,?,?,?,?,?,?,?,?)""",
            (
                alumno.id,
                persistible(alumno).model_dump_json(),
                persistible(previo).model_dump_json() if previo else None,
                source,
                operacion,
                int(confirmada),
                time.time(),
                instante(),
                estado,
            ),
        )
        CentralSQLite.seguimiento(db, alumno, estado, cursor.lastrowid)
        return cursor.lastrowid

    @staticmethod
    def confirmar(db, seq, alumno):
        db.execute(
            "UPDATE OUTBOX SET PAYLOAD=?,CONFIRMED=1,STATUS='PENDING',NEXT_ATTEMPT=0,UPDATED_AT=? WHERE SEQ=?",
            (persistible(alumno).model_dump_json(), instante(), seq),
        )
        CentralSQLite.seguimiento(db, alumno, "PENDING", seq)

    @staticmethod
    def cancelar(db, tarea):
        db.execute("DELETE FROM OUTBOX WHERE SEQ=?", (tarea["SEQ"],))
        if tarea["EXPECTED"]:
            previo = Alumno.model_validate_json(tarea["EXPECTED"])
            CentralSQLite.seguimiento(db, previo, "SYNCED")
        else:
            db.execute(
                "DELETE FROM SYNC_STATE WHERE ALUMNO_ID=?", (tarea["ALUMNO_ID"],)
            )

    def catalogo(self):
        with self.conexion() as db:
            return [
                Patronus(nom_eng=f[0], url=f[1])
                for f in db.execute(
                    "SELECT NOM_ENG,URL FROM PATRONUS_IMG ORDER BY NOM_ENG"
                )
            ]

    def guardar_catalogo(self, patronus):
        with self.conexion(escritura=True) as db:
            db.execute("DELETE FROM PATRONUS_IMG")
            db.executemany(
                "INSERT INTO PATRONUS_IMG VALUES(?,?)",
                [(p.nom_eng, p.url) for p in patronus],
            )

    def cola(self):
        with self.conexion() as db:
            return [
                dict(f)
                for f in db.execute("""SELECT SEQ,TARGET,SOURCE,OPERATION,CONFIRMED,
                ATTEMPTS,NEXT_ATTEMPT,STATUS,LAST_ERROR,UPDATED_AT FROM OUTBOX ORDER BY SEQ""")
            ]

    def estadisticas(self, capacidad):
        with self.conexion() as db:
            conteos = dict(
                db.execute("SELECT STATUS,COUNT(*) FROM OUTBOX GROUP BY STATUS")
            )
        activas = conteos.get("PENDING", 0) + conteos.get("PREPARED", 0)
        return {
            "activas": activas,
            "fallidas": conteos.get("FAILED", 0),
            "capacidad": capacidad,
            "ocupacion": activas / capacidad,
            "aviso": activas / capacidad >= 0.8,
        }
