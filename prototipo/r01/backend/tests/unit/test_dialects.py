"""Pruebas unitarias de los dialectos SQL (años entre orígenes)."""

from __future__ import annotations

import pytest

from app.core.exceptions import ValidacionError
from app.data_access.dialects import (
    get_dialect,
    map_row,
    safe_ident,
)

COLUMNS = {"id": "ID", "dni": "DNI", "nombre": "NOMBRE", "casa": "CASA", "patronus": "PATRONUS"}


@pytest.mark.parametrize("dialecto", ["derby", "hsqldb", "h2", "oracle"])
def test_get_dialect_conoce_los_cuatro_origenes(dialecto):
    assert get_dialect(dialecto).name == dialecto


def test_get_dialect_desconocido():
    with pytest.raises(ValidacionError):
        get_dialect("mysql")


def test_safe_ident_acepta_y_rechaza():
    assert safe_ident("estudiantes") == "estudiantes"
    for malo in ("estudiantes; DROP TABLE x", "1abc", "a b", "", None):
        with pytest.raises(ValidacionError):
            safe_ident(malo)  # type: ignore[arg-type]


def test_paginacion_estandar_y_validacion():
    dialecto = get_dialect("oracle")
    assert dialecto.fetch_clause(10, 5) == "OFFSET 10 ROWS FETCH FIRST 5 ROWS ONLY"
    with pytest.raises(ValidacionError):
        dialecto.fetch_clause(-1, 5)
    with pytest.raises(ValidacionError):
        dialecto.fetch_clause(0, 0)


def test_expresion_de_siguiente_id():
    assert get_dialect("derby").next_id_expression("T") == "SELECT COALESCE(MAX(ID), 0) + 1 FROM T"


def test_etiquetas_segun_dialecto():
    assert get_dialect("oracle").normalize_label("dni") == "DNI"
    assert get_dialect("h2").normalize_label("dni") == "dni"


def test_map_row_con_etiquetas_en_mayusculas_y_coercion():
    fila = map_row(
        COLUMNS,
        ["ID", "DNI", "NOMBRE", "CASA", "PATRONUS"],
        (1, "12345678Z", "Ana", "Ravenclaw", "Búho"),
        coercer={"id": int},
    )
    assert fila == {"id": 1, "dni": "12345678Z", "nombre": "Ana", "casa": "Ravenclaw", "patronus": "Búho"}


def test_map_row_ignora_columnas_desconocidas():
    fila = map_row(COLUMNS, ["ID", "SOBRENOMBRE"], (2, "x"), coercer={"id": int})
    assert fila == {"id": 2}
