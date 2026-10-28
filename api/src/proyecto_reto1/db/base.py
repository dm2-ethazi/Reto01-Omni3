"""Modelos de datos y contrato compartido por todos los repositorios.

Este módulo es el corazón del "acuerdo" entre la capa HTTP y las bases de
datos. Contiene, en este orden:

1. **Modelos Pydantic** (`Alumno`, `Patronus`, `AlumnoEntrada`,
   `AlumnoActualizacion`): definen qué devuelve y qué acepta la API.
2. **Excepciones** `Conflicto` (DNI duplicado) y `ConflictoVersion`
   (control de concurrencia optimista).
3. **Helpers** para convertir filas de BD en modelos y para construir los
   `UPDATE` parciales.
4. **Protocolo** `RepositorioAlumnos`: la interfaz que cumplen los 5 motores
   (Oracle, MariaDB, H2, HSQLDB y Derby). Si un repositorio implementa estos
   métodos, la capa HTTP puede usarlo sin distinguir de qué motor es.
"""

from datetime import datetime
from typing import Protocol

from pydantic import BaseModel, Field, field_validator, model_validator

# --------------------------------------------------------------------------- #
#  1. Modelos Pydantic                                                        #
# --------------------------------------------------------------------------- #


class Alumno(BaseModel):
    """Modelo de respuesta de un alumno (GET/POST/PUT).

    Atributos:
        id: DNI del alumno (clave primaria).
        nombre: Nombre del alumno.
        apellido: Apellido(s) del alumno, si se conoce.
        sexo: H (hombre), M (mujer) o S (desconocido/otro).
        patronus: Nombre del patronus (se guarda en inglés, columna NOM_ENG).
        patronus_url: URL de la imagen del patronus (buscada en PATRONUS_IMG).
        imagen: URL o ruta de la imagen del propio alumno.
        casa: Casa asignada por la API según la fuente que respondió
            (no es una columna de la tabla).
        activo: Borrado lógico: True si el registro está vigente.
        fecha_baja: Momento en que se produjo la baja lógica, si aplica.
        version: Versión del registro para concurrencia optimista (se
            incrementa en cada modificación).
    """

    id: str
    nombre: str
    apellido: str | None = None
    sexo: str | None = None
    patronus: str | None = None
    patronus_url: str | None = None
    imagen: str | None = None
    casa: str
    activo: bool = True
    fecha_baja: datetime | None = None
    version: int = 1


class Patronus(BaseModel):
    """Modelo de un patronus del catálogo PATRONUS_IMG (solo MariaDB).

    Atributos:
        nom_eng: Nombre del patronus en inglés (columna NOM_ENG).
        url: URL de la imagen del patronus (columna URL).
    """

    nom_eng: str
    url: str


class AlumnoEntrada(BaseModel):
    """Datos de entrada para crear un alumno (POST /alumnos).

    El campo `patronus` es **obligatorio** (requisito del reto): debe enviarse
    el nombre en inglés y no puede quedar vacío. Se valida contra la columna
    NOM_ENG de PATRONUS_IMG.

    Atributos:
        id: DNI del alumno (1-10 caracteres, obligatorio).
        nombre: Nombre del alumno (1-100, obligatorio).
        apellido: Apellido(s), opcional.
        sexo: H, M o S, opcional.
        patronus: Nombre del patronus **en inglés**, obligatorio.
        imagen: URL o ruta de la imagen del alumno, opcional (máx. 500).
    """

    id: str = Field(min_length=1, max_length=10)
    nombre: str = Field(min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str = Field(min_length=1, max_length=100)
    imagen: str | None = Field(default=None, max_length=500)

    @field_validator("patronus", mode="before")
    @classmethod
    def patronus_obligatorio(cls, valor):
        """Exige un patronus con contenido (no null, no vacío ni en blanco)."""
        if valor is None:
            raise ValueError("patronus es obligatorio y no puede ser nulo")
        if isinstance(valor, str):
            valor = valor.strip()
            if not valor:
                raise ValueError("patronus es obligatorio y no puede quedar vacío")
        return valor


class AlumnoActualizacion(BaseModel):
    """Datos de entrada para actualizar un alumno (PUT /alumnos/{dni}).

    Permite actualizaciones parciales: solo se modifican los campos enviados.
    Los campos enviados como null o cadena vacía "" se guardan como NULL,
    salvo `patronus`: si se envía, no puede quedar vacío.

    Para el control de concurrencia optimista puede incluirse el campo
    `version` con la versión leída por el cliente (alternativamente se envía
    la cabecera `If-Match`).

    Atributos:
        nombre: Nuevo nombre (no puede ser null; la tabla lo exige).
        apellido: Nuevo apellido.
        sexo: Nuevo sexo (H, M o S).
        patronus: Nuevo patronus (en inglés); si se envía, es obligatorio.
        imagen: Nueva imagen.
        version: Versión leída del recurso (control de concurrencia).
    """

    nombre: str | None = Field(default=None, min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)
    imagen: str | None = Field(default=None, max_length=500)
    version: int | None = Field(default=None, ge=1)

    @field_validator("nombre", mode="before")
    @classmethod
    def nombre_no_nulo(cls, valor):
        """Impide que el nombre sea null (es obligatorio en la tabla)."""
        if valor is None:
            raise ValueError("nombre no puede ser nulo")
        return valor

    @field_validator("apellido", "sexo", "patronus", "imagen", mode="before")
    @classmethod
    def cadena_vacia_a_nulo(cls, valor):
        """Convierte las cadenas vacías "" en None para guardar NULL en BD."""
        return None if valor == "" else valor

    @model_validator(mode="after")
    def al_menos_un_campo(self):
        """Valida las reglas globales del PUT.

        - Debe enviarse al menos un campo a actualizar (la `version` no cuenta).
        - Si se envía `patronus`, no puede quedar vacío.
        """
        if not (self.model_fields_set - {"version"}):
            raise ValueError("Debe indicar al menos un campo a actualizar")
        if "patronus" in self.model_fields_set and self.patronus is None:
            raise ValueError("Si envía patronus no puede quedar vacío")
        return self


# --------------------------------------------------------------------------- #
#  2. Excepciones                                                             #
# --------------------------------------------------------------------------- #


class Conflicto(Exception):
    """Se lanza al intentar crear un alumno con un DNI que ya existe."""


class ConflictoVersion(Exception):
    """Se lanza cuando la versión enviada no coincide con la actual.

    Atributos:
        version_actual: Versión vigente del recurso en la base de datos.
    """

    def __init__(self, mensaje: str, version_actual: int | None = None):
        super().__init__(mensaje)
        self.version_actual = version_actual


# --------------------------------------------------------------------------- #
#  3. Helpers compartidos por los repositorios                                #
# --------------------------------------------------------------------------- #

# Columnas de la tabla ALUMNOS "clásica" (Oracle, H2, HSQLDB, Derby), en el
# orden en que se hacen los SELECT.
COLUMNAS_ALUMNO_SQL = "ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN"

# Columnas de la tabla ALUMNOS v2 (MariaDB y SQLite), con borrado lógico y
# control de concurrencia.
COLUMNAS_ALUMNO_SQL_V2 = (
    "ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN, ACTIVO, FECHA_BAJA, VERSION"
)

# Mapeo de campos del modelo Python a columnas de la tabla ALUMNOS.
COLUMNAS_ALUMNO = {
    "nombre": "NOMBRE",
    "apellido": "APELLIDO",
    "sexo": "SEXO",
    "patronus": "PATRONUS",
    "imagen": "IMAGEN",
}


def campos_a_actualizar(datos: AlumnoActualizacion) -> dict[str, str | None]:
    """Extrae los campos enviados en la petición y los mapea a columnas SQL.

    Args:
        datos: Objeto AlumnoActualizacion ya validado por Pydantic.

    Returns:
        Dict {COLUMNA_SQL: valor} solo con los campos presentes en la petición
        (los omitidos no aparecen, así que no se tocan en el UPDATE).
    """
    cambios = datos.model_dump(exclude_unset=True)
    return {
        COLUMNAS_ALUMNO[campo]: valor
        for campo, valor in cambios.items()
        if campo in COLUMNAS_ALUMNO
    }


def texto(valor) -> str | None:
    """Convierte un valor de BD a string, manejando None y blobs/LOBs.

    Args:
        valor: Valor devuelto por el driver de la base de datos.

    Returns:
        str si hay valor, None si el valor es None.
    """
    if valor is None:
        return None
    if hasattr(valor, "read"):
        valor = valor.read()
    return str(valor)


def a_datetime(valor) -> datetime | None:
    """Convierte un valor de fecha de BD a `datetime`.

    Acepta `datetime`, `date`, ISO strings (SQLite) o None.

    Args:
        valor: Valor de la columna FECHA_BAJA.

    Returns:
        datetime normalizado o None.
    """
    if valor is None or isinstance(valor, datetime):
        return valor
    if isinstance(valor, str):
        return datetime.fromisoformat(valor)
    # Cualquier otro tipo (p. ej. date) se convierte vía isoformat().
    return datetime.fromisoformat(valor.isoformat())


def fila_a_alumno(fila, casa: str) -> Alumno:
    """Convierte una fila con las 6 columnas clásicas de ALUMNOS en un modelo.

    Todas las bases de datos "clásicas" devuelven las columnas en el orden de
    COLUMNAS_ALUMNO_SQL. Los campos nuevos (activo, version) toman sus valores
    por defecto: registro activo en versión 1.

    Args:
        fila: Tupla (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN).
        casa: Etiqueta de la casa que respondió (Ravenclaw, Hogwarts, etc.).

    Returns:
        Modelo Alumno listo para serializar a JSON.
    """
    return Alumno(
        id=texto(fila[0]) or "",
        nombre=texto(fila[1]) or "",
        apellido=texto(fila[2]),
        sexo=texto(fila[3]),
        patronus=texto(fila[4]),
        imagen=texto(fila[5]),
        casa=casa,
    )


def fila_a_alumno_v2(fila, casa: str) -> Alumno:
    """Convierte una fila con las 9 columnas v2 de ALUMNOS en un modelo.

    Args:
        fila: Tupla (ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN, ACTIVO,
            FECHA_BAJA, VERSION) — el orden de COLUMNAS_ALUMNO_SQL_V2.
        casa: Etiqueta de la casa que respondió.

    Returns:
        Modelo Alumno listo para serializar a JSON.
    """
    return Alumno(
        id=texto(fila[0]) or "",
        nombre=texto(fila[1]) or "",
        apellido=texto(fila[2]),
        sexo=texto(fila[3]),
        patronus=texto(fila[4]),
        imagen=texto(fila[5]),
        casa=casa,
        activo=bool(fila[6]),
        fecha_baja=a_datetime(fila[7]),
        version=int(fila[8] or 1),
    )


# --------------------------------------------------------------------------- #
#  4. Contrato de los repositorios                                            #
# --------------------------------------------------------------------------- #


class RepositorioAlumnos(Protocol):
    """Contrato que cumplen todos los repositorios de alumnos.

    Cada motor de base de datos implementa estos métodos, permitiendo que la
    capa HTTP (`api/alumnos.py`) no distinga entre unos y otros.

    Los parámetros opcionales `incluir_inactivos` y `version` pertenecen al
    borrado lógico y al control de concurrencia optimista (MariaDB y SQLite);
    los motores clásicos los aceptan y, si su esquema no tiene esas columnas,
    los ignoran sin romper el contrato.

    Atributos:
        casa: Nombre de la casa asignada a esta fuente de datos
            (Ravenclaw, Hogwarts, Hufflepuff, Slytherin o Gryffindor).
    """

    casa: str

    def listar(self, incluir_inactivos: bool = False) -> list[Alumno]:
        """Devuelve los alumnos de la fuente (activos por defecto)."""
        ...

    def buscar_por_nombre(
        self, prefijo: str, incluir_inactivos: bool = False
    ) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo dado (sin distinguir mayúsculas)."""
        ...

    def obtener(self, id_alumno: str, incluir_inactivos: bool = False) -> Alumno | None:
        """Busca un alumno por DNI. Devuelve None si no existe o está inactivo."""
        ...

    def actualizar(
        self,
        id_alumno: str,
        datos: AlumnoActualizacion,
        version: int | None = None,
    ) -> Alumno | None:
        """Actualiza campos parciales de un alumno. Devuelve None si no existe.

        Raises:
            ConflictoVersion: Si `version` se envió y no coincide con la actual.
        """
        ...

    def eliminar(self, id_alumno: str, version: int | None = None) -> bool:
        """Baja lógica de un alumno (activo = 0). Devuelve False si no existe.

        Raises:
            ConflictoVersion: Si `version` se envió y no coincide con la actual.
        """
        ...
