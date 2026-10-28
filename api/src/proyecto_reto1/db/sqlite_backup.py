"""Módulo de acceso a SQLite como base de respaldo central de la API.

SQLite respalda **únicamente los datos gestionados por la propia API**
(alumnos creados/editados/dados de baja aquí), no una réplica de las bases de
origen. El fichero es configurable con la variable de entorno `SQLITE_PATH`
(por defecto `data/respaldo.db`, dentro de la carpeta `api/`).

Contiene dos tablas:

- `alumnos`: esquema equivalente al de MariaDB, con `activo`, `fecha_baja`,
  `version` y `updated_at`.
- `pending_sync`: cola de operaciones pendientes de aplicar en MariaDB cuando
  éste falla, con reintentos, último error y estado.

Todas las consultas usan parámetros marcados (`?`); nunca se concatenan
valores del usuario en la sentencia SQL.
"""

import json
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime

from .base import Alumno, AlumnoEntrada

CASA_RESPALDO = "Hogwarts (respaldo SQLite)"

_ESQUEMA = """
CREATE TABLE IF NOT EXISTS alumnos (
    id TEXT NOT NULL PRIMARY KEY,
    nombre TEXT NOT NULL,
    apellido TEXT NULL,
    sexo TEXT NULL,
    patronus TEXT NULL,
    imagen TEXT NULL,
    activo INTEGER NOT NULL DEFAULT 1,
    fecha_baja TEXT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_sync (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operacion TEXT NOT NULL,
    payload TEXT NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 0,
    estado TEXT NOT NULL DEFAULT 'pendiente',
    ultimo_error TEXT NULL,
    proximo_intento TEXT NOT NULL,
    created_at TEXT NOT NULL
);
"""


class RespaldoSQLite:
    """Acceso al fichero SQLite de respaldo y a la cola `pending_sync`.

    Atributos:
        ruta: Ruta del fichero .db (debe estar en un volumen persistente).
        _lock: Cerrojo de proceso; sqlite3 no admite dos hilos con la misma
            conexión y el worker de sincronización comparte el fichero.
    """

    def __init__(self, ruta: str):
        """Crea el fichero y el esquema si no existen.

        Args:
            ruta: Ruta absoluta o relativa al fichero SQLite.
        """
        self.ruta = ruta
        self._lock = threading.Lock()
        directorio = ruta.rsplit("/", 1)[0].rsplit("\\", 1)[0]
        if directorio:
            import os

            os.makedirs(directorio, exist_ok=True)
        with self._conexion() as conexion:
            conexion.executescript(_ESQUEMA)
            conexion.commit()

    @contextmanager
    def _conexion(self):
        """Abre una conexión sqlite3 (fila como tupla) y la cierra siempre."""
        with self._lock:
            conexion = sqlite3.connect(self.ruta, timeout=10)
            try:
                yield conexion
            finally:
                conexion.close()

    # ------------------------------------------------------------------ #
    #  Salud y estado                                                     #
    # ------------------------------------------------------------------ #

    def ping(self) -> bool:
        """Comprueba que el fichero SQLite es consultable.

        Returns:
            True si la consulta básica funciona; False en caso contrario.
        """
        try:
            with self._conexion() as conexion:
                conexion.execute("SELECT COUNT(*) FROM alumnos").fetchone()
            return True
        except Exception:
            return False

    def contar_pendientes(self) -> int:
        """Cuenta las operaciones pendientes de sincronizar (pendientes + fallidas)."""
        with self._conexion() as conexion:
            fila = conexion.execute(
                "SELECT COUNT(*) FROM pending_sync WHERE estado IN ('pendiente', 'fallido')"
            ).fetchone()
        return int(fila[0])

    def contar_fallidas(self) -> int:
        """Cuenta las operaciones pendientes con fallo definitivo (agotó reintentos)."""
        with self._conexion() as conexion:
            fila = conexion.execute(
                "SELECT COUNT(*) FROM pending_sync WHERE estado = 'fallido'"
            ).fetchone()
        return int(fila[0])

    # ------------------------------------------------------------------ #
    #  Lecturas (respaldo de MariaDB)                                     #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _fila_a_alumno(fila) -> Alumno:
        """Convierte una fila de la tabla `alumnos` en un modelo Alumno."""
        return Alumno(
            id=fila[0],
            nombre=fila[1],
            apellido=fila[2],
            sexo=fila[3],
            patronus=fila[4],
            imagen=fila[5],
            casa=CASA_RESPALDO,
            activo=bool(fila[6]),
            fecha_baja=datetime.fromisoformat(fila[7]) if fila[7] else None,
            version=int(fila[8] or 1),
        )

    _COLUMNAS = "id, nombre, apellido, sexo, patronus, imagen, activo, fecha_baja, version"

    def listar(self, incluir_inactivos: bool = False) -> list[Alumno]:
        """Devuelve los alumnos del respaldo (activos por defecto)."""
        where = "" if incluir_inactivos else " WHERE activo = 1"
        with self._conexion() as conexion:
            filas = conexion.execute(
                f"SELECT {self._COLUMNAS} FROM alumnos{where} ORDER BY nombre"
            ).fetchall()
        return [self._fila_a_alumno(f) for f in filas]

    def buscar_por_nombre(self, prefijo: str, incluir_inactivos: bool = False) -> list[Alumno]:
        """Busca por prefijo de nombre en el respaldo (sin distinguir mayúsculas)."""
        where = "" if incluir_inactivos else " AND activo = 1"
        with self._conexion() as conexion:
            filas = conexion.execute(
                f"SELECT {self._COLUMNAS} FROM alumnos "
                f"WHERE UPPER(nombre) LIKE UPPER(?){where} ORDER BY nombre",
                (prefijo + "%",),
            ).fetchall()
        return [self._fila_a_alumno(f) for f in filas]

    def obtener(self, id_alumno: str, incluir_inactivos: bool = False) -> Alumno | None:
        """Busca un alumno por DNI en el respaldo (activos por defecto)."""
        where = "WHERE id = ?" if incluir_inactivos else "WHERE id = ? AND activo = 1"
        with self._conexion() as conexion:
            fila = conexion.execute(
                f"SELECT {self._COLUMNAS} FROM alumnos {where}", (id_alumno,)
            ).fetchone()
        return self._fila_a_alumno(fila) if fila else None

    # ------------------------------------------------------------------ #
    #  Escrituras locales (espejo y fallback de MariaDB)                  #
    # ------------------------------------------------------------------ #

    def guardar_alta(self, datos: AlumnoEntrada, version: int | None = None) -> None:
        """Guarda (o sobrescribe) un alta en el respaldo.

        Args:
            datos: Datos del alumno.
            version: Versión del registro (por defecto 1).
        """
        ahora = datetime.now().isoformat(timespec="seconds")
        with self._conexion() as conexion:
            conexion.execute(
                "INSERT INTO alumnos (id, nombre, apellido, sexo, patronus, imagen, "
                "activo, fecha_baja, version, updated_at) "
                "VALUES (?, ?, ?, ?, ?, ?, 1, NULL, ?, ?) "
                "ON CONFLICT(id) DO UPDATE SET "
                "nombre = excluded.nombre, apellido = excluded.apellido, "
                "sexo = excluded.sexo, patronus = excluded.patronus, "
                "imagen = excluded.imagen, activo = 1, fecha_baja = NULL, "
                "version = excluded.version, updated_at = excluded.updated_at",
                (
                    datos.id,
                    datos.nombre,
                    datos.apellido,
                    datos.sexo,
                    datos.patronus,
                    datos.imagen,
                    version or 1,
                    ahora,
                ),
            )
            conexion.commit()

    def guardar_cambios(self, id_alumno: str, cambios: dict, version: int) -> None:
        """Aplica cambios parciales sobre el alumno del respaldo.

        Args:
            id_alumno: DNI del alumno.
            cambios: Dict {campo: valor} con los campos a modificar.
            version: Nueva versión tras la edición.
        """
        if not cambios:
            return
        asignacion = ", ".join(f"{campo} = ?" for campo in cambios)
        ahora = datetime.now().isoformat(timespec="seconds")
        with self._conexion() as conexion:
            conexion.execute(
                f"UPDATE alumnos SET {asignacion}, version = ?, updated_at = ? WHERE id = ?",
                tuple(cambios.values()) + (version, ahora, id_alumno),
            )
            conexion.commit()

    def guardar_baja(self, id_alumno: str, version: int, fecha_baja: datetime | None = None) -> None:
        """Marca un alumno como inactivo en el respaldo (borrado lógico).

        Args:
            id_alumno: DNI del alumno.
            version: Nueva versión tras la baja.
            fecha_baja: Fecha de la baja; si no llega, se usa la hora actual.
        """
        fecha = (fecha_baja or datetime.now()).isoformat(timespec="seconds")
        ahora = datetime.now().isoformat(timespec="seconds")
        with self._conexion() as conexion:
            conexion.execute(
                "UPDATE alumnos SET activo = 0, fecha_baja = ?, version = ?, "
                "updated_at = ? WHERE id = ?",
                (fecha, version, ahora, id_alumno),
            )
            conexion.commit()

    def sincronizar_desde_mariadb(self, alumnos: list[Alumno]) -> int:
        """Refresca el respaldo con los alumnos devueltos por MariaDB.

        Se usa en la fase de recuperación: cuando MariaDB vuelve a estar
        disponible, el worker vuelca su estado real al respaldo.

        Args:
            alumnos: Lista de alumnos (activos y, si se pidieron, inactivos).

        Returns:
            Número de registros guardados.
        """
        ahora = datetime.now().isoformat(timespec="seconds")
        with self._conexion() as conexion:
            for alumno in alumnos:
                conexion.execute(
                    "INSERT INTO alumnos (id, nombre, apellido, sexo, patronus, imagen, "
                    "activo, fecha_baja, version, updated_at) "
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    "ON CONFLICT(id) DO UPDATE SET "
                    "nombre = excluded.nombre, apellido = excluded.apellido, "
                    "sexo = excluded.sexo, patronus = excluded.patronus, "
                    "imagen = excluded.imagen, activo = excluded.activo, "
                    "fecha_baja = excluded.fecha_baja, version = excluded.version, "
                    "updated_at = excluded.updated_at",
                    (
                        alumno.id,
                        alumno.nombre,
                        alumno.apellido,
                        alumno.sexo,
                        alumno.patronus,
                        alumno.imagen,
                        int(alumno.activo),
                        alumno.fecha_baja.isoformat(timespec="seconds") if alumno.fecha_baja else None,
                        alumno.version,
                        ahora,
                    ),
                )
            conexion.commit()
        return len(alumnos)

    # ------------------------------------------------------------------ #
    #  Cola pending_sync                                                  #
    # ------------------------------------------------------------------ #

    def encolar(self, operacion: str, payload: dict, error: str | None = None) -> int:
        """Registra una operación pendiente de aplicar en MariaDB.

        Args:
            operacion: 'crear', 'actualizar' o 'eliminar'.
            payload: Datos de la operación (se guarda como JSON).
            error: Mensaje de error original, si lo había.

        Returns:
            id de la operación en la cola.
        """
        ahora = datetime.now().isoformat(timespec="seconds")
        with self._conexion() as conexion:
            cursor = conexion.execute(
                "INSERT INTO pending_sync (operacion, payload, intentos, estado, "
                "ultimo_error, proximo_intento, created_at) VALUES (?, ?, 0, 'pendiente', ?, ?, ?)",
                (operacion, json.dumps(payload, ensure_ascii=False), error, ahora, ahora),
            )
            conexion.commit()
            return int(cursor.lastrowid)

    def pendientes(self) -> list[dict]:
        """Devuelve las operaciones pendientes ordenadas por su id (FIFO).

        Returns:
            Lista de dicts con id, operacion, payload (ya parseado), intentos,
            estado, ultimo_error, proximo_intento y created_at.
        """
        with self._conexion() as conexion:
            filas = conexion.execute(
                "SELECT id, operacion, payload, intentos, estado, ultimo_error, "
                "proximo_intento, created_at FROM pending_sync "
                "WHERE estado IN ('pendiente', 'fallido') ORDER BY id"
            ).fetchall()
        return [
            {
                "id": f[0],
                "operacion": f[1],
                "payload": json.loads(f[2]),
                "intentos": f[3],
                "estado": f[4],
                "ultimo_error": f[5],
                "proximo_intento": f[6],
                "created_at": f[7],
            }
            for f in filas
        ]

    def registrar_fallo(self, operacion_id: int, error: str, proximo_intento: datetime, agotado: bool) -> None:
        """Incrementa el contador de intentos y programa el siguiente reintento.

        Args:
            operacion_id: id de la operación en `pending_sync`.
            error: Mensaje del último error.
            proximo_intento: Momento del siguiente intento (backoff exponencial).
            agotado: True si se agotaron los intentos (estado 'fallido').
        """
        estado = "fallido" if agotado else "pendiente"
        with self._conexion() as conexion:
            conexion.execute(
                "UPDATE pending_sync SET intentos = intentos + 1, ultimo_error = ?, "
                "proximo_intento = ?, estado = ? WHERE id = ?",
                (error, proximo_intento.isoformat(timespec="seconds"), estado, operacion_id),
            )
            conexion.commit()

    def eliminar_pendiente(self, operacion_id: int) -> None:
        """Elimina de la cola una operación ya aplicada en MariaDB."""
        with self._conexion() as conexion:
            conexion.execute("DELETE FROM pending_sync WHERE id = ?", (operacion_id,))
            conexion.commit()


# Singleton del respaldo, compartido por la API y el worker de sincronización.
_respaldo: RespaldoSQLite | None = None


def obtener_respaldo(ruta: str) -> RespaldoSQLite:
    """Devuelve la instancia singleton del respaldo SQLite.

    Args:
        ruta: Ruta del fichero (se usa solo en la primera llamada).
    """
    global _respaldo
    if _respaldo is None:
        _respaldo = RespaldoSQLite(ruta)
    return _respaldo
