"""Repositorio de alumnos para Oracle (Ravenclaw).

Usa la librería nativa `oracledb` (funciona sin instalar el Oracle Client).
Los parámetros se pasan con notación `:nombre`. Cada operación abre y
cierra una conexión.
"""

from contextlib import contextmanager

import oracledb

from .base import (
    COLUMNAS_ALUMNO_SQL,
    Alumno,
    AlumnoActualizacion,
    ConflictoVersion,
    campos_a_actualizar,
    fila_a_alumno,
)


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
        # Se detecta bajo demanda si ALUMNOS tiene las columnas v2
        # (ACTIVO / VERSION) para habilitar borrado lógico y concurrencia.
        self._soporte_v2: bool | None = None

    def _columnas_v2(self, conexion, cursor) -> bool:
        """Detecta una sola vez si ALUMNOS tiene las columnas ACTIVO y VERSION.

        Si la consulta falla (esquema clásico), se descarta la transacción y
        se devuelve False para usar el comportamiento histórico.

        Args:
            conexion: Conexión Oracle activa.
            cursor: Cursor de esa conexión.
        """
        if self._soporte_v2 is not None:
            return self._soporte_v2
        try:
            cursor.execute("SELECT ACTIVO, VERSION FROM ALUMNOS WHERE 1 = 0")
            cursor.fetchall()
            self._soporte_v2 = True
        except Exception:
            self._soporte_v2 = False
            try:
                conexion.rollback()
            except Exception:
                pass
        return self._soporte_v2

    @contextmanager
    def _conexion(self):
        """Abre una conexión Oracle y la cierra siempre al salir del bloque.

        Uso:
            with self._conexion() as conexion:
                ...
        """
        with oracledb.connect(
            user=self.usuario,
            password=self.password,
            dsn=self.dsn,
            tcp_connect_timeout=5,
        ) as conexion:
            yield conexion

    # ------------------------------------------------------------------ #
    #  CRUD de alumnos                                                    #
    # ------------------------------------------------------------------ #

    def listar(self, incluir_inactivos: bool = False) -> list[Alumno]:
        """Devuelve todos los alumnos de la tabla ALUMNOS.

        `incluir_inactivos` se acepta por contrato; con el esquema clásico no
        filtra nada (sin columna ACTIVO).
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS")
                filas = cursor.fetchall()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def buscar_por_nombre(self, prefijo: str, incluir_inactivos: bool = False) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo (sin distinguir mayúsculas).

        Args:
            prefijo: Texto inicial del nombre a buscar.
            incluir_inactivos: Sin efecto con el esquema clásico.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS "
                    "WHERE UPPER(NOMBRE) LIKE UPPER(:prefijo) ORDER BY NOMBRE",
                    {"prefijo": prefijo + "%"},
                )
                filas = cursor.fetchall()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def obtener(self, id_alumno: str, incluir_inactivos: bool = False) -> Alumno | None:
        """Busca un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a buscar.
            incluir_inactivos: Sin efecto con el esquema clásico.

        Returns:
            Alumno si existe, None si no.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS WHERE ID = :id",
                    {"id": id_alumno},
                )
                fila = cursor.fetchone()
        if fila is None:
            return None
        return fila_a_alumno(fila, self.casa)

    def actualizar(
        self,
        id_alumno: str,
        datos: AlumnoActualizacion,
        version: int | None = None,
    ) -> Alumno | None:
        """Actualiza los campos enviados de un alumno existente.

        Si el esquema soporta la columna VERSION, aplica control de
        concurrencia optimista (`WHERE ID = :id AND VERSION = :version`).

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
            return self.obtener(id_alumno)
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
                if cursor.fetchone() is None:
                    return None
                asignacion = ", ".join(f"{nombre} = :{nombre.lower()}" for nombre in columnas)
                parametros = {nombre.lower(): valor for nombre, valor in columnas.items()}
                parametros["id"] = id_alumno
                if version is not None and self._columnas_v2(conexion, cursor):
                    cursor.execute("SELECT VERSION FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
                    fila = cursor.fetchone()
                    actual = int(fila[0]) if fila else None
                    if actual != version:
                        raise ConflictoVersion(
                            f"Conflicto de versiones: la versión actual es {actual}",
                            version_actual=actual,
                        )
                    cursor.execute(
                        f"UPDATE ALUMNOS SET {asignacion}, VERSION = VERSION + 1 WHERE ID = :id",
                        parametros,
                    )
                else:
                    cursor.execute(f"UPDATE ALUMNOS SET {asignacion} WHERE ID = :id", parametros)
            conexion.commit()
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str, version: int | None = None) -> bool:
        """Elimina un alumno por DNI.

        Con el esquema v2 realiza borrado lógico (`ACTIVO = 0`); con el
        esquema clásico mantiene el DELETE histórico.

        Args:
            id_alumno: DNI del alumno a eliminar.
            version: Versión leída por el cliente (optimistic locking).

        Returns:
            True si se eliminó, False si no existía.

        Raises:
            ConflictoVersion: Si `version` no coincide con la actual.
        """
        with self._conexion() as conexion:
            with conexion.cursor() as cursor:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
                if cursor.fetchone() is None:
                    return False
                if self._columnas_v2(conexion, cursor):
                    if version is not None:
                        cursor.execute(
                            "SELECT VERSION FROM ALUMNOS WHERE ID = :id", {"id": id_alumno}
                        )
                        fila = cursor.fetchone()
                        actual = int(fila[0]) if fila else None
                        if actual != version:
                            raise ConflictoVersion(
                                f"Conflicto de versiones: la versión actual es {actual}",
                                version_actual=actual,
                            )
                    cursor.execute(
                        "UPDATE ALUMNOS SET ACTIVO = 0, FECHA_BAJA = CURRENT_TIMESTAMP, "
                        "VERSION = VERSION + 1 WHERE ID = :id",
                        {"id": id_alumno},
                    )
                else:
                    cursor.execute("DELETE FROM ALUMNOS WHERE ID = :id", {"id": id_alumno})
            conexion.commit()
        return True
