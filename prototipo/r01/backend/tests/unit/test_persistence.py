"""Pruebas de la central doble: upsert idempotente, cola y composición."""

from __future__ import annotations

from sqlalchemy import create_engine

from app.persistence.models import Base
from app.persistence.repositories.composite import CompositeCentralRepository
from app.persistence.repositories.retry_queue import SqliteRetryQueue
from app.persistence.repositories.sqlite import SqliteRepository

FILAS = [
    {
        "origen": "derby",
        "id_origen": 1,
        "dni": "12345678Z",
        "nombre": "Alicia Torres",
        "casa": "Gryffindor",
        "patronus": "Leon",
    },
    {
        "origen": "derby",
        "id_origen": 2,
        "dni": "23456789D",
        "nombre": "Bruno Castillo",
        "casa": "Slytherin",
        "patronus": "Serpiente",
    },
]


def _motor(tmp_path, nombre: str):
    engine = create_engine(f"sqlite:///{tmp_path}/{nombre}.db", future=True)
    Base.metadata.create_all(engine)
    return engine


class RepoRoto(SqliteRepository):
    """Repositorio que simula un backend caído hasta que se repara."""

    def __init__(self, engine, name: str = "espejo") -> None:
        super().__init__(engine)
        self.name = name
        self.roto = True

    def upsert(self, rows):  # noqa: ANN001, ANN201
        if self.roto:
            raise RuntimeError("backend caido")
        return super().upsert(rows)

    def ping(self) -> bool:
        return not self.roto


def test_upsert_es_idempotente(tmp_path):
    repo = SqliteRepository(_motor(tmp_path, "central"))

    primero = repo.upsert(FILAS)
    assert (primero.insertados, primero.actualizados, primero.errores) == (2, 0, 0)

    segundo = repo.upsert(FILAS)
    assert (segundo.insertados, segundo.actualizados) == (0, 2)

    assert repo.count() == 2
    assert repo.count(dni="12345678Z") == 1
    assert repo.count(nombre="alicia") == 1


def test_lecturas_devuelven_diccionarios_y_total(tmp_path):
    repo = SqliteRepository(_motor(tmp_path, "central"))
    repo.upsert(FILAS)

    filas = repo.search(limit=1)
    assert [fila["dni"] for fila in filas] == ["12345678Z"]
    assert filas[0]["origen"] == "derby"
    assert isinstance(filas[0]["actualizado"], str)

    por_dni = repo.get_by_dni("23456789D")
    assert por_dni is not None and por_dni["nombre"] == "Bruno Castillo"
    assert repo.get_by_dni("00000000A") is None


def test_conteos_y_huella_por_origen(tmp_path):
    repo = SqliteRepository(_motor(tmp_path, "central"))
    repo.upsert(FILAS)

    assert repo.count_by_origen() == {"derby": 2}
    huella = repo.hash_by_origen()
    assert huella["derby"]
    # La huella es estable ante consultas repetidas.
    assert repo.hash_by_origen() == huella


def test_cola_de_reintentos_ciclo_completo(tmp_path):
    motor = _motor(tmp_path, "central")
    cola = SqliteRetryQueue(motor)

    assert cola.count() == 0
    assert cola.mark("mariadb", FILAS, "conexion rechazada") == 2
    assert cola.count() == 2

    pendientes = cola.pending("mariadb")
    assert {fila["dni"] for fila in pendientes} == {"12345678Z", "23456789D"}
    assert all(fila["intentos"] == 1 for fila in pendientes)

    # Volver a marcar la misma fila incrementa los intentos (no duplica).
    cola.mark("mariadb", FILAS[:1], "sigue caido")
    assert cola.count() == 2
    assert cola.pending("mariadb")[0]["intentos"] == 2

    cola.resolve("mariadb", "derby", 1)
    assert cola.count() == 1
    cola.resolve("mariadb", "derby", 2)
    assert cola.count() == 0


def test_composite_registra_fallo_parcial_y_repara(tmp_path):
    motor_ok = _motor(tmp_path, "ok.db")
    motor_ko = _motor(tmp_path, "ko.db")
    principal = SqliteRepository(motor_ok)
    espejo = RepoRoto(motor_ko, name="mariadb")
    cola = SqliteRetryQueue(motor_ok)
    composite = CompositeCentralRepository([principal, espejo], cola)

    resultado = composite.upsert(FILAS)
    assert resultado.por_backend["sqlite"].insertados == 2
    assert resultado.por_backend["mariadb"].errores == 2
    assert resultado.errores and "backend caido" in resultado.errores[0]
    assert composite.pendientes() == 2

    # El backend se repara: el siguiente reintento liquida la cola.
    espejo.roto = False
    reintentos = composite.retry_pending()
    assert reintentos["mariadb"].insertados == 2  # nunca habían llegado
    assert composite.pendientes() == 0
    assert espejo.count() == 2


def test_lecturas_fallan_al_primer_backend_disponible(tmp_path):
    motor_a = _motor(tmp_path, "a.db")
    motor_b = _motor(tmp_path, "b.db")
    primario = RepoRoto(motor_a, name="mariadb")
    secundario = SqliteRepository(motor_b)
    cola = SqliteRetryQueue(motor_b)
    composite = CompositeCentralRepository([primario, secundario], cola)

    secundario.upsert(FILAS)

    filas, backend = composite.search(limit=1)
    assert backend == "sqlite"
    assert filas[0]["dni"] == "12345678Z"
    assert composite.count() == 2
