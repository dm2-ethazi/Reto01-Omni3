"""Repositorio genérico JDBC para H2, HSQLDB y Derby.

Estos tres motores no tienen driver nativo en Python, así que se acceden a
ellos mediante JDBC: la librería `jaydebeapi` se conecta a través de una JVM
(Java) que se arranca una sola vez con `jpype`.

Este módulo contiene:

- `_ruta_jvm()` / `iniciar_jvm()`: localizan y arrancan la JVM con los
  drivers (`.jar`) en el classpath.
- `RepositorioJDBC`: implementación común del CRUD. Las subclases
  (H2, HSQLDB y Derby) solo declaran `casa` y `driver`.

Conexiones: cada operación abre y cierra su propia conexión (sin pool).
"""

import os
import re
import shutil
import subprocess
from contextlib import contextmanager
from pathlib import Path

import jaydebeapi
import jpype

from .base import (
    COLUMNAS_ALUMNO_SQL,
    Alumno,
    AlumnoActualizacion,
    campos_a_actualizar,
    fila_a_alumno,
)


# --------------------------------------------------------------------------- #
#  Arranque de la JVM                                                         #
# --------------------------------------------------------------------------- #


def _ruta_jvm() -> str:
    """Localiza la ruta al binario de la JVM (jvm.dll / libjvm.so).

    Primero intenta obtener `java.home` ejecutando `java -XshowSettings`.
    Si falla, usa la ruta por defecto de JPype.

    Returns:
        Ruta absoluta al binario de la JVM.
    """
    java = shutil.which("java")
    if java:
        try:
            proceso = subprocess.run(
                [java, "-XshowSettings:properties", "-version"],
                capture_output=True,
                text=True,
                timeout=30,
            )
            coincidencia = re.search(r"java\.home = (.+)", proceso.stderr + proceso.stdout)
            if coincidencia:
                hogar = Path(coincidencia.group(1).strip())
                # Rutas relativas del JVM dentro de JAVA_HOME según el sistema operativo.
                relativos = (
                    ("bin", "server", "jvm.dll"),
                    ("bin", "server", "libjvm.so"),
                    ("lib", "server", "libjvm.so"),
                )
                for rel in relativos:
                    jvm = hogar.joinpath(*rel)
                    if jvm.exists():
                        return str(jvm)
        except (OSError, subprocess.SubprocessError):
            pass
    return jpype.getDefaultJVMPath()


def iniciar_jvm(rutas_jar: list[str]) -> None:
    """Arranca la JVM con los drivers JDBC en el classpath.

    Solo se ejecuta una vez (singleton); las llamadas posteriores no hacen
    nada si la JVM ya está iniciada.

    Args:
        rutas_jar: Lista de rutas absolutas a archivos .jar de drivers JDBC.
    """
    if jpype.isJVMStarted():
        return
    classpath = os.pathsep.join(os.path.abspath(r) for r in rutas_jar)
    jpype.startJVM(
        _ruta_jvm(),
        f"-Djava.class.path={classpath}",
        "--enable-native-access=ALL-UNNAMED",
        convertStrings=False,
        ignoreUnrecognized=True,
    )


# --------------------------------------------------------------------------- #
#  Repositorio genérico                                                       #
# --------------------------------------------------------------------------- #


class RepositorioJDBC:
    """Repositorio genérico de alumnos vía JDBC.

    Implementa el Protocolo `RepositorioAlumnos` usando `jaydebeapi`.
    Cada subclase debe definir:

        casa: Nombre de la casa (ej. "Hufflepuff").
        driver: Clase completa del driver JDBC (ej. "org.h2.Driver").
    """

    casa = ""
    driver = ""

    def __init__(self, url: str, usuario: str, password: str):
        """Inicializa el repositorio.

        Args:
            url: URL JDBC de conexión (ej. jdbc:h2:tcp://localhost:9092/r01db).
            usuario: Usuario de la base de datos.
            password: Contraseña del usuario.
        """
        self.url = url
        self.usuario = usuario
        self.password = password

    # --- Conexión ---------------------------------------------------------- #

    def _credenciales(self) -> dict:
        """Devuelve el dict de credenciales para jaydebeapi.connect()."""
        if not self.usuario and not self.password:
            return {}
        return {"user": self.usuario, "password": self.password}

    @contextmanager
    def _conexion(self):
        """Abre una conexión JDBC y la cierra siempre al salir del bloque.

        Uso:
            with self._conexion() as conexion:
                ...
        """
        conexion = jaydebeapi.connect(self.driver, self.url, self._credenciales())
        try:
            yield conexion
        finally:
            conexion.close()

    # --- Operaciones CRUD -------------------------------------------------- #

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la tabla ALUMNOS."""
        with self._conexion() as conexion:
            cursor = conexion.cursor()
            try:
                cursor.execute(f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS")
                filas = cursor.fetchall()
            finally:
                cursor.close()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def buscar_por_nombre(self, prefijo: str) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo (sin distinguir mayúsculas).

        Args:
            prefijo: Texto inicial del nombre a buscar.
        """
        with self._conexion() as conexion:
            cursor = conexion.cursor()
            try:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS "
                    "WHERE UPPER(NOMBRE) LIKE UPPER(?) ORDER BY NOMBRE",
                    (prefijo + "%",),
                )
                filas = cursor.fetchall()
            finally:
                cursor.close()
        return [fila_a_alumno(f, self.casa) for f in filas]

    def obtener(self, id_alumno: str) -> Alumno | None:
        """Busca un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a buscar.

        Returns:
            Alumno si existe, None si no.
        """
        with self._conexion() as conexion:
            cursor = conexion.cursor()
            try:
                cursor.execute(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS WHERE ID = ?",
                    (id_alumno,),
                )
                fila = cursor.fetchone()
            finally:
                cursor.close()
        if fila is None:
            return None
        return fila_a_alumno(fila, self.casa)

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
        with self._conexion() as conexion:
            cursor = conexion.cursor()
            try:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = ?", (id_alumno,))
                if cursor.fetchone() is None:
                    return None
                asignacion = ", ".join(f"{nombre} = ?" for nombre in columnas)
                cursor.execute(
                    f"UPDATE ALUMNOS SET {asignacion} WHERE ID = ?",
                    tuple(columnas.values()) + (id_alumno,),
                )
                conexion.commit()
            finally:
                cursor.close()
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str) -> bool:
        """Elimina un alumno por DNI.

        Args:
            id_alumno: DNI del alumno a eliminar.

        Returns:
            True si se eliminó, False si no existía.
        """
        with self._conexion() as conexion:
            cursor = conexion.cursor()
            try:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = ?", (id_alumno,))
                if cursor.fetchone() is None:
                    return False
                cursor.execute("DELETE FROM ALUMNOS WHERE ID = ?", (id_alumno,))
                conexion.commit()
            finally:
                cursor.close()
        return True
