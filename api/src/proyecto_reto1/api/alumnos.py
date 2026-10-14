import logging

from fastapi import APIRouter, Depends, HTTPException, Query

from ..db.base import Alumno, AlumnoActualizacion, AlumnoEntrada, Conflicto, Patronus, RepositorioAlumnos
from ..db.factory import obtener_repositorios
from ..db.mariadb import RepositorioMariaDB

logger = logging.getLogger(__name__)

router = APIRouter()


def get_repositorios() -> list[RepositorioAlumnos]:
    return obtener_repositorios()


@router.get("/alumnos", response_model=list[Alumno])
def listar_alumnos(
    nombre: str | None = Query(default=None, min_length=1, max_length=100),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    alumnos: list[Alumno] = []
    for repositorio in fuentes:
        try:
            if nombre:
                alumnos.extend(repositorio.buscar_por_nombre(nombre))
            else:
                alumnos.extend(repositorio.listar())
        except Exception as error:
            logger.error("Fallo consultando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
    return alumnos


@router.get("/patronus", response_model=list[Patronus])
def listar_patronus(fuentes: list[RepositorioAlumnos] = Depends(get_repositorios)):
    repositorio = next((r for r in fuentes if isinstance(r, RepositorioMariaDB)), None)
    if repositorio is None:
        raise HTTPException(
            status_code=503,
            detail="La fuente 'mariadb' no esta activa (DB_ENGINES)",
        )
    try:
        return repositorio.listar_patronus()
    except Exception as error:
        logger.error("Fallo consultando PATRONUS_IMG: %s", error)
        raise HTTPException(
            status_code=503,
            detail=f"La fuente '{repositorio.casa}' no responde: {error}",
        ) from error


def _obtener_patronus_url(fuentes: list[RepositorioAlumnos], patronus: str | None) -> str | None:
    if not patronus:
        return None
    repositorio = next((r for r in fuentes if isinstance(r, RepositorioMariaDB)), None)
    if repositorio is None:
        return None
    try:
        return repositorio.buscar_patronus_url(patronus)
    except Exception as error:
        logger.warning("No se pudo obtener patronus_url para '%s': %s", patronus, error)
        return None


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
            alumno.patronus_url = _obtener_patronus_url(fuentes, alumno.patronus)
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
    if datos.patronus:
        try:
            patronus_valido = repositorio.validar_patronus(datos.patronus, datos.patronus_lang)
        except Exception as error:
            logger.error("Fallo validando patronus en MariaDB: %s", error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if not patronus_valido:
            idiomas = {"es": "español", "en": "inglés", "eu": "euskera"}
            raise HTTPException(
                status_code=422,
                detail=(
                    f"El patronus '{datos.patronus}' no existe en la tabla PATRONUS_IMG "
                    f"para el idioma '{idiomas[datos.patronus_lang]}'"
                ),
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


@router.put("/alumnos/{dni}", response_model=Alumno)
def actualizar_alumno(
    dni: str,
    datos: AlumnoActualizacion,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    actualizados: list[Alumno] = []
    for repositorio in fuentes:
        try:
            alumno = repositorio.actualizar(dni, datos)
        except Exception as error:
            logger.error("Fallo actualizando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if alumno is not None:
            actualizados.append(alumno)
    if not actualizados:
        raise HTTPException(
            status_code=404,
            detail=f"No existe ningun alumno con DNI '{dni}'",
        )
    return actualizados[0]


@router.delete("/alumnos/{dni}", status_code=204)
def eliminar_alumno(
    dni: str,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    eliminado = False
    for repositorio in fuentes:
        try:
            borrado = repositorio.eliminar(dni)
        except Exception as error:
            logger.error("Fallo eliminando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if borrado:
            eliminado = True
    if not eliminado:
        raise HTTPException(
            status_code=404,
            detail=f"No existe ningun alumno con DNI '{dni}'",
        )
