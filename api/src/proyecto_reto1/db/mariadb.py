"""Repositorio de alumnos y patronus para MariaDB (Hogwarts).

Usa la librería nativa `pymysql`. Es el repositorio **central** del proyecto:

- Las altas de alumnos (POST /alumnos) se realizan siempre aquí.
- Gestiona la tabla `PATRONUS_IMG` (catálogo de patronus con nombre en
  inglés e imagen), que solo existe en esta base de datos.
"""

from contextlib import contextmanager

import pymysql

from .base import (
    COLUMNAS_ALUMNO_SQL,
    Alumno,
    AlumnoActualizacion,
    AlumnoEntrada,
    Conflicto,
    Patronus,
    campos_a_actualizar,
    fila_a_alumno,
    texto,
)


class RepositorioMariaDB:
    """Repositorio de alumnos para la base de datos MariaDB (r01_central).

    Además del CRUD sobre ALUMNOS, incluye los métodos del catálogo
    PATRONUS_IMG (`listar_patronus`, `buscar_patronus_url`, `validar_patronus`).

    Atributos:
        casa: Etiqueta "Hogwarts" asignada a esta fuente.
        host: Host del servidor MariaDB.
        puerto: Puerto de conexión.
        base: Nombre de la base de datos.
        usuario: Usuario de MariaDB.
        password: Contraseña del usuario.
    """

    casa = "Hogwarts"

    def __init__(self, host: str, puerto: int, base: str, usuario: str, password: str):
        """Inicializa el repositorio.

        Args:
            host: Host del servidor MariaDB.
            puerto: Puerto de conexión (por defecto 3307).
            base: Nombre de la base de datos (ej. r01_central).
            usuario: Usuario de MariaDB.
            password: Contraseña del usuario.
        """
        self.host = host
        self.puerto = puerto
        self.base = base
        self.usuario = usuario
        self.password = password

    @contextmanager
    def _conexion(self):
        """Abre una conexión pymysql (charset utf8mb4) y la cierra siempre.

        Uso:
            with self._conexion() as conexion:
                ...
        """
        conexion = pymysql.connect(
            host=self.host,
            port=self.puerto,
            user=self.usuario,
            password=self.password,
            database=self.base,
            charset="utf8mb4",
            connect_timeout=5,
        )
        try:
            yield conexion
        finally:
            conexion.close()

    # ------------------------------------------------------------------ #
    #  Catálogo PATRONUS_IMG                                              #
    # ------------------------------------------------------------------ #

    def buscar_patronus_url(self, patronus: str) -> str | None:
        """Busca en PATRONUS_IMG el URL de la imagen de un patronus por nombre.

        Args:
            patronus: Nombre del patronus a buscar (columna NOM_ENG).

        Returns:
            URL de la imagen si se encontró, None si no.
        """
        if not patronus:
            return None
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT URL FROM PATRONUS_IMG WHERE NOM_ENG = %s LIMIT 1",
                    (patronus,),
                )
                fila = cursor.fetchone()
        return texto(fila[0]) if fila else None

    def validar_patronus(self, patronus: str) -> bool:
        """Comprueba que un patronus existe en PATRONUS_IMG.

        Args:
            patronus: Nombre del patronus a validar (columna NOM_ENG).

        Returns:
            True si el patronus existe en la tabla.
        """
        if not patronus:
            return False
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT 1 FROM PATRONUS_IMG WHERE NOM_ENG = %s LIMIT 1",
                    (patronus,),
                )
                existe = cursor.fetchone() is not None
        return existe

    def listar_patronus(self) -> list[Patronus]:
        """Devuelve todos los patronus del catálogo PATRONUS_IMG.

        Returns:
            Lista de objetos Patronus con nombre en inglés y URL de imagen.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT NOM_ENG, URL FROM PATRONUS_IMG")
                filas = cursor.fetchall()
        return [
            Patronus(
                nom_eng=texto(f[0]) or "",
                url=texto(f[1]) or "",
            )
            for f in filas
        ]

    # ------------------------------------------------------------------ #
    #  CRUD de alumnos                                                    #
    # ------------------------------------------------------------------ #

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la tabla ALUMNOS."""
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS")
                filas = cursor.fetchall()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def buscar_por_nombre(self, prefijo: str) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo (sin distinguir mayúsculas).

        Args:
            prefijo: Texto inicial del nombre a buscar.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS "
                    "WHERE UPPER(NOMBRE) LIKE UPPER(%s) ORDER BY NOMBRE",
                    (prefijo + "%",),
                )
                filas = cursor.fetchall()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def obtener(self, id_alumno: str) -> Alumno | None:
        """Busca un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a buscar.

        Returns:
            Alumno si existe, None si no.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS WHERE ID = %s",
                    (id_alumno,),
                )
                fila = cursor.fetchone()
        if fila is None:
            return None
        return fila_a_alumno(fila, self.casa)

    def crear(self, datos: AlumnoEntrada) -> Alumno:
        """Inserta un alumno nuevo en la tabla ALUMNOS.

        Args:
            datos: Datos del alumno a crear (AlumnoEntrada ya validado).

        Returns:
            Alumno creado.

        Raises:
            Conflicto: Si ya existe un alumno con ese DNI (error 1062 de MySQL).
        """
        with self._conexion() as conexion:
            try:
                with conexion.cursor() as cursor:
                    cursor.execute(
                        "INSERT INTO ALUMNOS (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN) "
                        "VALUES (%s, %s, %s, %s, %s, %s)",
                        (
                            datos.id,
                            datos.nombre,
                            datos.apellido,
                            datos.sexo,
                            datos.patronus,
                            datos.imagen,
                        ),
                    )
                conexion.commit()
            except pymysql.err.IntegrityError as error:
                conexion.rollback()
                if error.args and error.args[0] == 1062:
                    raise Conflicto(f"Ya existe un alumno con id '{datos.id}'") from error
                raise
        return Alumno(**datos.model_dump(), casa=self.casa)

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
        asignacion = ", ".join(f"{nombre} = %s" for nombre in columnas)
        with self._conexion() as conexion:
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
        if not existe:
            return None
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str) -> bool:
        """Elimina un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a eliminar.

        Returns:
            True si se eliminó, False si no existía.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                existe = cursor.fetchone() is not None
                if existe:
                    cursor.execute("DELETE FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                    conexion.commit()
        return existe
