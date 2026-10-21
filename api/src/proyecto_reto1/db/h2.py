"""Repositorio de alumnos para H2 (Hufflepuff).

Hereda toda la implementación JDBC de `RepositorioJDBC`.
"""


from .jdbc import RepositorioJDBC


class RepositorioH2(RepositorioJDBC):
    """Repositorio JDBC para la base de datos H2 de Hufflepuff.

    Atributos:
        casa: Etiqueta "Hufflepuff" asignada a esta fuente.
        driver: Clase del driver JDBC de H2.
    """

    casa = "Hufflepuff"
    driver = "org.h2.Driver"
