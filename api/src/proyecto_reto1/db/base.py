"""Modelos de datos y protocolo del repositorio de alumnos.

Define los modelos Pydantic (Alumno, Patronus, AlumnoEntrada, AlumnoActualizacion)
y el Protocolo `RepositorioAlumnos` que cumplen todos los motores de base de datos.
"""

from typing import Protocol

from pydantic import BaseModel, Field, field_validator, model_validator


class Alumno(BaseModel):
    """Modelo de respuesta de un alumno.

    Atributos:
        id: DNI del alumno (clave primaria).
        nombre: Nombre del alumno.
        apellido: Apellido(s) del alumno, si se conoce.
        sexo: H (hombre), M (mujer) o S (desconocido/otro).
        patronus: Nombre del patronus en el idioma en que se guardó.
        patronus_url: URL de la imagen del patronus (buscada en PATRONUS_IMG).
        imagen: URL o ruta de la imagen del propio alumno.
        casa: Casa asignada por la API según la fuente de datos (no es columna).
    """

    id: str
    nombre: str
    apellido: str | None = None
    sexo: str | None = None
    patronus: str | None = None
    patronus_url: str | None = None
    imagen: str | None = None
    casa: str


class Patronus(BaseModel):
    """Modelo de un patronus del catálogo PATRONUS_IMG.

    Atributos:
        nom_esp: Nombre del patronus en español.
        nom_eng: Nombre del patronus en inglés.
        nom_eus: Nombre del patronus en euskera.
        url: URL de la imagen del patronus.
    """

    nom_esp: str
    nom_eng: str
    nom_eus: str
    url: str


class AlumnoEntrada(BaseModel):
    """Datos de entrada para crear un alumno (POST /alumnos).

    Atributos:
        id: DNI del alumno (1-10 caracteres, obligatorio).
        nombre: Nombre del alumno (1-100, obligatorio).
        apellido: Apellido(s), opcional.
        sexo: H, M o S, opcional.
        patronus: Nombre del patronus, opcional. Se valida contra PATRONUS_IMG.
        patronus_lang: Idioma del patronus: 'es' (español), 'en' (inglés) o 'eu' (euskera).
            Determina contra qué columna de PATRONUS_IMG se valida.
        imagen: URL o ruta de la imagen del alumno, opcional (máx. 500).
    """

    id: str = Field(min_length=1, max_length=10)
    nombre: str = Field(min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)
    patronus_lang: str = Field(default="es", pattern="^(es|en|eu)$")
    imagen: str | None = Field(default=None, max_length=500)


class Conflicto(Exception):
    """Excepción lanzada cuando se intenta crear un alumno con un DNI duplicado."""


# Mapeo de campos del modelo Python a columnas de la tabla ALUMNOS.
COLUMNAS_ALUMNO = {
    "nombre": "NOMBRE",
    "apellido": "APELLIDO",
    "sexo": "SEXO",
    "patronus": "PATRONUS",
    "imagen": "IMAGEN",
}


class AlumnoActualizacion(BaseModel):
    """Datos de entrada para actualizar un alumno (PUT /alumnos/{dni}).

    Permite actualizaciones parciales: solo se modifican los campos enviados.
    Los campos enviados como null o cadena vacía "" se guardan como NULL.

    Atributos:
        nombre: Nuevo nombre (obligatorio si se envía, no puede ser null).
        apellido: Nuevo apellido.
        sexo: Nuevo sexo (H, M o S).
        patronus: Nuevo patronus.
        imagen: Nueva imagen.
    """

    nombre: str | None = Field(default=None, min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)
    imagen: str | None = Field(default=None, max_length=500)

    @field_validator("nombre", mode="before")
    @classmethod
    def nombre_no_nulo(cls, valor):
        """Impide que el nombre sea null (obligatorio en la tabla)."""
        if valor is None:
            raise ValueError("nombre no puede ser nulo")
        return valor

    @field_validator("apellido", "sexo", "patronus", "imagen", mode="before")
    @classmethod
    def cadena_vacia_a_nulo(cls, valor):
        """Convierte cadenas vacías "" en None para guardar NULL en BD."""
        return None if valor == "" else valor

    @model_validator(mode="after")
    def al_menos_un_campo(self):
        """Garantiza que la petición incluya al menos un campo a actualizar."""
        if not self.model_fields_set:
            raise ValueError("Debe indicar al menos un campo a actualizar")
        return self


def campos_a_actualizar(datos: AlumnoActualizacion) -> dict[str, str | None]:
    """Extrae los campos enviados en la petición y los mapea a columnas SQL.

    Args:
        datos: Objeto AlumnoActualizacion validado.

    Returns:
        Dict {COLUMNA_SQL: valor} solo con los campos presentes en model_fields_set.
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
        str si hay valor, None si el valor es None o vacío.
    """
    if valor is None:
        return None
    if hasattr(valor, "read"):
        valor = valor.read()
    return str(valor)


class RepositorioAlumnos(Protocol):
    """Protocolo que definen todos los repositorios de alumnos (Oracle, MariaDB, JDBC).

    Cada motor de base de datos implementa estos métodos, permitiendo que la
    capa HTTP no distinga entre unos y otros.

    Atributos:
        casa: Nombre de la casa asignada a esta fuente de datos
            (Ravenclaw, Hogwarts, Hufflepuff, Slytherin, Gryffindor).
    """

    casa: str

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la fuente."""
        ...

    def buscar_por_nombre(self, prefijo: str) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo dado (case-insensitive)."""
        ...

    def obtener(self, id_alumno: str) -> Alumno | None:
        """Busca un alumno por DNI. Devuelve None si no existe."""
        ...

    def actualizar(self, id_alumno: str, datos: AlumnoActualizacion) -> Alumno | None:
        """Actualiza campos parciales de un alumno. Devuelve None si no existe."""
        ...

    def eliminar(self, id_alumno: str) -> bool:
        """Elimina un alumno por DNI. Devuelve True si se eliminó."""
        ...
