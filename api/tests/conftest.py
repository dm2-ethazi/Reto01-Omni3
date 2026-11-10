"""Dobles de redes con SQLite real para verificar transacciones y HTTP."""

from contextlib import contextmanager

import pytest
from fastapi.testclient import TestClient
from proyecto_reto1.db.base import Conflicto, Patronus, fila_a_alumno
from proyecto_reto1.db.sqlite import CentralSQLite, persistible
from proyecto_reto1.main import crear_app
from proyecto_reto1.services.alumnos import ServicioAlumnos


class MariaPrueba:
    def __init__(self, path):
        self.local = CentralSQLite(str(path))
        self.disponible = True

    def preparar(self):
        if not self.disponible:
            raise ConnectionError("caída simulada")

    def listar_central(self):
        self.preparar()
        return self.local.listar_central()

    def obtener_central(self, dni):
        self.preparar()
        with self.local.conexion() as db:
            return self.local.obtener(db, dni)

    def mutar(self, previo, nuevo):
        self.preparar()
        with self.local.conexion(escritura=True) as db:
            actual = self.local.obtener(db, nuevo.id)
            if previo is None and actual is not None:
                raise Conflicto("El DNI ya existe")
            if previo is not None and (
                actual is None or persistible(actual) != persistible(previo)
            ):
                raise Conflicto("La versión ha cambiado")
            self.local.guardar(db, nuevo)
            return self.local.obtener(db, nuevo.id)

    def listar_patronus(self):
        self.preparar()
        return [Patronus(nom_eng=n, url="") for n in ("Otter", "Stag", "Doe", "Wolf")]

    def aplicar(self, alumno):
        self.preparar()
        assert alumno.tipo == "HISTORICO", (
            "El refresco no debe escribir nuevos en MariaDB"
        )
        with self.local.conexion(escritura=True) as db:
            actual = self.local.obtener(db, alumno.id)
            if actual and actual.version > alumno.version:
                raise Conflicto("Versión posterior")
            self.local.guardar(db, alumno)


class FuentePrueba:
    casa = "Hufflepuff"

    def __init__(self, path):
        self.local = CentralSQLite(str(path))
        self.disponible = True
        with self.local.conexion(escritura=True) as db:
            db.execute(
                "INSERT INTO ALUMNOS VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (
                    "10000002B",
                    "Cedric",
                    "Diggory",
                    "H",
                    None,
                    None,
                    self.casa,
                    "h2",
                    "HISTORICO",
                    1,
                    1,
                    "",
                ),
            )

    @contextmanager
    def _conexion(self):
        if not self.disponible:
            raise ConnectionError("origen caído")
        with self.local.conexion(escritura=True) as db:
            yield db

    def listar(self):
        with self._conexion() as db:
            return [
                fila_a_alumno(f, self.casa)
                for f in db.execute(
                    "SELECT ID,NOMBRE,APELLIDO,SEXO,PATRONUS,IMAGEN FROM ALUMNOS"
                )
            ]

    def obtener(self, dni):
        return next((a for a in self.listar() if a.id == dni), None)


@pytest.fixture
def entorno(tmp_path):
    local = CentralSQLite(str(tmp_path / "central.sqlite3"))
    maria = MariaPrueba(tmp_path / "espejo.sqlite3")
    fuente = FuentePrueba(tmp_path / "origen.sqlite3")
    servicio = ServicioAlumnos(local, maria, {"h2": fuente})
    with TestClient(crear_app(servicio, reintentos=False)) as cliente:
        yield cliente, servicio, maria, fuente
