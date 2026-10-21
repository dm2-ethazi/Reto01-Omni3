"""Punto de entrada de la API Alumnos (Reto 1).

Expone un alumnado de Hogwarts repartido en 5 bases de datos diferentes
(Oracle, MariaDB, H2, HSQLDB y Derby) con una única interfaz REST.
"""

from fastapi import FastAPI

from .api.alumnos import router as router_alumnos

__author__ = "Henri Tejada"

app = FastAPI(
    title="API Alumnos",
    description=(
        "API REST que gestiona el alumnado de Hogwarts distribuido en 5 motores "
        "de base de datos. Los endpoints de lectura recorren todas las fuentes; "
        "las escrituras se aplican en todas las fuentes donde exista el registro, "
        "y las altas se realizan siempre en MariaDB."
    ),
    version="1.0.0",
)

app.include_router(router_alumnos)
