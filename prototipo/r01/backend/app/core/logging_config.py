"""Configuración de logging: consola + fichero rotativo en ``logs/``."""

from __future__ import annotations

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

_SECRET_HINTS = ("password", "passwd", "secret", "token", "authorization", "api_key")


def _redact(message: str) -> str:
    """Evita que secretos accidentales acaben en el fichero de log."""
    lowered = message.lower()
    for hint in _SECRET_HINTS:
        if hint in lowered:
            return "[REDACTADO: posible secreto en el mensaje]"
    return message


class RedactionFilter(logging.Filter):
    """Filtro sencillo de defensa en profundidad para no loguear secretos."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.msg = _redact(str(record.msg))
        return True


def configure_logging(log_dir: Path, level: str = "INFO") -> None:
    """Instala el handler de consola y el rotativo de ``logs/app.log``."""
    log_dir.mkdir(parents=True, exist_ok=True)

    root = logging.getLogger()
    root.setLevel(level.upper())
    root.handlers.clear()

    formatter = logging.Formatter(FORMAT, datefmt=DATE_FORMAT)

    console = logging.StreamHandler()
    console.setFormatter(formatter)

    file_handler = RotatingFileHandler(
        log_dir / "app.log",
        maxBytes=2 * 1024 * 1024,
        backupCount=5,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)

    for handler in (console, file_handler):
        handler.addFilter(RedactionFilter())
        root.addHandler(handler)

    logging.getLogger("uvicorn.access").setLevel(logging.WARNING)
    logging.getLogger("uvicorn.error").setLevel(logging.INFO)
