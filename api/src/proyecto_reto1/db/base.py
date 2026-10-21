"""Modelos de datos y contrato compartido por todos los repositorios.

Este módulo es el corazón del "acuerdo" entre la capa HTTP y las bases de
datos. Contiene, en este orden:

1. **Modelos Pydantic** (`Alumno`, `Patronus`, `AlumnoEntrada`,
   `AlumnoActualizacion`): definen qué devuelve y qué acepta la API.
2. **Excepción** `Conflicto`: usada por los repositorios para señalar DNI
   duplicado.
3. **Helpers** para convertir filas de BD en modelos y para construir los
   `UPDATE` parciales.
4. **Protocolo** `RepositorioAlumnos`: la interfaz que cumplen los 5 motores
   (Oracle, MariaDB, H2, HSQLDB y Derby). Si un repositorio implementa estos
   métodos, la capa HTTP puede usarlo sin distinguir de qué motor es.
"""

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
    """Modelo de un patronus del catálogo PATRONUS_IMG (solo MariaDB).

    Atributos:
        nom_eng: Nombre del patronus en inglés (columna NOM_ENG).
        url: URL de la imagen del patronus (columna URL).
    """

    nom_eng: str
    url: str


class AlumnoEntrada(BaseModel):
    """Datos de entrada para crear un alumno (POST /alumnos).

    Atributos:
        id: DNI del alumno (1-10 caracteres, obligatorio).
        nombre: Nombre del alumno (1-100, obligatorio).
        apellido: Apellido(s), opcional.
        sexo: H, M o S, opcional.
        patronus: Nombre del patronus **en inglés**, opcional.
            Se valida contra la columna NOM_ENG de PATRONUS_IMG.
        imagen: URL o ruta de la imagen del alumno, opcional (máx. 500).
    """

    id: str = Field(min_length=1, max_length=10)
    nombre: str = Field(min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)
    imagen: str | None = Field(default=None, max_length=500)


class AlumnoActualizacion(BaseModel):
    """Datos de entrada para actualizar un alumno (PUT /alumnos/{dni}).

    Permite actualizaciones parciales: solo se modifican los campos enviados.
    Los campos enviados como null o cadena vacía "" se guardan como NULL.

    Atributos:
        nombre: Nuevo nombre (no puede ser null; la tabla lo exige).
        apellido: Nuevo apellido.
        sexo: Nuevo sexo (H, M o S).
        patronus: Nuevo patronus (en inglés).
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
        """Garantiza que la petición incluya al menos un campo a actualizar."""
        if not self.model_fields_set:
            raise ValueError("Debe indicar al menos un campo a actualizar")
        return self


# --------------------------------------------------------------------------- #
#  2. Excepciones                                                             #
# --------------------------------------------------------------------------- #


class Conflicto(Exception):
    """Se lanza al intentar crear un alumno con un DNI que ya existe."""


# --------------------------------------------------------------------------- #
#  3. Helpers compartidos por los repositorios                                #
# --------------------------------------------------------------------------- #

# Columnas de la tabla ALUMNOS, en el orden en que se hacen los SELECT.
COLUMNAS_ALUMNO_SQL = "ID, NOMBRE, APELLIDO, SEXO, PATRONUS, IMAGEN"

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


def fila_a_alumno(fila, casa: str) -> Alumno:
    """Convierte una fila con las 6 columnas de ALUMNOS en un modelo Alumno.

    Todas las bases de datos devuelven las columnas en el orden de
    COLUMNAS_ALUMNO_SQL, por lo que este helper sirve para los 5 motores.

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


# --------------------------------------------------------------------------- #
#  4. Contrato de los repositorios                                            #
# --------------------------------------------------------------------------- #


class RepositorioAlumnos(Protocol):
    """Contrato que cumplen todos los repositorios de alumnos.

    Cada motor de base de datos implementa estos métodos, permitiendo que la
    capa HTTP (`api/alumnos.py`) no distinga entre unos y otros.

    Atributos:
        casa: Nombre de la casa asignada a esta fuente de datos
            (Ravenclaw, Hogwarts, Hufflepuff, Slytherin o Gryffindor).
    """

    casa: str

    def listar(self) -> list[Alumno]:
        """Devuelve todos los alumnos de la fuente."""
        ...

    def buscar_por_nombre(self, prefijo: str) -> list[Alumno]:
        """Busca alumnos cuyo nombre empiece por el prefijo dado (sin distinguir mayúsculas)."""
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
