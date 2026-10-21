"""Configuración global del backend (variables de entorno + fichero .env)."""

from __future__ import annotations

import os
from collections.abc import Mapping
from functools import lru_cache
from pathlib import Path

from dotenv import dotenv_values
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[2]


def runtime_env() -> Mapping[str, str]:
    """Variables del proceso combinadas con las del fichero ``.env``.

    Sólo el fichero de ajustes de :class:`Settings` lee ``.env``
    automáticamente; los conectores JDBC resuelven sus credenciales por el
    nombre de variable declarado en ``config/connectors.yaml``, así que hay
    que ofrecérselas explícitamente.
    """
    archivo = dotenv_values(BACKEND_DIR / ".env")
    cargadas = {k: v for k, v in archivo.items() if v is not None}
    return {**os.environ, **cargadas}


class Settings(BaseSettings):
    """Ajustes de la aplicación.

    Los valores se resuelven en este orden: variables de entorno, fichero
    ``.env`` y, por último, los valores por defecto declarados aquí.
    """

    model_config = SettingsConfigDict(
        env_file=str(BACKEND_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "R01 - Backend de integracion"
    app_version: str = "1.0.0"
    environment: str = "dev"
    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "INFO"

    log_dir: Path = BACKEND_DIR / "logs"
    connectors_config: Path = BACKEND_DIR / "config" / "connectors.yaml"
    drivers_dir: Path = BACKEND_DIR / "drivers"
    sqlite_central_path: Path = BACKEND_DIR / "data" / "central.db"

    # --- Central doble: MariaDB (o MySQL) + SQLite -----------------------
    mariadb_host: str = "localhost"
    mariadb_port: int = 3306
    mariadb_user: str = "r01"
    mariadb_password: str = "r01pass"
    mariadb_database: str = "central"
    mariadb_enabled: bool = True

    sync_page_size: int = 100
    request_timeout_seconds: int = 15

    @property
    def mariadb_url(self) -> str:
        """URL SQLAlchemy del backend MariaDB/MySQL de la central."""
        return (
            f"mysql+pymysql://{self.mariadb_user}:{self.mariadb_password}"
            f"@{self.mariadb_host}:{self.mariadb_port}/{self.mariadb_database}"
            f"?charset=utf8mb4"
        )

    @property
    def sqlite_url(self) -> str:
        """URL SQLAlchemy del backend SQLite de la central."""
        return f"sqlite:///{self.sqlite_central_path}"


@lru_cache
def get_settings() -> Settings:
    """Devuelve la única instancia de :class:`Settings` del proceso."""
    return Settings()
