"""Configuración de la API cargada desde el archivo .env.

Lee las credenciales y parámetros de conexión de las 5 bases de datos
desde un archivo `.env` ubicado en la raíz del proyecto `api/`.

Las rutas de `.env` y `JARS_DIR` se resuelven como rutas absolutas
respecto a la carpeta `api/`, por lo que la API funciona correctamente
independientemente del directorio desde el que se ejecute.
"""

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

__author__ = "Henri Tejada"

# Ruta raíz del proyecto api/ (donde está .env y jars/)
_API_DIR = Path(__file__).resolve().parent.parent.parent
_ENV_PATH = _API_DIR / ".env"


class Settings(BaseSettings):
    """Configuración de la API. Los valores se cargan del .env o de variables de entorno.

    Atributos:
        db_engines: Motores activos separados por coma (oracle,mariadb,h2,hsqldb,derby).
        jars_dir: Carpeta con los drivers JDBC. Se resuelve a ruta absoluta si es relativa.
        oracle_host / oracle_port / oracle_service / oracle_user / oracle_password:
            Conexión a Oracle (Ravenclaw).
        mariadb_host / mariadb_port / mariadb_database / mariadb_user / mariadb_password:
            Conexión a MariaDB (Hogwarts, base r01_central).
        h2_url / h2_user / h2_password: Conexión a H2 (Hufflepuff).
        hsqldb_url / hsqldb_user / hsqldb_password: Conexión a HSQLDB (Slytherin).
        derby_url / derby_user / derby_password: Conexión a Derby (Gryffindor).
    """

    model_config = SettingsConfigDict(env_file=str(_ENV_PATH), env_file_encoding="utf-8")

    # --- General ---
    db_engines: str = "oracle,mariadb,h2,hsqldb,derby"
    jars_dir: str = "jars"

    # --- Oracle (Ravenclaw) ---
    oracle_host: str = "localhost"
    oracle_port: int = 1521
    oracle_service: str = "FREE"
    oracle_user: str = "ravenclaw"
    oracle_password: str = ""

    # --- MariaDB (Hogwarts) ---
    mariadb_host: str = "localhost"
    mariadb_port: int = 3307
    mariadb_database: str = "r01_central"
    mariadb_user: str = "r01"
    mariadb_password: str = ""

    # --- H2 (Hufflepuff) ---
    h2_url: str = "jdbc:h2:tcp://localhost:9092/r01db"
    h2_user: str = "sa"
    h2_password: str = ""

    # --- HSQLDB (Slytherin) ---
    hsqldb_url: str = "jdbc:hsqldb:hsql://localhost:9001/slytherin"
    hsqldb_user: str = "SA"
    hsqldb_password: str = ""

    # --- Derby (Gryffindor) ---
    derby_url: str = "jdbc:derby://localhost:1527/db"
    derby_user: str = ""
    derby_password: str = ""

    def model_post_init(self, __context) -> None:
        """Resuelve jars_dir a ruta absoluta si se indicó una relativa."""
        if not Path(self.jars_dir).is_absolute():
            self.jars_dir = str(_API_DIR / self.jars_dir)


settings = Settings()
