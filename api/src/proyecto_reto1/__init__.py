"""API Alumnos (Reto 1): alumnado de Hogwarts repartido en 5 bases de datos.

Estructura del paquete:

- `config.py`: configuración cargada desde el archivo `.env`.
- `main.py`: aplicación FastAPI principal (los endpoints de alumnos).
- `main_tareas.py`: API de ejemplo independiente (tareas en memoria).
- `api/`: endpoints REST.
- `db/`: modelos, protocolo y repositorios de cada motor.
"""


def main() -> None:
    """Arranca la API con Uvicorn (ejecutable `proyecto-reto1`)."""
    import uvicorn

    uvicorn.run("proyecto_reto1.main:app", host="127.0.0.1", port=8000)
