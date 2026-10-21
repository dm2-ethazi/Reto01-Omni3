from fastapi import FastAPI
from pydantic import BaseModel

import persistencia
from persistencia import guardar_estudiantes, obtener_estudiantes

app = FastAPI(title="prototipo_r01", version="0.1")


class Fila(BaseModel):
    nombre: str
    casa: str = ""
    origen: str = ""


@app.get("/health")
def health() -> dict:
    return {"reto": "R01", "status": "ok"}


@app.get("/estado")
def estado() -> dict:
    return persistencia.estado()


@app.get("/estudiantes")
def listar() -> dict:
    filas = obtener_estudiantes()
    return {"total": len(filas), "estudiantes": filas}


@app.post("/estudiantes")
def guardar(filas: list[Fila]) -> dict:
    n = guardar_estudiantes([f.model_dump() for f in filas])
    return {"guardados": n, **persistencia.estado()}
