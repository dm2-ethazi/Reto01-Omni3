import oracledb

from .base import Alumno, texto


class RepositorioOracle:
    casa = "Ravenclaw"

    def __init__(self, host: str, puerto: int, servicio: str, usuario: str, password: str):
        self.dsn = f"{host}:{puerto}/{servicio}"
        self.usuario = usuario
        self.password = password

    def listar(self) -> list[Alumno]:
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS")
                filas = cursor.fetchall()
        return [
            Alumno(
                id=texto(fila[0]) or "",
                nombre=texto(fila[1]) or "",
                apellido=texto(fila[2]),
                sexo=texto(fila[3]),
                patronus=texto(fila[4]),
                casa=self.casa
            )
            for fila in filas
        ]

    def obtener(self, id_alumno: str) -> Alumno | None:
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS WHERE ID = :id",
                    {"id": id_alumno},
                )
                fila = cursor.fetchone()
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
