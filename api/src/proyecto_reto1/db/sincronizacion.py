"""Capa de sincronización entre MariaDB (central) y SQLite (respaldo).

Centraliza las operaciones sobre la base central para que la capa HTTP no
tenga que preocuparse del respaldo:

- **Lecturas**: si MariaDB no responde, se sirven desde el respaldo SQLite y
  la respuesta se marca con la cabecera `X-Data-Source: sqlite`.
- **Escrituras**: se aplican en MariaDB y se reflejan en SQLite. Si MariaDB
  falla, la operación se guarda en SQLite y se encola en `pending_sync` para
  que el worker de sincronización la reintente con backoff exponencial.
- Los conflictos de concurrencia optimista (`ConflictoVersion`) **no** se
  encolan: son decisiones del cliente y se propagan tal cual (409).
"""

import logging
from datetime import datetime

from .base import Alumno, AlumnoActualizacion, AlumnoEntrada, ConflictoVersion
from .mariadb import RepositorioMariaDB
from .sqlite_backup import RespaldoSQLite

logger = logging.getLogger(__name__)


class Sincronizador:
    """Orquesta MariaDB + SQLite para las operaciones de la API.

    Atributos:
        mariadb: Repositorio central de MariaDB (puede ser None si no está activo).
        respaldo: Módulo SQLite de respaldo.
    """

    def __init__(self, mariadb: RepositorioMariaDB | None, respaldo: RespaldoSQLite):
        """Guarda las referencias al repositorio central y al respaldo.

        Args:
            mariadb: Repositorio MariaDB o None si el motor no está activo.
            respaldo: Instancia del respaldo SQLite.
        """
        self.mariadb = mariadb
        self.respaldo = respaldo

    # ------------------------------------------------------------------ #
    #  Lecturas (con fallback a SQLite)                                   #
    # ------------------------------------------------------------------ #

    def listar(self, incluir_inactivos: bool = False) -> tuple[list[Alumno], bool]:
        """Lista los alumnos de la central.

        Returns:
            (alumnos, desde_respaldo): la segunda indica si la respuesta vino
            del respaldo SQLite porque MariaDB no respondió.
        """
        if self.mariadb is None:
            return self.respaldo.listar(incluir_inactivos), True
        try:
            return self.mariadb.listar(incluir_inactivos), False
        except Exception as error:
            logger.warning("MariaDB no responde en listar; se usa respaldo: %s", error)
            return self.respaldo.listar(incluir_inactivos), True

    def buscar_por_nombre(
        self, prefijo: str, incluir_inactivos: bool = False
    ) -> tuple[list[Alumno], bool]:
        """Busca por prefijo de nombre en la central (fallback a SQLite)."""
        if self.mariadb is None:
            return self.respaldo.buscar_por_nombre(prefijo, incluir_inactivos), True
        try:
            return self.mariadb.buscar_por_nombre(prefijo, incluir_inactivos), False
        except Exception as error:
            logger.warning("MariaDB no responde en buscar_por_nombre; usa respaldo: %s", error)
            return self.respaldo.buscar_por_nombre(prefijo, incluir_inactivos), True

    def obtener(self, dni: str, incluir_inactivos: bool = False) -> tuple[Alumno | None, bool]:
        """Busca un alumno por DNI en la central (fallback a SQLite)."""
        if self.mariadb is None:
            return self.respaldo.obtener(dni, incluir_inactivos), True
        try:
            return self.mariadb.obtener(dni, incluir_inactivos), False
        except Exception as error:
            logger.warning("MariaDB no responde en obtener(%s); usa respaldo: %s", dni, error)
            return self.respaldo.obtener(dni, incluir_inactivos), True

    # ------------------------------------------------------------------ #
    #  Escrituras (MariaDB + SQLite + cola pending_sync)                  #
    # ------------------------------------------------------------------ #

    def crear(self, datos: AlumnoEntrada) -> tuple[Alumno, bool]:
        """Da de alta un alumno en la central y lo refleja en el respaldo.

        Si MariaDB falla, se guarda en SQLite y se encola la operación.

        Returns:
            (alumno, pendiente): `pendiente` indica que la operación quedó
            encolada en `pending_sync` (y se responderá con X-Pending-Sync).
        """
        if self.mariadb is None:
            return self._crear_pendiente(datos, "El motor 'mariadb' no está activo (DB_ENGINES)")
        try:
            alumno = self.mariadb.crear(datos)
        except Exception as error:
            from .base import Conflicto

            if isinstance(error, Conflicto):
                raise
            return self._crear_pendiente(datos, str(error))
        self.respaldo.guardar_alta(datos, version=alumno.version)
        return alumno, False

    def _crear_pendiente(self, datos: AlumnoEntrada, error: str) -> tuple[Alumno, bool]:
        """Guarda el alta en SQLite y la encola para reintentar en MariaDB."""
        logger.warning("Alta de '%s' encolada en pending_sync: %s", datos.id, error)
        self.respaldo.guardar_alta(datos, version=1)
        self.respaldo.encolar("crear", datos.model_dump(), error)
        return Alumno(**datos.model_dump(), casa="Hogwarts", activo=True, version=1), True

    def actualizar(
        self, dni: str, datos: AlumnoActualizacion, version: int | None
    ) -> tuple[Alumno | None, bool]:
        """Actualiza un alumno en la central (concurrencia optimista).

        Args:
            dni: DNI del alumno.
            datos: Campos a modificar.
            version: Versión leída por el cliente (optimistic locking).

        Returns:
            (alumno, pendiente). `alumno` es None si el DNI no existe.

        Raises:
            ConflictoVersion: Si la versión no coincide (no se encola).
        """
        if self.mariadb is None:
            return self._actualizar_pendiente(dni, datos, version, "motor 'mariadb' no activo")
        try:
            alumno = self.mariadb.actualizar(dni, datos, version)
        except ConflictoVersion:
            raise
        except Exception as error:
            return self._actualizar_pendiente(dni, datos, version, str(error))
        if alumno is None:
            return None, False
        cambios = datos.model_dump(exclude_unset=True, exclude={"version"})
        self.respaldo.guardar_cambios(dni, cambios, alumno.version)
        return alumno, False

    def _actualizar_pendiente(
        self, dni: str, datos: AlumnoActualizacion, version: int | None, error: str
    ) -> tuple[Alumno | None, bool]:
        """Aplica la edición al respaldo y la encola para reintentar."""
        existente = self.respaldo.obtener(dni, incluir_inactivos=True)
        if existente is None:
            return None, False
        logger.warning("Edición de '%s' encolada en pending_sync: %s", dni, error)
        cambios = datos.model_dump(exclude_unset=True, exclude={"version"})
        nueva_version = (version or existente.version) + 1
        self.respaldo.guardar_cambios(dni, cambios, nueva_version)
        self.respaldo.encolar(
            "actualizar",
            {"id": dni, "campos": cambios, "version": nueva_version},
            error,
        )
        actualizado = self.respaldo.obtener(dni, incluir_inactivos=True)
        return actualizado, True

    def eliminar(self, dni: str, version: int | None) -> tuple[bool, bool]:
        """Baja lógica de un alumno en la central (borrado lógico, no físico).

        Args:
            dni: DNI del alumno.
            version: Versión leída por el cliente (optimistic locking).

        Returns:
            (existe, pendiente). `existe` es False si el DNI no existe.

        Raises:
            ConflictoVersion: Si la versión no coincide (no se encola).
        """
        if self.mariadb is None:
            return self._eliminar_pendiente(dni, version, "motor 'mariadb' no activo")
        try:
            existe = self.mariadb.eliminar(dni, version)
        except ConflictoVersion:
            raise
        except Exception as error:
            return self._eliminar_pendiente(dni, version, str(error))
        if existe:
            alumno = self.respaldo.obtener(dni, incluir_inactivos=True)
            self.respaldo.guardar_baja(dni, (version or (alumno.version if alumno else 1)) + 1)
        return existe, False

    def _eliminar_pendiente(
        self, dni: str, version: int | None, error: str
    ) -> tuple[bool, bool]:
        """Aplica la baja al respaldo y la encola para reintentar."""
        existente = self.respaldo.obtener(dni, incluir_inactivos=True)
        if existente is None:
            return False, False
        logger.warning("Baja de '%s' encolada en pending_sync: %s", dni, error)
        nueva_version = (version or existente.version) + 1
        self.respaldo.guardar_baja(dni, nueva_version)
        self.respaldo.encolar("eliminar", {"id": dni, "version": nueva_version}, error)
        return True, True
