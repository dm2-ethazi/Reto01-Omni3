"""Endpoints REST de la API Alumnos.

Define las rutas HTTP para gestionar alumnos y consultar el catálogo de
patronus. Las fuentes de datos se inyectan con `Depends(get_repositorios)`
y se recorren según la operación:

- **Lecturas (GET)**: recorren las 5 fuentes; la búsqueda por DNI devuelve la
  primera coincidencia y el listado concatena todas.
- **Escrituras (PUT/DELETE)**: se aplican en todas las fuentes donde exista
  el registro.
- **Altas (POST)**: se realizan siempre en MariaDB (la base central).

Si alguna fuente no responde, la API responde 503 en lugar de degradar en
silencio.
"""

import logging

from fastapi import APIRouter, Depends, HTTPException, Query

from ..db.base import (
    Alumno,
    AlumnoActualizacion,
    AlumnoEntrada,
    Conflicto,
    Patronus,
    RepositorioAlumnos,
)
from ..db.factory import obtener_repositorios
from ..db.mariadb import RepositorioMariaDB

logger = logging.getLogger(__name__)

router = APIRouter()


# --------------------------------------------------------------------------- #
#  Dependencias y helpers                                                     #
# --------------------------------------------------------------------------- #


def get_repositorios() -> list[RepositorioAlumnos]:
    """Dependencia de FastAPI que inyecta la lista de repositorios activos.

    Returns:
        Lista de repositorios creados por la factory (singleton).
    """
    return obtener_repositorios()


def _repositorio_mariadb(fuentes: list[RepositorioAlumnos]) -> RepositorioMariaDB | None:
    """Devuelve el repositorio de MariaDB si está activo, o None.

    MariaDB es la base central: allí viven las altas de alumnos y la tabla
    PATRONUS_IMG.

    Args:
        fuentes: Lista de repositorios activos.
    """
    return next((r for r in fuentes if isinstance(r, RepositorioMariaDB)), None)


def _obtener_patronus_url(fuentes: list[RepositorioAlumnos], patronus: str | None) -> str | None:
    """Busca la URL de la imagen de un patronus en PATRONUS_IMG (MariaDB).

    Args:
        fuentes: Lista de repositorios activos.
        patronus: Nombre del patronus en inglés (columna NOM_ENG).

    Returns:
        URL de la imagen si se encontró; None si el patronus es None, si
        MariaDB no está activa o si hay algún error de consulta.
    """
    if not patronus:
        return None
    repositorio = _repositorio_mariadb(fuentes)
    if repositorio is None:
        return None
    try:
        return repositorio.buscar_patronus_url(patronus)
    except Exception as error:
        logger.warning("No se pudo obtener patronus_url para '%s': %s", patronus, error)
        return None


# --------------------------------------------------------------------------- #
#  GET  /alumnos        —  Listado y búsqueda por nombre                      #
# --------------------------------------------------------------------------- #


@router.get("/alumnos", response_model=list[Alumno], summary="Listar o buscar alumnos")
def listar_alumnos(
    nombre: str | None = Query(
        default=None,
        min_length=1,
        max_length=100,
        description="Filtra por prefijo de nombre (sin distinguir mayúsculas). Si se omite, lista todos.",
    ),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    """Lista todos los alumnos de las 5 bases de datos, o filtra por prefijo de nombre.

    Si se proporciona el parámetro `nombre`, busca alumnos cuyo nombre empiece
    por ese texto (sin distinguir mayúsculas/minúsculas). Si no se proporciona,
    devuelve la lista completa.

    Args:
        nombre: Prefijo del nombre a buscar (opcional).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Returns:
        Lista de alumnos de todas las fuentes.

    Raises:
        HTTPException 503: Si alguna fuente no responde.
    """
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


# --------------------------------------------------------------------------- #
#  GET  /alumnos/{dni}  —  Búsqueda por DNI                                   #
# --------------------------------------------------------------------------- #


@router.get("/alumnos/{dni}", response_model=Alumno, summary="Buscar alumno por DNI")
def obtener_alumno(dni: str, fuentes: list[RepositorioAlumnos] = Depends(get_repositorios)):
    """Busca un alumno por DNI en las 5 fuentes y devuelve la primera coincidencia.

    Si el alumno tiene patronus, se busca también la URL de su imagen en
    PATRONUS_IMG y se incluye en el campo `patronus_url`.

    Args:
        dni: DNI del alumno a buscar.
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Returns:
        Alumno encontrado, con `patronus_url` relleno si aplica.

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente.
        HTTPException 503: Si alguna fuente no responde.
    """
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


# --------------------------------------------------------------------------- #
#  GET  /patronus      —  Catálogo de patronus                                #
# --------------------------------------------------------------------------- #


@router.get("/patronus", response_model=list[Patronus], summary="Listar todos los patronus")
def listar_patronus(fuentes: list[RepositorioAlumnos] = Depends(get_repositorios)):
    """Devuelve todos los patronus del catálogo PATRONUS_IMG (nombre en inglés e imagen).

    La tabla PATRONUS_IMG solo existe en MariaDB, por lo que este endpoint
    requiere que el motor 'mariadb' esté activo en DB_ENGINES.

    Args:
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Returns:
        Lista de patronus con nombre en inglés y URL de imagen.

    Raises:
        HTTPException 503: Si MariaDB no está activa o no responde.
    """
    repositorio = _repositorio_mariadb(fuentes)
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


# --------------------------------------------------------------------------- #
#  POST /alumnos      —  Crear alumno                                         #
# --------------------------------------------------------------------------- #


@router.post("/alumnos", response_model=Alumno, status_code=201, summary="Crear un alumno")
def crear_alumno(
    datos: AlumnoEntrada,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    """Crea un alumno nuevo en MariaDB (la base central).

    Flujo:
    1. Comprueba que el DNI no exista en ninguna de las 5 fuentes (409 si existe).
    2. Valida el patronus contra PATRONUS_IMG (columna NOM_ENG). Si no existe, 422.
    3. Inserta el alumno en MariaDB.

    Args:
        datos: Datos del alumno (AlumnoEntrada; el patronus va en inglés).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Returns:
        Alumno creado.

    Raises:
        HTTPException 409: Si el DNI ya existe en alguna fuente.
        HTTPException 422: Si el patronus no existe en PATRONUS_IMG o los datos no son válidos.
        HTTPException 503: Si MariaDB no está activa o alguna fuente no responde.
    """
    # 1) Comprobar DNI duplicado en las 5 fuentes.
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

    # 2) Obtener repositorio MariaDB (central).
    repositorio = _repositorio_mariadb(fuentes)
    if repositorio is None:
        raise HTTPException(
            status_code=503,
            detail="La fuente 'mariadb' no esta activa (DB_ENGINES)",
        )

    # 3) Validar patronus contra PATRONUS_IMG si se indicó.
    if datos.patronus:
        try:
            patronus_valido = repositorio.validar_patronus(datos.patronus)
        except Exception as error:
            logger.error("Fallo validando patronus en MariaDB: %s", error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
        if not patronus_valido:
            raise HTTPException(
                status_code=422,
                detail=(
                    f"El patronus '{datos.patronus}' no existe en la tabla PATRONUS_IMG"
                ),
            )

    # 4) Insertar en MariaDB.
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


# --------------------------------------------------------------------------- #
#  PUT  /alumnos/{dni}  —  Actualizar alumno                                  #
# --------------------------------------------------------------------------- #


@router.put("/alumnos/{dni}", response_model=Alumno, summary="Actualizar alumno")
def actualizar_alumno(
    dni: str,
    datos: AlumnoActualizacion,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    """Actualiza los campos enviados de un alumno en todas las fuentes donde exista.

    Solo se modifican los campos presentes en el cuerpo de la petición.
    Los campos omitidos no se tocan. Los campos enviados como null o ""
    se guardan como NULL. No valida el patronus contra PATRONUS_IMG
    (solo el POST lo valida).

    Args:
        dni: DNI del alumno a actualizar (tomado de la URL).
        datos: Campos a modificar (actualización parcial).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Returns:
        Alumno actualizado (la primera coincidencia entre las fuentes).

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente.
        HTTPException 422: Si el cuerpo está vacío o algún valor no es válido.
        HTTPException 503: Si alguna fuente no responde.
    """
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


# --------------------------------------------------------------------------- #
#  DELETE /alumnos/{dni}  —  Eliminar alumno                                  #
# --------------------------------------------------------------------------- #


@router.delete("/alumnos/{dni}", status_code=204, summary="Eliminar alumno")
def eliminar_alumno(
    dni: str,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
):
    """Elimina un alumno de todas las fuentes donde exista.

    Args:
        dni: DNI del alumno a eliminar (tomado de la URL).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente.
        HTTPException 503: Si alguna fuente no responde.
    """
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
