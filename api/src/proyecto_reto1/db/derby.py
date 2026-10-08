from .jdbc import RepositorioJDBC


class RepositorioDerby(RepositorioJDBC):
    casa = "Gryffindor"
    driver = "org.apache.derby.client.ClientAutoloadedDriver"
