from .jdbc import RepositorioJDBC


class RepositorioHSQLDB(RepositorioJDBC):
    casa = "Slytherin"
    driver = "org.hsqldb.jdbc.JDBCDriver"
