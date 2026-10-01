"""Transformación de filas de origen al modelo unificado de la central.

Reglas aplicadas (todas centralizadas aquí, sin duplicarlas por conector):

1. ``trim`` y colapso de espacios repetidos.
2. DNI en mayúsculas y validado con formato español.
3. Longitudes según el modelo (100/30/50).
4. Campo ``origen`` añadido para trazar de dónde viene cada fila.
"""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

from app.core.exceptions import ValidacionError

_DNI_RE = re.compile(r"^(?:[0-9]{8}[A-Z]|[XYZ][0-9]{7}[A-Z])$")

LIMITE_NOMBRE = 100
LIMITE_CASA = 30
LIMITE_PATRONUS = 50


def normalizar_espacios(valor: str) -> str:
    """Elimina espacios sobrantes al principio/final y colapsa los internos."""
    return " ".join(str(valor).split())


def normalizar_dni(valor: str) -> str:
    """DNI/NIE en mayúsculas sin espacios."""
    return normalizar_espacios(valor).upper()


def normalizar_nombre(valor: str) -> str:
    """Nombre canónico: espacios simples y sin espacios al borde."""
    return normalizar_espacios(valor)


def validar_dni(dni: str) -> str:
    """Valida el formato ``12345678Z`` / ``X1234567Z`` y lo devuelve normalizado."""
    normalizado = normalizar_dni(dni)
    if not _DNI_RE.match(normalizado):
        raise ValidacionError(
            "El DNI no tiene un formato válido (8 dígitos + letra o X/Y/Z + 7 dígitos + letra)",
            {"dni": normalizado},
        )
    return normalizado


def _limitar(valor: str, campo: str, limite: int) -> str:
    if len(valor) > limite:
        raise ValidacionError(
            f"El campo '{campo}' supera los {limite} caracteres",
            {"campo": campo, "longitud": len(valor), "maximo": limite},
        )
    return valor


def transform_row(raw: Mapping[str, Any], origen: str) -> dict[str, Any]:
    """Convierte una fila del origen en una fila de la central.

    Lanza :class:`ValidacionError` si la fila es incompleta o inválida; el
    llamador decide si la cuenta como error y continúa.
    """
    faltantes = [campo for campo in ("id", "dni", "nombre", "casa", "patronus") if not raw.get(campo) and raw.get(campo) != 0]
    if faltantes:
        raise ValidacionError(
            "Fila incompleta en el origen",
            {"origen": origen, "faltantes": faltantes},
        )

    try:
        id_origen = int(raw["id"])
    except (TypeError, ValueError) as exc:
        raise ValidacionError("El id de origen no es numérico", {"valor": raw["id"]}) from exc

    return {
        "origen": origen,
        "id_origen": id_origen,
        "dni": validar_dni(raw["dni"]),
        "nombre": _limitar(normalizar_nombre(raw["nombre"]), "nombre", LIMITE_NOMBRE),
        "casa": _limitar(normalizar_espacios(raw["casa"]), "casa", LIMITE_CASA),
        "patronus": _limitar(normalizar_espacios(raw["patronus"]), "patronus", LIMITE_PATRONUS),
    }
