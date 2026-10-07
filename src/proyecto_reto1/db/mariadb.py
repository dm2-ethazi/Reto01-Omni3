import pymysql

from .base import Alumno, AlumnoEntrada, Conflicto, texto


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
        return Alumno(**datos.model_dump(), casa=self.casa)
