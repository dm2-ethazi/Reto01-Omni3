"""Repositorio de alumnos para HSQLDB (Slytherin).

Hereda toda la implementación JDBC de `RepositorioJDBC`.
"""

from .jdbc import RepositorioJDBC

__author__ = "Henri Tejada"


class RepositorioHSQLDB(RepositorioJDBC):
    """Repositorio JDBC para la base de datos HSQLDB de Slytherin.

    Atributos:
        casa: Etiqueta "Slytherin" asignada a esta fuente.
        driver: Clase del driver JDBC de HSQLDB.
    """

    casa = "Slytherin"
    driver = "org.hsqldb.jdbc.JDBCDriver"
