"""Repositorio de alumnos y patronus para MariaDB (Hogwarts).

Usa la librería nativa `pymysql`. Es el repositorio central: las altas de
alumnos (POST) se realizan siempre aquí, y también gestiona la tabla
`PATRONUS_IMG` (catálogo de patronus con imagen en 3 idiomas).
"""

import pymysql

from .base import Alumno, AlumnoActualizacion, AlumnoEntrada, Conflicto, Patronus, campos_a_actualizar, texto


class RepositorioMariaDB:
    """Repositorio de alumnos para MariaDB (base r01_central).

    Además de las operaciones CRUD sobre ALUMNOS, incluye métodos para
    consultar y validar patronus contra la tabla PATRONUS_IMG.

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

    def _conectar(self):
        """Abre y devuelve una conexión pymysql con charset utf8mb4."""
        return pymysql.connect(
            host=self.host,
            port=self.puerto,
            user=self.usuario,
            password=self.password,
            database=self.base,
            charset="utf8mb4",
            connect_timeout=5,
        )

    # ------------------------------------------------------------------ #
    #                        PATRONUS_IMG                                 #
    # ------------------------------------------------------------------ #

    def buscar_patronus_url(self, patronus: str) -> str | None:
        """Busca en PATRONUS_IMG el URL de la imagen de un patronus.

        Consulta las 3 columnas de nombre (NOM_ESP, NOM_ENG, NOM_EUS)
        para encontrar coincidencia en cualquier idioma.

        Args:
            patronus: Nombre del patronus a buscar.

        Returns:
            URL de la imagen si se encontró, None si no.
        """
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
        """Comprueba que un patronus existe en PATRONUS_IMG en el idioma indicado.

        Args:
            patronus: Nombre del patronus a validar.
            lang: Idioma ('es', 'en' o 'eu'). Determina la columna a consultar.

        Returns:
            True si el patronus existe en la columna del idioma indicado.
        """
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

    def listar_patronus(self) -> list[Patronus]:
        """Devuelve todos los patronus del catálogo PATRONUS_IMG.

        Returns:
            Lista de objetos Patronus con nombre en 3 idiomas y URL de imagen.
        """
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT NOM_ESP, NOM_ENG, NOM_EUS, URL FROM PATRONUS_IMG")
                filas = cursor.fetchall()
        finally:
            conexion.close()
        return [
            Patronus(
                nom_esp=texto(f[0]) or "",
                nom_eng=texto(f[1]) or "",
                nom_eus=texto(f[2]) or "",
                url=texto(f[3]) or "",
            )
            for f in filas
        ]

    # ------------------------------------------------------------------ #
    #                           ALUMNOS                                   #
    # ------------------------------------------------------------------ #

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la tabla ALUMNOS."""
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS")
                filas = cursor.fetchall()
        finally:
            conexion.close()
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
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS "
                    "WHERE UPPER(NOMBRE) LIKE UPPER(%s) ORDER BY NOMBRE",
                    (prefijo + "%",),
                )
                filas = cursor.fetchall()
        finally:
            conexion.close()
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
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN FROM ALUMNOS WHERE ID = %s",
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
            imagen=texto(fila[5]),
            casa=self.casa,
        )

    def crear(self, datos: AlumnoEntrada) -> Alumno:
        """Inserta un alumno nuevo en la tabla ALUMNOS.

        Args:
            datos: Datos del alumno a crear (AlumnoEntrada ya validado).

        Returns:
            Alumno creado.

        Raises:
            Conflicto: Si ya existe un alumno con ese DNI (error 1062 de MySQL).
        """
        conexion = self._conectar()
        try:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "INSERT INTO ALUMNOS (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN) "
                    "VALUES (%s, %s, %s, %s, %s, %s)",
                    (datos.id, datos.nombre, datos.apellido, datos.sexo, datos.patronus, datos.imagen),
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
        """Elimina un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a eliminar.

        Returns:
            True si se eliminó, False si no existía.
        """
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
