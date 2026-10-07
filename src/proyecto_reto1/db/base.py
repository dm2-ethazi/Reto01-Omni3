from typing import Protocol

from pydantic import BaseModel, Field


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
