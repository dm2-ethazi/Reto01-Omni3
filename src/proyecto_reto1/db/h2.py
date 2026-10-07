from .jdbc import RepositorioJDBC


class RepositorioH2(RepositorioJDBC):
    casa = "Hufflepuff"
    driver = "org.h2.Driver"
