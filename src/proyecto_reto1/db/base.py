from typing import Protocol

from pydantic import BaseModel, Field, field_validator, model_validator


class Alumno(BaseModel):
    id: str
    nombre: str
    apellido: str | None = None
    sexo: str | None = None
    patronus: str | None = None
    casa: str


class AlumnoEntrada(BaseModel):
    id: str = Field(min_length=1, max_length=10)
    nombre: str = Field(min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)


class Conflicto(Exception):
    pass


COLUMNAS_ALUMNO = {
    "nombre": "NOMBRE",
    "apellido": "APELLIDO",
    "sexo": "SEXO",
    "patronus": "PATRONUS",
}


class AlumnoActualizacion(BaseModel):
    nombre: str | None = Field(default=None, min_length=1, max_length=100)
    apellido: str | None = Field(default=None, max_length=100)
    sexo: str | None = Field(default=None, pattern="^[HMS]$")
    patronus: str | None = Field(default=None, max_length=100)

    @field_validator("nombre", mode="before")
    @classmethod
    def nombre_no_nulo(cls, valor):
        if valor is None:
            raise ValueError("nombre no puede ser nulo")
        return valor

    @field_validator("apellido", "sexo", "patronus", mode="before")
    @classmethod
    def cadena_vacia_a_nulo(cls, valor):
        return None if valor == "" else valor

    @model_validator(mode="after")
    def al_menos_un_campo(self):
        if not self.model_fields_set:
            raise ValueError("Debe indicar al menos un campo a actualizar")
        return self


def campos_a_actualizar(datos: AlumnoActualizacion) -> dict[str, str | None]:
    """Devuelve {COLUMNA: valor} solo con los campos enviados en la peticion."""
    cambios = datos.model_dump(exclude_unset=True)
    return {
        COLUMNAS_ALUMNO[campo]: valor
        for campo, valor in cambios.items()
        if campo in COLUMNAS_ALUMNO
    }


def texto(valor) -> str | None:
    if valor is None:
        return None
    if hasattr(valor, "read"):
        valor = valor.read()
    return str(valor)


class RepositorioAlumnos(Protocol):
    casa: str

    def listar(self) -> list[Alumno]: ...

    def obtener(self, id_alumno: str) -> Alumno | None: ...

    def actualizar(self, id_alumno: str, datos: AlumnoActualizacion) -> Alumno | None: ...

    def eliminar(self, id_alumno: str) -> bool: ...
