# Imagen de la API Alumnos (Reto R01) para Docker Compose.
# Con DB_ENGINES=mariadb no se necesita JVM (solo la usarían H2/HSQLDB/Derby).
FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy

WORKDIR /app

# Primero las dependencias (capa cacheable), después el código.
# pyproject declara readme = "README.md", así que hay que copiarlo también.
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-install-project

COPY src ./src
RUN uv sync --frozen

# El respaldo SQLite vive en /data/api (volumen 'api_data' del compose).
ENV SQLITE_PATH=/data/api/respaldo.db \
    DB_ENGINES=mariadb

EXPOSE 8000

CMD ["uv", "run", "--no-sync", "uvicorn", "proyecto_reto1.main:app", \
     "--host", "0.0.0.0", "--port", "8000"]
