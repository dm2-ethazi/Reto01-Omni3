"""Pruebas unitarias de la transformación de filas de origen."""

from __future__ import annotations

import pytest

from app.core.exceptions import ValidacionError
from app.services.transform import (
    normalizar_dni,
    normalizar_espacios,
    normalizar_nombre,
    transform_row,
    validar_dni,
)

FILA_OK = {
    "id": 7,
    "dni": " 12345678z ",
    "nombre": "  Ana   López  ",
    "casa": "  Ravenclaw ",
    "patronus": "  Búho  ",
}


def test_normalizaciones():
    assert normalizar_espacios("  a   b  ") == "a b"
    assert normalizar_dni(" 12345678z ") == "12345678Z"
    assert normalizar_nombre("\tAna\tLópez ") == "Ana López"


@pytest.mark.parametrize("dni", ["12345678Z", "X1234567Z", "Y0000001T", "12345678A"])
def test_validar_dni_formatos_correctos(dni):
    assert validar_dni(dni) == dni


@pytest.mark.parametrize("dni", ["1234567", "12345678ZZ", "1234567A", "ABC12345Z", ""])
def test_validar_dni_formatos_incorrectos(dni):
    with pytest.raises(ValidacionError):
        validar_dni(dni)


def test_transform_row_normaliza_y_anade_origen():
    fila = transform_row(FILA_OK, "derby")
    assert fila == {
        "origen": "derby",
        "id_origen": 7,
        "dni": "12345678Z",
        "nombre": "Ana López",
        "casa": "Ravenclaw",
        "patronus": "Búho",
    }


def test_transform_row_detecta_campos_faltantes():
    with pytest.raises(ValidacionError) as exc:
        transform_row({"id": 1, "dni": "12345678Z", "nombre": "Ana"}, "h2")
    assert exc.value.status_code == 400
    assert set(exc.value.detalle["faltantes"]) == {"casa", "patronus"}


def test_transform_row_id_no_numerico():
    with pytest.raises(ValidacionError):
        transform_row({**FILA_OK, "id": "ABC"}, "derby")


def test_transform_row_dni_invalido():
    with pytest.raises(ValidacionError):
        transform_row({**FILA_OK, "dni": "0000"}, "derby")


def test_transform_row_nombre_demasiado_largo():
    with pytest.raises(ValidacionError) as exc:
        transform_row({**FILA_OK, "nombre": "x" * 101}, "derby")
    assert exc.value.detalle["maximo"] == 100
