import pymysql

from .base import Alumno, AlumnoActualizacion, AlumnoEntrada, Conflicto, campos_a_actualizar, texto


class RepositorioMariaDB:
    casa = "Hogwarts"

    def __init__(self, host: str, puerto: int, base: str, usuario: str, password: str):
        self.host = host
        self.puerto = puerto
        self.base = base
        self.usuario = usuario
        self.password = password

    def _conectar(self):
        return pymysql.connect(
            host=self.host,
            port=self.puerto,
            user=self.usuario,
            password=self.password,
            database=self.base,
            charset="utf8mb4",
            connect_timeout=5,
        )

    def buscar_patronus_url(self, patronus: str) -> str | None:
        """Busca en PATRONUS_IMG el URL de un patronus por nombre (en cualquier idioma)."""
        if not patronus:
            return None
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT URL FROM PATRONUS_IMG "
                    "WHERE NOM_ESP = %s OR NOM_ENG = %s OR NOM_EUS = %s LIMIT 1",
                    (patronus, patronus, patronus),
                )
                fila = cursor.fetchone()
        finally:
            conexion.close()
        return texto(fila[0]) if fila else None

    def validar_patronus(self, patronus: str, lang: str) -> bool:
        """Comprueba que un patronus existe en PATRONUS_IMG en el idioma indicado."""
        if not patronus:
            return False
        columna = {"es": "NOM_ESP", "en": "NOM_ENG", "eu": "NOM_EUS"}[lang]
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT 1 FROM PATRONUS_IMG WHERE {columna} = %s LIMIT 1",
                    (patronus,),
                )
                existe = cursor.fetchone() is not None
        finally:
            conexion.close()
        return existe

    def listar(self) -> list[Alumno]:
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS")
                filas = cursor.fetchall()
        finally:
            conexion.close()
        return [
            Alumno(
                id=texto(fila[0]) or "",
                nombre=texto(fila[1]) or "",
                apellido=texto(fila[2]),
                sexo=texto(fila[3]),
                patronus=texto(fila[4]),
                casa=self.casa,
            )
            for fila in filas
        ]

    def obtener(self, id_alumno: str) -> Alumno | None:
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS WHERE ID = %s",
                    (id_alumno,),
                )
                fila = cursor.fetchone()
        finally:
            conexion.close()
        if fila is None:
            return None
        return Alumno(
            id=texto(fila[0]) or "",
            nombre=texto(fila[1]) or "",
            apellido=texto(fila[2]),
            sexo=texto(fila[3]),
            patronus=texto(fila[4]),
            casa=self.casa,
        )

    def crear(self, datos: AlumnoEntrada) -> Alumno:
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "INSERT INTO ALUMNOS (ID, NOMBRE, APELLIDO, SEXO, PATRONUS) VALUES (%s, %s, %s, %s, %s)",
                    (datos.id, datos.nombre, datos.apellido, datos.sexo, datos.patronus),
                )
            conexion.commit()
        except pymysql.err.IntegrityError as error:
            conexion.rollback()
            if error.args and error.args[0] == 1062:
                raise Conflicto(f"Ya existe un alumno con id '{datos.id}'") from error
            raise
        finally:
            conexion.close()
        return Alumno(**datos.model_dump(exclude={"patronus_lang"}), casa=self.casa)

    def actualizar(self, id_alumno: str, datos: AlumnoActualizacion) -> Alumno | None:
        columnas = campos_a_actualizar(datos)
        if not columnas:
            return self.obtener(id_alumno)
        asignacion = ", ".join(f"{nombre} = %s" for nombre in columnas)
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                existe = cursor.fetchone() is not None
                if existe:
                    cursor.execute(
                        f"UPDATE ALUMNOS SET {asignacion} WHERE ID = %s",
                        tuple(columnas.values()) + (id_alumno,),
                    )
            if existe:
                conexion.commit()
        finally:
            conexion.close()
        if not existe:
            return None
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str) -> bool:
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                existe = cursor.fetchone() is not None
                if existe:
                    cursor.execute("DELETE FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                    conexion.commit()
        finally:
            conexion.close()
        return existe
