ORIGENES = ("derby", "hsqldb", "h2", "oracle")

_FUENTES = {
    "derby": [
        {"nombre": "Harry Potter", "casa": "Gryffindor"},
        {"nombre": "Hermione Granger", "casa": "Gryffindor"},
        {"nombre": "Draco Malfoy", "casa": "Slytherin"},
    ],
    "hsqldb": [
        {"nombre": "Ron Weasley", "casa": "Gryffindor"},
        {"nombre": "Luna Lovegood", "casa": "Ravenclaw"},
        {"nombre": "Neville Longbottom", "casa": "Gryffindor"},
    ],
    "h2": [
        {"nombre": "Ginny Weasley", "casa": "Gryffindor"},
        {"nombre": "Cho Chang", "casa": "Ravenclaw"},
        {"nombre": "Cedric Diggory", "casa": "Hufflepuff"},
    ],
    "oracle": [
        {"nombre": "Severus Snape", "casa": "Slytherin"},
        {"nombre": "Albus Dumbledore", "casa": "Gryffindor"},
        {"nombre": "Newt Scamander", "casa": "Hufflepuff"},
    ],
}


def leer_origen(nombre: str) -> list[dict]:
    if nombre not in _FUENTES:
        raise KeyError(f"origen desconocido: {nombre!r} (validos: {', '.join(ORIGENES)})")
    return [dict(fila, origen=nombre) for fila in _FUENTES[nombre]]
