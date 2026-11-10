"""Transporte HTTP; las reglas y la coordinación viven en ServicioAlumnos."""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Header, Query, Request, Response
from pydantic import BaseModel, ConfigDict, Field

from ..db.base import Alumno, AlumnoActualizacion, AlumnoEntrada, Patronus
from ..services.alumnos import ErrorServicio, ServicioAlumnos

router = APIRouter()


def get_servicio(request: Request) -> ServicioAlumnos:
    return request.app.state.servicio


Servicio = Annotated[ServicioAlumnos, Depends(get_servicio)]


def version_esperada(version, if_match):
    if if_match is None:
        return version
    try:
        cabecera = int(if_match.strip('"'))
        if cabecera < 1 or (version is not None and version != cabecera):
            raise ValueError
        return cabecera
    except ValueError as exc:
        raise ErrorServicio(
            422,
            "If-Match debe contener la versión positiva leída, igual a version si se envían ambas",
        ) from exc


@router.get("/alumnos", response_model=list[Alumno])
@router.get("/students", response_model=list[Alumno])
def listar_alumnos(
    servicio: Servicio,
    response: Response,
    nombre: str | None = Query(default=None, min_length=1, max_length=100),
    lectura: Literal["auto", "sqlite"] = Query(
        default="auto", description="auto: MariaDB con respaldo SQLite; sqlite: consultar la copia SQLite"
    ),
):
    """Consulta la central persistida, aunque las fuentes o MariaDB no respondan."""
    alumnos, cache = servicio.listar_con_estado(nombre, lectura=lectura)
    response.headers["X-Data-Source"] = "sqlite-cache" if cache else "mariadb"
    return alumnos


@router.get("/alumnos/{dni}", response_model=Alumno)
@router.get("/students/{dni}", response_model=Alumno)
def obtener_alumno(dni: str, response: Response, servicio: Servicio):
    alumno = servicio.obtener(dni.strip().upper())
    response.headers["ETag"] = f'"{alumno.version}"'
    response.headers["X-Data-Source"] = (
        "sqlite-cache" if alumno.desde_cache else "mariadb"
    )
    return alumno


@router.get("/patronus", response_model=list[Patronus])
def listar_patronus(servicio: Servicio):
    return servicio.catalogo()


@router.post("/alumnos", response_model=Alumno, status_code=201)
@router.post("/students", response_model=Alumno, status_code=201)
def crear_alumno(datos: AlumnoEntrada, response: Response, servicio: Servicio):
    alumno = servicio.crear(datos)
    response.headers["ETag"] = f'"{alumno.version}"'
    response.headers["X-Sync-Pending"] = str(len(servicio.local.cola()))
    return alumno


@router.put("/alumnos/{dni}", response_model=Alumno)
@router.put("/students/{dni}", response_model=Alumno)
def actualizar_alumno(
    dni: str,
    datos: AlumnoActualizacion,
    response: Response,
    servicio: Servicio,
    if_match: str | None = Header(default=None),
):
    alumno = servicio.modificar(
        dni.strip().upper(),
        datos.model_dump(exclude_unset=True, exclude={"version"}),
        version_esperada(datos.version, if_match),
    )
    response.headers["ETag"] = f'"{alumno.version}"'
    response.headers["X-Sync-Pending"] = str(len(servicio.local.cola()))
    return alumno


@router.delete("/alumnos/{dni}", status_code=204)
@router.delete("/students/{dni}", status_code=204)
def eliminar_alumno(
    dni: str,
    servicio: Servicio,
    response: Response,
    version: int | None = Query(default=None, ge=1),
    if_match: str | None = Header(default=None),
):
    servicio.modificar(
        dni.strip().upper(), {}, version_esperada(version, if_match), baja=True
    )
    response.headers["X-Sync-Pending"] = str(len(servicio.local.cola()))


@router.get("/health")
def salud(servicio: Servicio, response: Response):
    estado = servicio.salud()
    if estado["centrales"]["sqlite"] == "caido":
        response.status_code = 503
    return estado


@router.get("/connectors")
def conectores(servicio: Servicio):
    return servicio.conectores()


@router.get("/connectors/{origen}/alumnos", response_model=list[Alumno])
def consultar_fuente(origen: str, servicio: Servicio):
    fuente = servicio.fuentes.get(origen)
    if fuente is None:
        raise ErrorServicio(404, "Conector no configurado")
    try:
        return fuente.listar()
    except Exception as exc:
        raise ErrorServicio(
            503, "La fuente no responde o no tiene el esquema preparado"
        ) from exc


class SolicitudSync(BaseModel):
    model_config = ConfigDict(extra="forbid")
    origenes: list[Literal["oracle", "h2", "hsqldb", "derby"]] | None = None
    ids: list[str] | None = Field(default=None, max_length=1000)
    resolver_conflictos: bool = False


@router.post("/sync")
def sincronizar(servicio: Servicio, datos: SolicitudSync | None = None):
    datos = datos or SolicitudSync()
    servicio.reintentar(forzar=True)
    return servicio.importar(datos.origenes, datos.ids, datos.resolver_conflictos)


@router.get("/admin/refresh-queue")
def cola(servicio: Servicio):
    return servicio.local.cola()


@router.post("/admin/retry")
def reintentar(servicio: Servicio):
    return servicio.relanzar()


@router.post("/admin/refresh-queue/{seq}/retry")
def relanzar_refresco(seq: int, servicio: Servicio):
    return servicio.relanzar(seq)


@router.get("/admin/coherence")
def coherencia(servicio: Servicio, response: Response):
    informe = servicio.coherencia()
    if not informe["completo"]:
        response.status_code = 503
    return informe


@router.get("/alumnos/{dni}/sync")
@router.get("/students/{dni}/sync")
def sincronizacion_alumno(dni: str, servicio: Servicio):
    alumno = servicio.obtener(dni.strip().upper())
    return {
        "id": alumno.id,
        "version": alumno.version,
        "sincronizacion": alumno.sincronizacion,
        "sincronizado_en": alumno.sincronizado_en,
        "desde_cache": alumno.desde_cache,
    }
