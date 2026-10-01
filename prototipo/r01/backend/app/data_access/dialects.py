"""Dialectos SQL: aíslan las diferencias entre los cuatro orígenes.

Todas las sentencias usan parámetros ``?`` (estilo JDBC) y sólo se generan
identificadores que superan la lista blanca.
"""

from __future__ import annotations

import re
from typing import Any

from app.core.exceptions import ValidacionError

_IDENT_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def safe_ident(name: str) -> str:
    """Valida un identificador SQL contra la lista blanca."""
    if not _IDENT_RE.match(name or ""):
        raise ValidacionError(f"Identificador SQL no permitido: {name!r}")
    return name


class Dialect:
    """Diferencias de sintaxis/etiquetas entre orígenes."""

    name: str = "base"
    upper_labels: bool = False

    @staticmethod
    def fetch_clause(offset: int, limit: int) -> str:
        """Paginación estándar SQL:2008 (válida en los cuatro orígenes)."""
        if offset < 0 or limit < 1:
            raise ValidacionError("Paginación inválida", {"offset": offset, "limit": limit})
        return f"OFFSET {int(offset)} ROWS FETCH FIRST {int(limit)} ROWS ONLY"

    @staticmethod
    def ident(name: str) -> str:
        """Identificador validado."""
        return safe_ident(name)

    @staticmethod
    def next_id_expression(table_ident: str) -> str:
        """Expresión portable para calcular el siguiente id."""
        return f"SELECT COALESCE(MAX(ID), 0) + 1 FROM {table_ident}"

    def normalize_label(self, label: str) -> str:
        """Etiqueta de columna devuelta por JDBC."""
        return label.upper() if self.upper_labels else label


class DerbyDialect(Dialect):
    name = "derby"


class HsqldbDialect(Dialect):
    name = "hsqldb"


class H2Dialect(Dialect):
    name = "h2"


class OracleDialect(Dialect):
    """Oracle devuelve las etiquetas de columna en MAYÚSCULAS."""

    name = "oracle"
    upper_labels = True


_DIALECTS: dict[str, type[Dialect]] = {
    "derby": DerbyDialect,
    "hsqldb": HsqldbDialect,
    "h2": H2Dialect,
    "oracle": OracleDialect,
}


def get_dialect(name: str) -> Dialect:
    """Devuelve el dialecto registrado o lanza un error de validación."""
    try:
        return _DIALECTS[name]()
    except KeyError as exc:
        raise ValidacionError(f"Dialecto desconocido: {name!r}") from exc


def map_row(
    columns: dict[str, str],
    labels: list[str],
    values: tuple[Any, ...],
    coercer: dict[str, type] | None = None,
) -> dict[str, Any]:
    """Convierte una fila JDBC en un dict del modelo.

    ``columns`` mapea modelo -> columna real; los valores se convierten al
    tipo del modelo para que la serialización a JSON sea siempre correcta.
    """
    lookup = {label.upper(): label for label in labels}
    reverse = {column.upper(): model for model, column in columns.items()}
    row: dict[str, Any] = {}
    for label, value in zip(labels, values, strict=False):
        model_key = reverse.get(lookup.get(label, label).upper())
        if model_key is None:
            continue
        expected = (coercer or {}).get(model_key)
        row[model_key] = _coerce(value, expected)
    return row


def _coerce(value: Any, expected: type | None) -> Any:
    if value is None:
        return None
    if expected is None:
        return value
    if expected is int:
        return int(value)
    return str(value)
