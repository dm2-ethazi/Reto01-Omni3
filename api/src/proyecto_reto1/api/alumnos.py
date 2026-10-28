"""Endpoints REST de la API Alumnos.

Define las rutas HTTP para gestionar alumnos, consultar el catálogo de
patronus y ver el estado del sistema (R01). Las fuentes de datos se inyectan
con `Depends(get_repositorios)` y con el `Sincronizador` (MariaDB + respaldo
SQLite):

- **Lecturas (GET)**: recorren las 5 fuentes; la búsqueda por DNI devuelve la
  primera coincidencia y el listado concatena todas. Si MariaDB no responde,
  la parte central se sirve desde el respaldo SQLite y la respuesta lleva la
  cabecera `X-Data-Source: sqlite`.
- **Escrituras (PUT/DELETE)**: se aplican en todas las fuentes donde exista el
  registro; MariaDB se gestiona vía `Sincronizador` (si falla, se guarda en
  SQLite y se encola en `pending_sync`). Requieren la versión del recurso
  (`If-Match` o campo `version`) para el control de concurrencia optimista.
- **Altas (POST)**: se realizan siempre en MariaDB (la base central) y el
  campo `patronus` es obligatorio.

Si alguna fuente clásica no responde, la API responde 503 en lugar de
degradar en silencio.
"""

import logging

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response

from ..db.base import (
    Alumno,
    AlumnoActualizacion,
    AlumnoEntrada,
    Conflicto,
    ConflictoVersion,
    Patronus,
    RepositorioAlumnos,
)
from ..db.factory import obtener_repositorios, obtener_sincronizador
from ..db.mariadb import RepositorioMariaDB
from ..db.sincronizacion import Sincronizador

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


def get_sincronizador() -> Sincronizador:
    """Dependencia de FastAPI que inyecta el sincronizador (MariaDB + SQLite).

    Returns:
        Instancia singleton del sincronizador.
    """
    return obtener_sincronizador()


def _repositorio_mariadb(fuentes: list[RepositorioAlumnos]) -> RepositorioMariaDB | None:
    """Devuelve el repositorio de MariaDB si está activo, o None.

    MariaDB es la base central: allí viven las altas de alumnos y la tabla
    PATRONUS_IMG.

    Args:
        fuentes: Lista de repositorios activos.
    """
    return next((r for r in fuentes if isinstance(r, RepositorioMariaDB)), None)


def _marcar_fuente(response: Response, desde_respaldo: bool) -> None:
    """Marca la cabecera `X-Data-Source` según venga del respaldo o no.

    Args:
        response: Respuesta de FastAPI (para añadir cabeceras).
        desde_respaldo: True si los datos proceden del respaldo SQLite.
    """
    if desde_respaldo:
        response.headers["X-Data-Source"] = "sqlite"


def _marcar_pendiente(response: Response, pendiente: bool) -> None:
    """Marca la cabecera `X-Pending-Sync` si la escritura quedó encolada.

    Args:
        response: Respuesta de FastAPI (para añadir cabeceras).
        pendiente: True si la operación se guardó en `pending_sync`.
    """
    if pendiente:
        response.headers["X-Pending-Sync"] = "true"


def _version_desde_if_match(if_match: str | None) -> int | None:
    """Extrae la versión numérica de una cabecera `If-Match`.

    Acepta formatos `3`, `"3"` y `W/"3"`.

    Args:
        if_match: Valor de la cabecera If-Match (o None).

    Returns:
        Versión como entero, o None si la cabecera no venía o no era numérica.
    """
    if not if_match:
        return None
    texto = if_match.strip().removeprefix("W/").strip('"')
    return int(texto) if texto.isdigit() else None


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


def _version_requerida(if_match: str | None, version_cuerpo: int | None) -> int:
    """Valida que la petición incluya la versión del recurso (428 si falta).

    Args:
        if_match: Cabecera If-Match enviada por el cliente.
        version_cuerpo: Campo `version` del cuerpo o de la query string.

    Returns:
        La versión a aplicar (prioriza If-Match).

    Raises:
        HTTPException 428: Si no se envió ninguna versión.
    """
    version = _version_desde_if_match(if_match) or version_cuerpo
    if version is None:
        raise HTTPException(
            status_code=428,
            detail=(
                "Debe enviar la versión del recurso para modificarlo: "
                "cabecera If-Match (p. ej. If-Match: 1) o campo 'version'"
            ),
        )
    return version


def _conflicto_version(error: ConflictoVersion) -> HTTPException:
    """Convierte un ConflictoVersion en un 409 con la versión actual."""
    return HTTPException(
        status_code=409,
        detail={
            "mensaje": str(error),
            "version_actual": error.version_actual,
        },
    )


# --------------------------------------------------------------------------- #
#  GET  /alumnos        — Listado y búsqueda por nombre                       #
# --------------------------------------------------------------------------- #


@router.get("/alumnos", response_model=list[Alumno], summary="Listar o buscar alumnos")
def listar_alumnos(
    response: Response,
    nombre: str | None = Query(
        default=None,
        min_length=1,
        max_length=100,
        description="Filtra por prefijo de nombre (sin distinguir mayúsculas). Si se omite, lista todos.",
    ),
    incluir_inactivos: bool = Query(
        default=False,
        description="Si es true, incluye los alumnos dados de baja lógicamente (activo = false).",
    ),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Lista los alumnos activos de las 5 bases de datos, o filtra por prefijo.

    Por defecto solo devuelve alumnos **activos** (borrado lógico). Pasa
    `incluir_inactivos=true` para ver también los dados de baja. Si MariaDB no
    responde, la parte central se sirve del respaldo SQLite (cabecera
    `X-Data-Source: sqlite`).

    Args:
        nombre: Prefijo del nombre a buscar (opcional).
        incluir_inactivos: Incluir registros dados de baja (opcional).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Returns:
        Lista de alumnos de todas las fuentes.

    Raises:
        HTTPException 503: Si alguna fuente clásica no responde.
    """
    alumnos: list[Alumno] = []
    desde_respaldo = False
    for repositorio in fuentes:
        if isinstance(repositorio, RepositorioMariaDB):
            if nombre:
                encontrados, fallback = sincronizador.buscar_por_nombre(nombre, incluir_inactivos)
            else:
                encontrados, fallback = sincronizador.listar(incluir_inactivos)
            desde_respaldo = desde_respaldo or fallback
            alumnos.extend(encontrados)
            continue
        try:
            if nombre:
                alumnos.extend(repositorio.buscar_por_nombre(nombre, incluir_inactivos))
            else:
                alumnos.extend(repositorio.listar(incluir_inactivos))
        except Exception as error:
            logger.error("Fallo consultando %s: %s", repositorio.casa, error)
            raise HTTPException(
                status_code=503,
                detail=f"La fuente '{repositorio.casa}' no responde: {error}",
            ) from error
    _marcar_fuente(response, desde_respaldo)
    return alumnos


# --------------------------------------------------------------------------- #
#  GET  /alumnos/{dni}  — Búsqueda por DNI                                    #
# --------------------------------------------------------------------------- #


@router.get("/alumnos/{dni}", response_model=Alumno, summary="Buscar alumno por DNI")
def obtener_alumno(
    dni: str,
    response: Response,
    incluir_inactivos: bool = Query(
        default=False,
        description="Si es true, también encuentra alumnos dados de baja lógicamente.",
    ),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Busca un alumno por DNI en las 5 fuentes (activos por defecto).

    Si el alumno tiene patronus, se busca también la URL de su imagen en
    PATRONUS_IMG y se incluye en el campo `patronus_url`. Si MariaDB no
    responde, la búsqueda central se hace en el respaldo SQLite (cabecera
    `X-Data-Source: sqlite`).

    Args:
        dni: DNI del alumno a buscar.
        incluir_inactivos: También encontrar alumnos dados de baja.
        fuentes: Lista de repositorios activos (inyectada por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Returns:
        Alumno encontrado, con `patronus_url` relleno si aplica.

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente (o está inactivo).
        HTTPException 503: Si alguna fuente clásica no responde.
    """
    desde_respaldo = False
    for repositorio in fuentes:
        if isinstance(repositorio, RepositorioMariaDB):
            alumno, fallback = sincronizador.obtener(dni, incluir_inactivos)
            desde_respaldo = desde_respaldo or fallback
        else:
            try:
                alumno = repositorio.obtener(dni, incluir_inactivos)
            except Exception as error:
                logger.error("Fallo consultando %s: %s", repositorio.casa, error)
                raise HTTPException(
                    status_code=503,
                    detail=f"La fuente '{repositorio.casa}' no responde: {error}",
                ) from error
        if alumno is not None:
            alumno.patronus_url = _obtener_patronus_url(fuentes, alumno.patronus)
            _marcar_fuente(response, desde_respaldo)
            return alumno
    raise HTTPException(
        status_code=404,
        detail=f"No existe ningun alumno activo con DNI '{dni}'",
    )


# --------------------------------------------------------------------------- #
#  GET  /patronus      — Catálogo de patronus                                 #
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
#  POST /alumnos      — Crear alumno                                          #
# --------------------------------------------------------------------------- #


@router.post("/alumnos", response_model=Alumno, status_code=201, summary="Crear un alumno")
def crear_alumno(
    datos: AlumnoEntrada,
    response: Response,
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Crea un alumno nuevo en MariaDB (la base central).

    El campo `patronus` es **obligatorio** (422 si falta o viene vacío).

    Flujo:
    1. Comprueba que el DNI no exista en ninguna de las 5 fuentes, incluidos
       los alumnos dados de baja lógicamente (409 si existe).
    2. Valida el patronus contra PATRONUS_IMG (columna NOM_ENG). Si no existe, 422.
       (Si MariaDB no responde, la validación se salta y el alta se encola.)
    3. Inserta el alumno en MariaDB y lo refleja en el respaldo SQLite. Si
       MariaDB falla, el alta queda guardada en SQLite y encolada en
       `pending_sync` (cabecera `X-Pending-Sync: true`).

    Args:
        datos: Datos del alumno (AlumnoEntrada; el patronus va en inglés).
        fuentes: Lista de repositorios activos (inyectada por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Returns:
        Alumno creado.

    Raises:
        HTTPException 409: Si el DNI ya existe (aunque esté dado de baja).
        HTTPException 422: Si falta el patronus, no existe en PATRONUS_IMG o
            los datos no son válidos.
        HTTPException 503: Si alguna fuente clásica no responde.
    """
    # 1) Comprobar DNI duplicado en las 5 fuentes (incluidos inactivos).
    for repositorio in fuentes:
        if isinstance(repositorio, RepositorioMariaDB):
            existente, _ = sincronizador.obtener(datos.id, incluir_inactivos=True)
        else:
            try:
                existente = repositorio.obtener(datos.id, incluir_inactivos=True)
            except Exception as error:
                logger.error("Fallo comprobando DNI en %s: %s", repositorio.casa, error)
                raise HTTPException(
                    status_code=503,
                    detail=f"La fuente '{repositorio.casa}' no responde: {error}",
                ) from error
        if existente is not None:
            estado = "" if existente.activo else " (dado de baja)"
            raise HTTPException(
                status_code=409,
                detail=f"El DNI '{datos.id}' ya existe en {repositorio.casa}{estado}",
            )

    # 2) Obtener repositorio MariaDB (central).
    repositorio = _repositorio_mariadb(fuentes)

    # 3) Validar patronus contra PATRONUS_IMG (obligatorio en altas).
    if repositorio is not None:
        try:
            patronus_valido = repositorio.validar_patronus(datos.patronus)
        except Exception as error:
            # MariaDB caído: no se puede validar ahora; el alta se encolará.
            logger.warning("No se pudo validar el patronus (MariaDB caído): %s", error)
        else:
            if not patronus_valido:
                raise HTTPException(
                    status_code=422,
                    detail=f"El patronus '{datos.patronus}' no existe en la tabla PATRONUS_IMG",
                )

    # 4) Alta en MariaDB (con respaldo SQLite y cola de sincronización).
    try:
        alumno, pendiente = sincronizador.crear(datos)
    except Conflicto as error:
        raise HTTPException(status_code=409, detail=str(error)) from error
    _marcar_pendiente(response, pendiente)
    return alumno


# --------------------------------------------------------------------------- #
#  PUT  /alumnos/{dni}  — Actualizar alumno                                   #
# --------------------------------------------------------------------------- #


@router.put("/alumnos/{dni}", response_model=Alumno, summary="Actualizar alumno")
def actualizar_alumno(
    dni: str,
    datos: AlumnoActualizacion,
    response: Response,
    if_match: str | None = Header(default=None, alias="If-Match"),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Actualiza los campos enviados de un alumno (concurrencia optimista).

    **Control de concurrencia**: la petición debe incluir la versión que el
    cliente leyó, mediante la cabecera `If-Match: N` o el campo `version` del
    cuerpo. Si no se envía → 428. Si no coincide con la versión actual → 409
    con la versión actual del recurso. Solo se modifican los campos presentes.

    Si MariaDB falla, los cambios se aplican al respaldo SQLite y quedan
    encolados en `pending_sync` (cabecera `X-Pending-Sync: true`).

    Args:
        dni: DNI del alumno a actualizar (tomado de la URL).
        datos: Campos a modificar (actualización parcial; `version` opcional).
        if_match: Cabecera If-Match con la versión leída.
        fuentes: Lista de repositorios activos (inyectada por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Returns:
        Alumno actualizado (la primera coincidencia entre las fuentes).

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente.
        HTTPException 409: Si la versión enviada no coincide.
        HTTPException 428: Si no se envía la versión.
        HTTPException 422: Si el cuerpo no es válido (p. ej. patronus vacío).
        HTTPException 503: Si alguna fuente clásica no responde.
    """
    version = _version_requerida(if_match, datos.version)
    actualizados: list[Alumno] = []
    pendiente = False
    desde_respaldo = False
    for repositorio in fuentes:
        if isinstance(repositorio, RepositorioMariaDB):
            try:
                alumno, pendiente_fuente = sincronizador.actualizar(dni, datos, version)
            except ConflictoVersion as error:
                raise _conflicto_version(error) from error
            pendiente = pendiente or pendiente_fuente
        else:
            try:
                alumno = repositorio.actualizar(dni, datos, version)
            except ConflictoVersion as error:
                raise _conflicto_version(error) from error
            except Exception as error:
                logger.error("Fallo actualizando %s: %s", repositorio.casa, error)
                raise HTTPException(
                    status_code=503,
                    detail=f"La fuente '{repositorio.casa}' no responde: {error}",
                ) from error
        if alumno is not None:
            if isinstance(alumno.casa, str) and "respaldo" in alumno.casa:
                desde_respaldo = True
            actualizados.append(alumno)
    if not actualizados:
        raise HTTPException(
            status_code=404,
            detail=f"No existe ningun alumno activo con DNI '{dni}'",
        )
    _marcar_pendiente(response, pendiente)
    _marcar_fuente(response, desde_respaldo)
    return actualizados[0]


# --------------------------------------------------------------------------- #
#  DELETE /alumnos/{dni}  — Baja lógica del alumno                            #
# --------------------------------------------------------------------------- #


@router.delete("/alumnos/{dni}", status_code=204, summary="Dar de baja un alumno (borrado lógico)")
def eliminar_alumno(
    dni: str,
    response: Response,
    if_match: str | None = Header(default=None, alias="If-Match"),
    version: int | None = Query(default=None, ge=1, description="Versión leída del recurso (alternativa a If-Match)."),
    fuentes: list[RepositorioAlumnos] = Depends(get_repositorios),
    sincronizador: Sincronizador = Depends(get_sincronizador),
):
    """Da de baja un alumno con **borrado lógico**: el registro se conserva
    marcado como inactivo (`activo = 0`, `fecha_baja` rellena).

    **Control de concurrencia**: debe enviarse la versión leída mediante
    `If-Match: N` o el parámetro de query `?version=N`. Sin versión → 428;
    versión desactualizada → 409 con la versión actual.

    Respuesta idempotente: si el alumno ya estaba dado de baja, se devuelve 204
    sin modificar nada. Si MariaDB falla, la baja se aplica al respaldo SQLite
    y queda encolada en `pending_sync` (cabecera `X-Pending-Sync: true`).

    Args:
        dni: DNI del alumno a dar de baja (tomado de la URL).
        if_match: Cabecera If-Match con la versión leída.
        version: Alternativa a If-Match vía query string.
        fuentes: Lista de repositorios activos (inyectada por FastAPI).
        sincronizador: Capa MariaDB + SQLite (inyectada por FastAPI).

    Raises:
        HTTPException 404: Si el DNI no existe en ninguna fuente.
        HTTPException 409: Si la versión enviada no coincide.
        HTTPException 428: Si no se envía la versión.
        HTTPException 503: Si alguna fuente clásica no responde.
    """
    version_enviada = _version_requerida(if_match, version)
    eliminado = False
    pendiente = False
    for repositorio in fuentes:
        if isinstance(repositorio, RepositorioMariaDB):
            try:
                borrado, pendiente_fuente = sincronizador.eliminar(dni, version_enviada)
            except ConflictoVersion as error:
                raise _conflicto_version(error) from error
            pendiente = pendiente or pendiente_fuente
        else:
            try:
                borrado = repositorio.eliminar(dni, version_enviada)
            except ConflictoVersion as error:
                raise _conflicto_version(error) from error
            except Exception as error:
                logger.error("Fallo dando de baja %s: %s", repositorio.casa, error)
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
    _marcar_pendiente(response, pendiente)
