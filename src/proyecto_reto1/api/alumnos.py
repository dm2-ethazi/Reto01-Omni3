import logging

from fastapi import APIRouter, Depends, HTTPException

from ..db.base import Alumno, AlumnoEntrada, Conflicto, RepositorioAlumnos
from ..db.factory import obtener_repositorios
from ..db.mariadb import RepositorioMariaDB

logger = logging.getLogger(__name__)

router = APIRouter()


def get_repositorios() -> list[RepositorioAlumnos]:
    return obtener_repositorios()


@router.get("/alumnos", response_model=list[Alumno])
def listar_alumnos(fuentes: list[RepositorioAlumnos] = Depends(get_repositorios)):
    alumnos: list[Alumno] = []
    for repositorio in fuentes:
        try:
            alumnos.extend(repositorio.listar())
        except Exception as error:
            logger.error("Fallo consultando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
    return alumnos


@router.get("/alumnos/{dni}", response_model=Alumno)
def obtener_alumno(dni: str, fuentes: list[RepositorioAlumnos] = Depends(get_repositorios)):
    for repositorio in fuentes:
        try:
            alumno = repositorio.obtener(dni)
        except Exception as error:
            logger.error("Fallo consultando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if alumno is not None:
            return alumno
    raise HTTPException(
        status_code=404,
        detail=f"No existe ningun alumno con DNI '{dni}'",
    )


@router.post("/alumnos", response_model=Alumno, status_code=201)
def crear_alumno(
    datos: AlumnoEntrada,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    for repositorio in fuentes:
        try:
            existente = repositorio.obtener(datos.id)
        except Exception as error:
            logger.error("Fallo comprobando DNI en %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if existente is not None:
            raise HTTPException(
                status_code=409,
                detail=f"El DNI '{datos.id}' ya existe en {repositorio.casa}",
            )

    repositorio = next((r for r in fuentes if isinstance(r, RepositorioMariaDB)), None)
    if repositorio is None:
        raise HTTPException(
            status_code=503,
            detail="La fuente 'mariadb' no esta activa (DB_ENGINES)",
        )
    try:
        return repositorio.crear(datos)
    except Conflicto as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    except Exception as error:
        logger.error("Fallo al crear alumno en MariaDB: %s", error)
        raise HTTPException(
            status_code=503,
            detail=f"La fuente '{repositorio.casa}' no responde: {error}",
        ) from error
