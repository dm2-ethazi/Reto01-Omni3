# fallback temporal WP2
"""Persistencia minima mientras `persistencia/` (WP2) no esta entregada.

Misma firma que se espera de `persistencia.guardar_estudiantes(filas)`.
Escribe en `data/r01.sqlite` (relativo a la raiz de `prototipo_r01/`).
"""

import os
import sqlite3

_RUTA_RIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUTA_DB = os.path.join(_RUTA_RIZ, "data", "r01.sqlite")

_TABLA = """CREATE TABLE IF NOT EXISTS estudiantes (
    nombre TEXT NOT NULL,
    casa  TEXT NOT NULL,
    origen TEXT NOT NULL,
    PRIMARY KEY (origen, nombre)
)"""


def guardar_estudiantes(filas: list[dict]) -> int:
    if not filas:
        return 0
    os.makedirs(os.path.dirname(RUTA_DB), exist_ok=True)
    with sqlite3.connect(RUTA_DB) as con:
        con.execute(_TABLA)
        con.executemany(
            "INSERT OR REPLACE INTO estudiantes (nombre, casa, origen) VALUES (?, ?, ?)",
            [(f.get("nombre"), f.get("casa"), f.get("origen")) for f in filas],
        )
    return len(filas)
