"""Escritura síncrona contra el estado anterior de una fuente."""

from contextlib import suppress

from .base import COLUMNAS_ALUMNO_SQL, Conflicto, EscrituraIncierta, fila_a_alumno

CAMPOS_ORIGEN = ("nombre", "apellido", "sexo", "patronus", "imagen")


def contenido(alumno):
    return tuple(getattr(alumno, c) for c in CAMPOS_ORIGEN) if alumno else None


def aplicar_origen(repo, previo, nuevo):
    """CAS síncrono; devuelve el dato releído. El worker nunca llama a esta función."""
    oracle = nuevo.origen == "oracle"
    with repo._conexion() as db:
        if hasattr(db, "jconn"):
            db.jconn.setAutoCommit(False)
        cursor = db.cursor()
        try:

            def ejecutar(sql, valores):
                if oracle:
                    partes = sql.split("?")
                    sql = partes[0] + "".join(
                        f":p{i}" + p for i, p in enumerate(partes[1:])
                    )
                    cursor.execute(sql, {f"p{i}": v for i, v in enumerate(valores)})
                else:
                    cursor.execute(sql, tuple(valores))

            ejecutar(
                f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS WHERE ID=?", [nuevo.id]
            )
            fila = cursor.fetchone()
            actual = fila_a_alumno(fila, repo.casa) if fila else None
            if contenido(actual) != contenido(previo):
                raise Conflicto(
                    "La fuente ha cambiado; recarga mediante sincronización"
                )
            condicion = "ID=?" + "".join(
                f" AND ({c.upper()}=? OR ({c.upper()} IS NULL AND ? IS NULL))"
                for c in CAMPOS_ORIGEN
            )
            anteriores = [previo.id]
            for campo in CAMPOS_ORIGEN:
                anteriores.extend([getattr(previo, campo)] * 2)
            if nuevo.activo:
                ejecutar(
                    "UPDATE ALUMNOS SET "
                    + ",".join(f"{c.upper()}=?" for c in CAMPOS_ORIGEN)
                    + " WHERE "
                    + condicion,
                    list(contenido(nuevo)) + anteriores,
                )
            else:
                ejecutar("DELETE FROM ALUMNOS WHERE " + condicion, anteriores)
            if cursor.rowcount != 1:
                raise Conflicto("Edición simultánea detectada en la fuente")
            if nuevo.activo:
                ejecutar(
                    f"SELECT {COLUMNAS_ALUMNO_SQL} FROM ALUMNOS WHERE ID=?", [nuevo.id]
                )
                releido = fila_a_alumno(cursor.fetchone(), repo.casa)
                nuevo = nuevo.model_copy(
                    update={campo: getattr(releido, campo) for campo in CAMPOS_ORIGEN}
                )
            try:
                db.commit()
            except Exception as exc:
                raise EscrituraIncierta(
                    "No se conoce el resultado del commit del origen"
                ) from exc
            return nuevo
        except BaseException:
            with suppress(Exception):
                db.rollback()
            raise
        finally:
            cursor.close()
