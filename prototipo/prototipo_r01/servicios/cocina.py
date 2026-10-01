"""Servicio de sincronizacion: recorre los 4 orígenes y los persiste."""

import json
import sys

from acceso_datos.fuentes import ORIGENES, leer_origen

try:
    from persistencia import guardar_estudiantes  # tipo: ignore
    PERSISTENCIA = "real"
except Exception:
    from servicios._persistencia_fallback import guardar_estudiantes
    PERSISTENCIA = "fallback"


def sincronizar() -> dict:
    por_origen = {}
    total = 0
    for nombre in ORIGENES:
        filas = leer_origen(nombre)
        guardadas = guardar_estudiantes(filas)
        por_origen[nombre] = guardadas
        total += guardadas
    return {"por_origen": por_origen, "total": total}


if __name__ == "__main__":
    resultado = sincronizar()
    resultado["persistencia"] = PERSISTENCIA
    print(json.dumps(resultado, ensure_ascii=False, indent=2))
    sys.exit(0)
