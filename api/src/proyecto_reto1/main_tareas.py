"""API de ejemplo: gestión de tareas en memoria (independiente de la API Alumnos).

Este módulo **no** forma parte de la API principal (`main.py`): es una
aplicación FastAPI aparte, pensada como plantilla o práctica de CRUD
básico con Pydantic. Los datos se guardan en una lista de Python, por lo
que se pierden al reiniciar el servidor.

Arranque:
    uv run uvicorn proyecto_reto1.main_tareas:app --reload
"""

from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field

app = FastAPI(title="API Tareas")


# --------------------------------------------------------------------------- #
#  Modelos de entrada y salida                                                #
# --------------------------------------------------------------------------- #


class TareaIn(BaseModel):
    """Datos para crear una tarea (POST /tareas)."""

    titulo: str = Field(min_length=3, max_length=80)
    descripcion: str = Field(default="", max_length=200)


class TareaUpdate(BaseModel):
    """Campos a modificar en una tarea (PUT /tareas/{id}). Actualización parcial."""

    titulo: str | None = Field(default=None, min_length=3, max_length=80)
    descripcion: str | None = Field(default=None, max_length=200)
    completada: bool | None = None


class TareaOut(BaseModel):
    """Devuelto por la API en todas las respuestas que incluyen una tarea."""

    id: int
    titulo: str
    descripcion: str
    completada: bool


# --------------------------------------------------------------------------- #
#  Almacén en memoria                                                         #
# --------------------------------------------------------------------------- #

db: list[dict] = []
next_id = 1


# --------------------------------------------------------------------------- #
#  Endpoints                                                                  #
# --------------------------------------------------------------------------- #


@app.get("/tareas", response_model=list[TareaOut])
def listar_tareas():
    """Devuelve todas las tareas almacenadas."""
    return db


@app.post("/tareas", response_model=TareaOut, status_code=status.HTTP_201_CREATED)
def crear_tarea(payload: TareaIn):
    """Crea una tarea nueva (siempre `completada: false`)."""
    global next_id
    nueva = {
        "id": next_id,
        "titulo": payload.titulo,
        "descripcion": payload.descripcion,
        "completada": False,
    }
    db.append(nueva)
    next_id += 1
    return nueva


@app.get("/tareas/{tarea_id}", response_model=TareaOut)
def obtener_tarea(tarea_id: int):
    """Devuelve una tarea por su ID.

    Raises:
        HTTPException 404: Si no existe ninguna tarea con ese ID.
    """
    for t in db:
        if t["id"] == tarea_id:
            return t
    raise HTTPException(status_code=404, detail="Tarea no encontrada")


@app.put("/tareas/{tarea_id}", response_model=TareaOut)
def actualizar_tarea(tarea_id: int, payload: TareaUpdate):
    """Actualiza los campos enviados de una tarea (los omitidos no se tocan).

    Raises:
        HTTPException 404: Si no existe ninguna tarea con ese ID.
    """
    for t in db:
        if t["id"] == tarea_id:
            if payload.titulo is not None:
                t["titulo"] = payload.titulo
            if payload.descripcion is not None:
                t["descripcion"] = payload.descripcion
            if payload.completada is not None:
                t["completada"] = payload.completada
            return t
    raise HTTPException(status_code=404, detail="Tarea no encontrada")


@app.delete("/tareas/{tarea_id}", status_code=status.HTTP_204_NO_CONTENT)
def eliminar_tarea(tarea_id: int):
    """Elimina una tarea por su ID.

    Raises:
        HTTPException 404: Si no existe ninguna tarea con ese ID.
    """
    for i, t in enumerate(db):
        if t["id"] == tarea_id:
            db.pop(i)
            return None
    raise HTTPException(status_code=404, detail="Tarea no encontrada")
