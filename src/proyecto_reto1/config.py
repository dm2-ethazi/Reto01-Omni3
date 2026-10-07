from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    db_engines: str = "oracle,mariadb,h2,hsqldb,derby"
    jars_dir: str = "jars"

    oracle_host: str = "localhost"
    oracle_port: int = 1521
    oracle_service: str = "FREE"
    oracle_user: str = "ravenclaw"
    oracle_password: str = ""

    mariadb_host: str = "localhost"
    mariadb_port: int = 3307
    mariadb_database: str = "r01_central"
    mariadb_user: str = "r01"
    mariadb_password: str = ""

    h2_url: str = "jdbc:h2:tcp://localhost:9092/r01db"
    h2_user: str = "sa"
    h2_password: str = ""

    hsqldb_url: str = "jdbc:hsqldb:hsql://localhost:9001/slytherin"
    hsqldb_user: str = "SA"
    hsqldb_password: str = ""

    derby_url: str = "jdbc:derby://localhost:1527/db"
    derby_user: str = ""
    derby_password: str = ""


settings = Settings()
