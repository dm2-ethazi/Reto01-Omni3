from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field

app = FastAPI(title='API Tareas')

class TareaIn(BaseModel):
    titulo: str = Field(min_length=3, max_length=80)
    descripcion: str = Field(default='', max_length=200)

class TareaUpdate(BaseModel):
    titulo: str | None = Field(default=None, min_length=3, max_length=80)
    descripcion: str | None = Field(default=None, max_length=200)
    completada: bool | None = None

class TareaOut(BaseModel):
    id: int
    titulo: str
    descripcion: str
    completada: bool

db: list[dict] = []
next_id = 1

@app.get('/tareas', response_model=list[TareaOut])
def listar_tareas():
    return db

@app.post('/tareas', response_model=TareaOut, status_code=status.HTTP_201_CREATED)
def crear_tarea(payload: TareaIn):
    global next_id
    nueva = {
        'id': next_id,
        'titulo': payload.titulo,
        'descripcion': payload.descripcion,
        'completada': False,
    }
    db.append(nueva)
    next_id += 1
    return nueva

@app.get('/tareas/{tarea_id}', response_model=TareaOut)
def obtener_tarea(tarea_id: int):
    for t in db:
        if t['id'] == tarea_id:
            return t
    raise HTTPException(status_code=404, detail='Tarea no encontrada')

@app.put('/tareas/{tarea_id}', response_model=TareaOut)
def actualizar_tarea(tarea_id: int, payload: TareaUpdate):
    for t in db:
        if t['id'] == tarea_id:
            if payload.titulo is not None:
                t['titulo'] = payload.titulo
            if payload.descripcion is not None:
                t['descripcion'] = payload.descripcion
            if payload.completada is not None:
                t['completada'] = payload.completada
            return t
    raise HTTPException(status_code=404, detail='Tarea no encontrada')

@app.delete('/tareas/{tarea_id}', status_code=status.HTTP_204_NO_CONTENT)
def eliminar_tarea(tarea_id: int):
    for i, t in enumerate(db):
        if t['id'] == tarea_id:
            db.pop(i)
            return None
    raise HTTPException(status_code=404, detail='Tarea no encontrada')