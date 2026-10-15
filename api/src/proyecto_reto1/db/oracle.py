"""Repositorio de alumnos para Oracle (Ravenclaw).

Usa la librería nativa `oracledb` (sin Oracle Client). Los parámetros se
pasan con notación `:nombre`. Cada operación abre y cierra una conexión.
"""

import oracledb

from .base import Alumno, AlumnoActualizacion, campos_a_actualizar, texto


class RepositorioOracle:
    """Repositorio de alumnos para la base de datos Oracle de Ravenclaw.

    Implementa el Protocolo `RepositorioAlumnos` usando `oracledb`.

    Atributos:
        casa: Etiqueta "Ravenclaw" asignada a esta fuente.
        dsn: String DSN de conexión (host:puerto/servicio).
        usuario: Usuario de Oracle.
        password: Contraseña del usuario.
    """

    casa = "Ravenclaw"

    def __init__(self, host: str, puerto: int, servicio: str, usuario: str, password: str):
        """Inicializa el repositorio.

        Args:
            host: Host del servidor Oracle.
            puerto: Puerto del listener (por defecto 1521).
            servicio: Nombre del servicio/PDB de Oracle (ej. FREEPDB1).
            usuario: Usuario de Oracle.
            password: Contraseña del usuario.
        """
        self.dsn = f"{host}:{puerto}/{servicio}"
        self.usuario = usuario
        self.password = password

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la tabla ALUMNOS."""
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS")
                filas = cursor.fetchall()
        return [
            Alumno(
                id=texto(f[0]) or "",
                nombre=texto(f[1]) or "",
                apellido=texto(f[2]),
                sexo=texto(f[3]),
                patronus=texto(f[4]),
                imagen=texto(f[5]),
                casa=self.casa,
            )
            for f in filas
        ]

    def buscar_por_nombre(self, prefijo: str) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo (case-insensitive).

        Args:
            prefijo: Texto inicial del nombre a buscar.
        """
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS "
                    "WHERE UPPER(NOMBRE) LIKE UPPER(:prefijo) ORDER BY NOMBRE",
                    {"prefijo": prefijo + "%"},
                )
                filas = cursor.fetchall()
        return [
            Alumno(
                id=texto(f[0]) or "",
                nombre=texto(f[1]) or "",
                apellido=texto(f[2]),
                sexo=texto(f[3]),
                patronus=texto(f[4]),
                imagen=texto(f[5]),
                casa=self.casa,
            )
            for f in filas
        ]

    def obtener(self, id_alumno: str) -> Alumno | None:
        """Busca un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a buscar.

        Returns:
            Alumno si existe, None si no.
        """
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS WHERE ID = :id",
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
            imagen=texto(fila[5]),
            casa=self.casa,
        )

    def actualizar(self, id_alumno: str, datos: AlumnoActualizacion) -> Alumno | None:
        """Actualiza los campos enviados de un alumno existente.

        Args:
            id_alumno: DNI del alumno a actualizar.
            datos: Campos a modificar (actualización parcial).

        Returns:
            Alumno actualizado si existía, None si no.
        """
        columnas = campos_a_actualizar(datos)
        if not columnas:
            return self.obtener(id_alumno)
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
                if cursor.fetchone() is None:
                    return None
                asignacion = ", ".join(f"{nombre} = :{nombre.lower()}" for nombre in columnas)
                parametros = {nombre.lower(): valor for nombre, valor in columnas.items()}
                parametros["id"] = id_alumno
                cursor.execute(f"UPDATE ALUMNOS SET {asignacion} WHERE ID = :id", parametros)
            conexion.commit()
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str) -> bool:
        """Elimina un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a eliminar.

        Returns:
            True si se eliminó, False si no existía.
        """
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
                if cursor.fetchone() is None:
                    return False
                cursor.execute("DELETE FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
            conexion.commit()
        return True
