"""Fuente de verdad de nuevos alumnos y vista central de los históricos."""

from contextlib import suppress

import pymysql

from .base import Alumno, Conflicto, EscrituraIncierta
from .mariadb import RepositorioMariaDB
from .sqlite import CAMPOS, persistible


class CentralMariaDB(RepositorioMariaDB):
    def preparar(self):
        with self._conexion() as db, db.cursor() as cursor:
            cursor.execute("""CREATE TABLE IF NOT EXISTS ALUMNOS (
                ID VARCHAR(10) PRIMARY KEY, NOMBRE VARCHAR(100) NOT NULL,
                APELLIDO VARCHAR(100), SEXO CHAR(1), PATRONUS VARCHAR(100), IMAGEN VARCHAR(500)
            ) CHARACTER SET utf8mb4""")
            for campo, tipo in {
                "ORIGEN": "VARCHAR(20) NOT NULL DEFAULT 'mariadb'",
                "TIPO": "VARCHAR(20) NOT NULL DEFAULT 'NUEVO'",
                "CASA": "VARCHAR(30) NULL",
                "VERSION": "BIGINT NOT NULL DEFAULT 1",
                "ACTIVO": "BOOLEAN NOT NULL DEFAULT TRUE",
                "ACTUALIZADO_EN": "VARCHAR(40) NOT NULL DEFAULT ''",
            }.items():
                cursor.execute(
                    f"ALTER TABLE ALUMNOS ADD COLUMN IF NOT EXISTS {campo} {tipo}"
                )
            cursor.execute("""CREATE TABLE IF NOT EXISTS PATRONUS_IMG (
                ID INT AUTO_INCREMENT PRIMARY KEY, NOM_ENG VARCHAR(100) NOT NULL UNIQUE,
                URL VARCHAR(500) NOT NULL) CHARACTER SET utf8mb4""")
            db.commit()

    @staticmethod
    def _leer(cursor, dni, bloquear=False):
        cursor.execute(
            f"SELECT {','.join(CAMPOS)} FROM ALUMNOS WHERE ID=%s"
            + (" FOR UPDATE" if bloquear else ""),
            (dni,),
        )
        fila = cursor.fetchone()
        return Alumno(**dict(zip(CAMPOS, fila))) if fila else None

    def obtener_central(self, dni):
        with self._conexion() as db, db.cursor() as cursor:
            return self._leer(cursor, dni)

    def listar_central(self):
        with self._conexion() as db, db.cursor() as cursor:
            cursor.execute(f"SELECT {','.join(CAMPOS)} FROM ALUMNOS ORDER BY ID")
            return [Alumno(**dict(zip(CAMPOS, f))) for f in cursor.fetchall()]

    @staticmethod
    def _escribir(cursor, alumno, insertar=False):
        if insertar:
            cursor.execute(
                f"INSERT INTO ALUMNOS ({','.join(CAMPOS)}) VALUES ({','.join('%s' for _ in CAMPOS)})",
                tuple(getattr(alumno, c) for c in CAMPOS),
            )
        else:
            cursor.execute(
                "UPDATE ALUMNOS SET "
                + ",".join(f"{c}=%s" for c in CAMPOS if c != "id")
                + " WHERE ID=%s",
                tuple(getattr(alumno, c) for c in CAMPOS if c != "id") + (alumno.id,),
            )

    def mutar(self, previo, nuevo):
        """Escritura optimista en la principal; relee triggers/defaults antes de confirmar."""
        with self._conexion() as db:
            try:
                with db.cursor() as cursor:
                    actual = self._leer(cursor, nuevo.id, bloquear=True)
                    if previo is None:
                        if actual is not None:
                            raise Conflicto("El DNI ya existe en MariaDB")
                    elif actual is None or persistible(actual) != persistible(previo):
                        raise Conflicto(
                            "El alumno ha cambiado en MariaDB; recarga la ficha"
                        )
                    self._escribir(cursor, nuevo, insertar=actual is None)
                    releido = self._leer(cursor, nuevo.id)
                try:
                    db.commit()
                except Exception as exc:
                    raise EscrituraIncierta(
                        "No se pudo confirmar la respuesta del commit de MariaDB"
                    ) from exc
                return releido
            except pymysql.err.IntegrityError as exc:
                db.rollback()
                if exc.args[0] == 1062:
                    raise Conflicto("El DNI ya existe en MariaDB") from exc
                raise
            except BaseException:
                with suppress(Exception):
                    db.rollback()
                raise

    def aplicar(self, alumno):
        """Refresca exclusivamente históricos; nunca altera nuevos desde una copia."""
        if alumno.tipo != "HISTORICO":
            raise ValueError("Los nuevos se escriben con mutar(), no mediante refresco")
        with self._conexion() as db:
            try:
                with db.cursor() as cursor:
                    actual = self._leer(cursor, alumno.id, bloquear=True)
                    if actual:
                        if actual.tipo != "HISTORICO" or actual.origen != alumno.origen:
                            raise Conflicto("El DNI pertenece a otra fuente")
                        if actual.version > alumno.version:
                            raise Conflicto(
                                "MariaDB contiene una versión posterior; requiere revisión"
                            )
                        if actual.version == alumno.version:
                            if persistible(actual) != persistible(alumno):
                                raise Conflicto(
                                    "Hay datos diferentes para la misma versión"
                                )
                            return
                    self._escribir(cursor, alumno, insertar=actual is None)
                db.commit()
            except BaseException:
                db.rollback()
                raise
