from fastapi import FastAPI

from .api.alumnos import router as router_alumnos

app = FastAPI(title="API Alumnos")

app.include_router(router_alumnos)

