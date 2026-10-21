"""Persistencia dual del prototipo R01.

Contrato (WP3 lo importa asi):

    guardar_estudiantes(filas: list[dict]) -> int
    obtener_estudiantes() -> list[dict]

Tabla unica `estudiantes(id, nombre, casa, origen, sincronizado_en)`.
Motor MariaDB con timeout corto (<=3 s) y fallback automatico a SQLite
`prototipo_r01/data/r01.sqlite`.  El arranque NUNCA falla si MariaDB cae.
"""

from __future__ import annotations

import os
import threading
import time
from datetime import datetime
from pathlib import Path

from sqlalchemy import (
    Column,
    DateTime,
    Index,
    Integer,
    MetaData,
    String,
    Table,
    create_engine,
    select,
    text,
)
from sqlalchemy.engine import Engine
from sqlalchemy import inspect as sqlalchemy_inspect

TABLA = "estudiantes"
metadata = MetaData()

estudiantes = Table(
    TABLA,
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("nombre", String(120), nullable=False),
    Column("casa", String(60), nullable=False, default=""),
    Column("origen", String(60), nullable=False, default=""),
    Column("sincronizado_en", DateTime, nullable=False),
)
Index("uq_estudiantes_nombre_origen", estudiantes.c.nombre, estudiantes.c.origen, unique=True)

_CLAVE_MARIADB = os.environ.get("MARIADB_PASSWORD", "r01pass")
_CANDIDATAS_FALLBACK = (
    f"mariadb+pymysql://r01:{_CLAVE_MARIADB}@127.0.0.1:3306/r01_central",
    "mariadb+pymysql://r01:r01@127.0.0.1:3307/hogwarts",
)

_lock = threading.Lock()
_engine: Engine | None = None
motor_usado = "pendiente"
_detalle = "sin resolver"


def _urls_mariadb() -> list[str]:
    urls: list[str] = []
    env = os.environ.get("MARIADB_URL", "").strip()
    if env:
        urls.append(env)
    urls.extend(u for u in _CANDIDATAS_FALLBACK if u not in urls)
    return urls


def _url_enmascarada(url: str) -> str:
    try:
        return url.split("://", 1)[0] + "://" + url.split("@", 1)[1]
    except Exception:
        return url


def _sqlite() -> Engine:
    ruta = Path(__file__).resolve().parents[1] / "data" / "r01.sqlite"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    return create_engine(f"sqlite:///{ruta.as_posix()}", future=True)


_COLUMNAS = {"id", "nombre", "casa", "origen", "sincronizado_en"}


def _esquema_compatible(eng: Engine) -> bool:
    """True si `estudiantes` no existe (la creamos) o ya cumple el contrato."""
    insp = sqlalchemy_inspect(eng)
    if not insp.has_table(TABLA):
        return True
    return _COLUMNAS <= {c["name"] for c in insp.get_columns(TABLA)}


def _intentar_mariadb() -> tuple[Engine, str] | tuple[None, str]:
    limite = time.monotonic() + 3.0
    motivos: list[str] = []
    for url in _urls_mariadb():
        restante = limite - time.monotonic()
        if restante <= 0.5:
            motivos.append("presupuesto 3 s agotado")
            break
        eng: Engine | None = None
        try:
            eng = create_engine(
                url,
                future=True,
                pool_pre_ping=True,
                pool_recycle=3600,
                connect_args={"connect_timeout": max(1, int(restante))},
            )
            with eng.connect() as conn:
                conn.execute(text("SELECT 1"))
            if not _esquema_compatible(eng):
                motivos.append(f"{_url_enmascarada(url)} -> esquema ajeno (tabla estudiantes de otro modulo)")
                eng.dispose()
                continue
            metadata.create_all(eng)
            return eng, _url_enmascarada(url)
        except Exception as exc:
            motivos.append(f"{_url_enmascarada(url)} -> {type(exc).__name__}: {exc}")
            if eng is not None:
                eng.dispose()
    return None, "; ".join(motivos)[:400]


def _resolver() -> tuple[Engine, str, str]:
    mariadb, motivo_maria = _intentar_mariadb()
    if mariadb is not None:
        return mariadb, "mariadb", motivo_maria
    try:
        eng = _sqlite()
        with eng.connect() as conn:
            metadata.create_all(conn)
        return eng, "sqlite", motivo_maria or "fallback"
    except Exception as exc:  # nunca debe tumbar el arranque
        return _sqlite(), "sqlite", f"error sqlite {type(exc).__name__}: {exc}"


def _obtener_motor() -> Engine:
    global _engine, motor_usado, _detalle
    if _engine is None:
        with _lock:
            if _engine is None:
                _engine, motor_usado, _detalle = _resolver()
                metadata.create_all(_engine)
    return _engine


def estado() -> dict:
    """Bandera visible del motor realmente usado (para smokes/informes)."""
    if _engine is None:
        _obtener_motor()
    return {"motor": motor_usado, "detalle": _detalle}


def guardar_estudiantes(filas: list[dict]) -> int:
    if not filas:
        return 0
    eng = _obtener_motor()
    ahora = datetime.now().replace(microsecond=0)
    escritos = 0
    with eng.begin() as conn:
        existentes = {
            (nombre, origen): ident
            for ident, nombre, origen in conn.execute(
                select(estudiantes.c.id, estudiantes.c.nombre, estudiantes.c.origen)
            )
        }
        for fila in filas:
            nombre = str(fila.get("nombre") or "").strip()
            if not nombre:
                continue
            casa = str(fila.get("casa") or "").strip()
            origen = str(fila.get("origen") or "").strip()
            clave = (nombre, origen)
            if clave in existentes:
                conn.execute(
                    estudiantes.update()
                    .where(estudiantes.c.id == existentes[clave])
                    .values(casa=casa, sincronizado_en=ahora)
                )
            else:
                conn.execute(
                    estudiantes.insert().values(
                        nombre=nombre, casa=casa, origen=origen, sincronizado_en=ahora
                    )
                )
            escritos += 1
    return escritos


def obtener_estudiantes() -> list[dict]:
    eng = _obtener_motor()
    with eng.connect() as conn:
        filas = conn.execute(
            select(
                estudiantes.c.id,
                estudiantes.c.nombre,
                estudiantes.c.casa,
                estudiantes.c.origen,
                estudiantes.c.sincronizado_en,
            ).order_by(estudiantes.c.id)
        ).mappings().all()
    salida: list[dict] = []
    for fila in filas:
        item = dict(fila)
        momento = item.get("sincronizado_en")
        if isinstance(momento, datetime):
            item["sincronizado_en"] = momento.isoformat(sep=" ")
        salida.append(item)
    return salida


__all__ = [
    "guardar_estudiantes",
    "obtener_estudiantes",
    "estado",
    "motor_usado",
    "estudiantes",
    "metadata",
]
