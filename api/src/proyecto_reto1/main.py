"""Punto de entrada de la API Alumnos (Reto 1 / R01).

Expone un alumnado de Hogwarts repartido en 5 bases de datos diferentes
(Oracle, MariaDB, H2, HSQLDB y Derby) con una única interfaz REST, más un
respaldo central en SQLite con sincronización con reintentos y un endpoint de
estado (`GET /status`).

El evento de lifespan arranca y detiene el worker de sincronización que vacía
la cola `pending_sync` cuando MariaDB se recupera.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api.alumnos import router as router_alumnos
from .api.estado import router as router_estado
from .db.factory import obtener_worker

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Arranca el worker de sincronización al iniciar y lo detiene al parar."""
    worker = obtener_worker()
    worker.arrancar()
    try:
        yield
    finally:
        worker.detener()


app = FastAPI(
    title="API Alumnos",
    description=(
        "API REST que gestiona el alumnado de Hogwarts distribuido en 5 motores "
        "de base de datos, con respaldo central en SQLite (Reto R01). Los "
        "endpoints de lectura recorren todas las fuentes; las escrituras se "
        "aplican en todas las fuentes donde exista el registro, y las altas se "
        "realizan siempre en MariaDB. El borrado es lógico (activo=false), las "
        "ediciones usan concurrencia optimista (If-Match / version) y el "
        "patronus es obligatorio en altas. GET /status informa del estado."
    ),
    version="2.0.0",
    lifespan=lifespan,
)

app.include_router(router_alumnos)
app.include_router(router_estado)
