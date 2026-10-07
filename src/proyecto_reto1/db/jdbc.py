import os
import re
import shutil
import subprocess
from pathlib import Path

import jpype
import jaydebeapi

from .base import Alumno, AlumnoActualizacion, campos_a_actualizar, texto


def _ruta_jvm() -> str:
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


class RepositorioJDBC:
    casa = ""
    driver = ""

    def __init__(self, url: str, usuario: str, password: str):
        self.url = url
        self.usuario = usuario
        self.password = password

    def listar(self) -> list[Alumno]:
        credenciales = {"user": self.usuario, "password": self.password}
        if not self.usuario and not self.password:
            credenciales = {}
        conexion = jaydebeapi.connect(
            self.driver,
            self.url,
            credenciales,
        )
        try:
            cursor = conexion.cursor()
            try:
                cursor.execute("SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS")
                filas = cursor.fetchall()
            finally:
                cursor.close()
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
        credenciales = {"user": self.usuario, "password": self.password}
        if not self.usuario and not self.password:
            credenciales = {}
        conexion = jaydebeapi.connect(
            self.driver,
            self.url,
            credenciales,
        )
        try:
            cursor = conexion.cursor()
            try:
                cursor.execute(
                    "SELECT ID, NOMBRE, APELLIDO, SEXO, PATRONUS FROM ALUMNOS WHERE ID = ?",
                    (id_alumno,),
                )
                fila = cursor.fetchone()
            finally:
                cursor.close()
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

    def actualizar(self, id_alumno: str, datos: AlumnoActualizacion) -> Alumno | None:
        columnas = campos_a_actualizar(datos)
        if not columnas:
            return self.obtener(id_alumno)
        credenciales = {"user": self.usuario, "password": self.password}
        if not self.usuario and not self.password:
            credenciales = {}
        conexion = jaydebeapi.connect(
            self.driver,
            self.url,
            credenciales,
        )
        try:
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
        finally:
            conexion.close()
        return self.obtener(id_alumno)

    def eliminar(self, id_alumno: str) -> bool:
        credenciales = {"user": self.usuario, "password": self.password}
        if not self.usuario and not self.password:
            credenciales = {}
        conexion = jaydebeapi.connect(
            self.driver,
            self.url,
            credenciales,
        )
        try:
            cursor = conexion.cursor()
            try:
                cursor.execute("SELECT ID FROM ALUMNOS WHERE ID = ?", (id_alumno,))
                if cursor.fetchone() is None:
                    return False
                cursor.execute("DELETE FROM ALUMNOS WHERE ID = ?", (id_alumno,))
                conexion.commit()
            finally:
                cursor.close()
        finally:
            conexion.close()
        return True
