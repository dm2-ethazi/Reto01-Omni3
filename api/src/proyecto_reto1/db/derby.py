"""Repositorio de alumnos para Derby (Gryffindor).

Hereda toda la implementación JDBC de `RepositorioJDBC`.
"""

from .jdbc import RepositorioJDBC

__author__ = "Henri Tejada"


class RepositorioDerby(RepositorioJDBC):
    """Repositorio JDBC para la base de datos Derby de Gryffindor.

    Atributos:
        casa: Etiqueta "Gryffindor" asignada a esta fuente.
        driver: Clase del driver JDBC de Derby.
    """

    casa = "Gryffindor"
    driver = "org.apache.derby.client.ClientAutoloadedDriver"
