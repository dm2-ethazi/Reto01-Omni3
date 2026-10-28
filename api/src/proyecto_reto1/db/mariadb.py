"""Repositorio de alumnos y patronus para MariaDB (Hogwarts).

Usa la librería nativa `pymysql`. Es el repositorio **central** del proyecto:

- Las altas de alumnos (POST /alumnos) se realizan siempre aquí.
- Gestiona la tabla `PATRONUS_IMG` (catálogo de patronus con nombre en
  inglés e imagen), que solo existe en esta base de datos.
- Aplica el **borrado lógico** (`ACTIVO` / `FECHA_BAJA`) y el **control de
  concurrencia optimista** (`VERSION`), requisitos del reto R01.
"""

from contextlib import contextmanager
from datetime import datetime

import pymysql

from .base import (
    COLUMNAS_ALUMNO_SQL_V2,
    Alumno,
    AlumnoActualizacion,
    AlumnoEntrada,
    Conflicto,
    ConflictoVersion,
    Patronus,
    campos_a_actualizar,
    fila_a_alumno_v2,
    texto,
)


class RepositorioMariaDB:
    """Repositorio de alumnos para la base de datos MariaDB (r01_central).

    Además del CRUD sobre ALUMNOS, incluye los métodos del catálogo
    PATRONUS_IMG (`listar_patronus`, `buscar_patronus_url`, `validar_patronus`)
    y un `ping()` para el endpoint de estado.

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
            read_timeout=10,
            write_timeout=10,
        )
        try:
            yield conexion
        finally:
            conexion.close()

    # ------------------------------------------------------------------ #
    #  Salud                                                              #
    # ------------------------------------------------------------------ #

    def ping(self) -> bool:
        """Comprueba que el servidor MariaDB responde.

        Returns:
            True si la conexión y la consulta funcionan; False en caso contrario.
        """
        try:
            with self._conexion() as conexion:
                with conexion.cursor() as cursor:
                    cursor.execute("SELECT 1")
                    cursor.fetchone()
            return True
        except Exception:
            return False

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

    def listar(self, incluir_inactivos: bool = False) -> list[Alumno]:
        """Devuelve los alumnos de la tabla ALUMNOS (activos por defecto).

        Args:
            incluir_inactivos: Si True, incluye los dados de baja lógicamente.
        """
        where = "" if incluir_inactivos else " WHERE ACTIVO = 1"
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(f"SELECT {COLUMNAS_ALUMNO_SQL_V2} FROM ALUMNOS{where}")
                filas = cursor.fetchall()
        return [fila_a_alumno_v2(f, self.casa) for f in filas]

    def buscar_por_nombre(self, prefijo: str, incluir_inactivos: bool = False) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo (sin distinguir mayúsculas).

        Args:
            prefijo: Texto inicial del nombre a buscar.
            incluir_inactivos: Si True, incluye los dados de baja lógicamente.
        """
        where = "" if incluir_inactivos else " AND ACTIVO = 1"
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL_V2} FROM ALUMNOS "
                    f"WHERE UPPER(NOMBRE) LIKE UPPER(%s){where} ORDER BY NOMBRE",
                    (prefijo + "%",),
                )
                filas = cursor.fetchall()
        return [fila_a_alumno_v2(f, self.casa) for f in filas]

    def obtener(self, id_alumno: str, incluir_inactivos: bool = False) -> Alumno | None:
        """Busca un alumno por DNI (activos por defecto, borrado lógico).

        Args:
            id_alumno: DNI del alumno a buscar.
            incluir_inactivos: Si True, también encuentra alumnos de baja.

        Returns:
            Alumno si existe (y está activo, salvo que se pida lo contrario),
            None si no.
        """
        where = "WHERE ID = %s" if incluir_inactivos else "WHERE ID = %s AND ACTIVO = 1"
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL_V2} FROM ALUMNOS {where}",
                    (id_alumno,),
                )
                fila = cursor.fetchone()
        if fila is None:
            return None
        return fila_a_alumno_v2(fila, self.casa)

    def crear(self, datos: AlumnoEntrada) -> Alumno:
        """Inserta un alumno nuevo en la tabla ALUMNOS (activo, versión 1).

        Args:
            datos: Datos del alumno a crear (AlumnoEntrada ya validado; el
                patronus es obligatorio).

        Returns:
            Alumno creado.

        Raises:
            Conflicto: Si ya existe un alumno con ese DNI (error 1062 de MySQL).
        """
        with self._conexion() as conexion:
            try:
                with conexion.cursor() as cursor:
                    cursor.execute(
                        "INSERT INTO ALUMNOS (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN, "
                        "ACTIVO, FECHA_BAJA, VERSION, UPDATED_AT) "
                        "VALUES (%s, %s, %s, %s, %s, %s, 1, NULL, 1, CURRENT_TIMESTAMP)",
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
        return Alumno(**datos.model_dump(), casa=self.casa, activo=True, version=1)

    def _version_actual(self, conexion, id_alumno: str) -> int | None:
        """Devuelve la versión vigente de un alumno o None si no existe."""
        with conexion.cursor() as cursor:
            cursor.execute("SELECT VERSION FROM ALUMNOS WHERE ID = %s", (id_alumno,))
            fila = cursor.fetchone()
        return int(fila[0]) if fila else None

    def actualizar(
        self,
        id_alumno: str,
        datos: AlumnoActualizacion,
        version: int | None = None,
    ) -> Alumno | None:
        """Actualiza los campos enviados de un alumno existente.

        Aplica control de concurrencia optimista: si se recibe `version`, el
        `UPDATE` se limita a `WHERE ID = ? AND VERSION = ?` y al acierto se
        incrementa la versión (`VERSION = VERSION + 1`).

        Args:
            id_alumno: DNI del alumno a actualizar.
            datos: Campos a modificar (actualización parcial).
            version: Versión leída por el cliente (optimistic locking).

        Returns:
            Alumno actualizado si existía, None si no.

        Raises:
            ConflictoVersion: Si `version` no coincide con la actual.
        """
        columnas = campos_a_actualizar(datos)
        if not columnas:
            return self.obtener(id_alumno, incluir_inactivos=True)
        asignacion = ", ".join(f"{nombre} = %s" for nombre in columnas)
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                if version is not None:
                    cursor.execute(
                        f"UPDATE ALUMNOS SET {asignacion}, VERSION = VERSION + 1, "
                        "UPDATED_AT = CURRENT_TIMESTAMP "
                        "WHERE ID = %s AND VERSION = %s AND ACTIVO = 1",
                        tuple(columnas.values()) + (id_alumno, version),
                    )
                    afectados = cursor.rowcount
                    if afectados == 0:
                        conexion.rollback()
                        actual = self._version_actual(conexion, id_alumno)
                        if actual is None:
                            return None
                        raise ConflictoVersion(
                            f"Conflicto de versiones: la versión actual es {actual}",
                            version_actual=actual,
                        )
                else:
                    cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = %s", (id_alumno,))
                    if cursor.fetchone() is None:
                        return None
                    cursor.execute(
                        f"UPDATE ALUMNOS SET {asignacion}, VERSION = VERSION + 1, "
                        "UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = %s",
                        tuple(columnas.values()) + (id_alumno,),
                    )
            conexion.commit()
        return self.obtener(id_alumno, incluir_inactivos=True)

    def eliminar(self, id_alumno: str, version: int | None = None) -> bool:
        """Baja lógica de un alumno por DNI: `ACTIVO = 0` y `FECHA_BAJA = NOW()`.

        El registro **no se borra** de la base de datos. Si el alumno ya estaba
        dado de baja, la operación es idempotente (no vuelve a modificar nada).

        Args:
            id_alumno: DNI del alumno a dar de baja.
            version: Versión leída por el cliente (optimistic locking).

        Returns:
            True si el alumno existía (activo o ya inactivo), False si no existe.

        Raises:
            ConflictoVersion: Si `version` no coincide con la actual.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "SELECT ID, ACTIVO FROM ALUMNOS WHERE ID = %s", (id_alumno,)
                )
                fila = cursor.fetchone()
                if fila is None:
                    return False
                if fila[1]:  # Solo actúa si está activo; si no, idempotente.
                    if version is not None:
                        cursor.execute(
                            "UPDATE ALUMNOS SET ACTIVO = 0, FECHA_BAJA = NOW(), "
                            "VERSION = VERSION + 1, UPDATED_AT = CURRENT_TIMESTAMP "
                            "WHERE ID = %s AND VERSION = %s AND ACTIVO = 1",
                            (id_alumno, version),
                        )
                        if cursor.rowcount == 0:
                            conexion.rollback()
                            actual = self._version_actual(conexion, id_alumno)
                            if actual is None:
                                return False
                            raise ConflictoVersion(
                                f"Conflicto de versiones: la versión actual es {actual}",
                                version_actual=actual,
                            )
                    else:
                        cursor.execute(
                            "UPDATE ALUMNOS SET ACTIVO = 0, FECHA_BAJA = NOW(), "
                            "VERSION = VERSION + 1, UPDATED_AT = CURRENT_TIMESTAMP "
                            "WHERE ID = %s AND ACTIVO = 1",
                            (id_alumno,),
                        )
                conexion.commit()
        return True

    def restaurar(self, datos: AlumnoEntrada, version: int | None = None) -> Alumno:
        """Reaplica un alta en MariaDB (usado por el worker de sincronización).

        Args:
            datos: Datos originales del alta.
            version: Versión con la que debe cuadrar el registro (None = sin control).

        Returns:
            El alumno tal y como quedó registrado.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "INSERT INTO ALUMNOS (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN, "
                    "ACTIVO, FECHA_BAJA, VERSION, UPDATED_AT) "
                    "VALUES (%s, %s, %s, %s, %s, %s, 1, NULL, %s, CURRENT_TIMESTAMP) "
                    "ON DUPLICATE KEY UPDATE "
                    "NOMBRE = VALUES(NOMBRE), APELLIDO = VALUES(APELLIDO), "
                    "SEXO = VALUES(SEXO), PATRONUS = VALUES(PATRONUS), "
                    "IMAGEN = VALUES(IMAGEN), ACTIVO = 1, FECHA_BAJA = NULL, "
                    "VERSION = GREATEST(VERSION, VALUES(VERSION)), "
                    "UPDATED_AT = CURRENT_TIMESTAMP",
                    (
                        datos.id,
                        datos.nombre,
                        datos.apellido,
                        datos.sexo,
                        datos.patronus,
                        datos.imagen,
                        version or 1,
                    ),
                )
            conexion.commit()
        return self.obtener(datos.id, incluir_inactivos=True) or Alumno(
            **datos.model_dump(), casa=self.casa, activo=True, version=version or 1
        )

    def aplicar_baja(self, id_alumno: str, version: int | None, fecha_baja: datetime | None):
        """Reaplica una baja lógica en MariaDB (usado por el worker de sincronización).

        Args:
            id_alumno: DNI del alumno.
            version: Nueva versión tras la baja.
            fecha_baja: Fecha de la baja original (si no llega, usa NOW()).
        """
        fecha = fecha_baja or datetime.now()
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    "UPDATE ALUMNOS SET ACTIVO = 0, FECHA_BAJA = %s, "
                    "VERSION = %s, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = %s",
                    (fecha, version or 1, id_alumno),
                )
            conexion.commit()

    def aplicar_actualizacion(self, datos: dict, version: int | None):
        """Reaplica una edición en MariaDB (usado por el worker de sincronización).

        Args:
            datos: Dict con los campos a aplicar (nombre, apellido, ...).
            version: Versión resultante tras la edición.
        """
        columnas = {k.upper(): v for k, v in datos.items() if k in COLUMNAS_ALUMNO_MAP}
        if not columnas:
            return
        asignacion = ", ".join(f"{nombre} = %s" for nombre in columnas)
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"UPDATE ALUMNOS SET {asignacion}, VERSION = %s, "
                    "UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = %s",
                    tuple(columnas.values()) + (version or 1, datos["id"]),
                )
            conexion.commit()


# Mapeo campo->columna usado por aplicar_actualizacion (evita import circular).
COLUMNAS_ALUMNO_MAP = {"nombre", "apellido", "sexo", "patronus", "imagen"}
