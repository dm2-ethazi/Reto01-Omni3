"""Una instalación local por usuario, compartida por todos sus clones del repo.

Conserva credenciales y SQLite. La migración de instalaciones anteriores verifica
las claves antes de adoptarlas y nunca borra volúmenes ni cambia sus contraseñas.
"""

import argparse
import hashlib
import json
import os
import secrets
import sqlite3
import subprocess
import sys
import time
from contextlib import closing
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

from dotenv import dotenv_values, set_key

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "r01-adat-validation"


def directorio_compartido():
    if os.environ.get("R01_LOCAL_HOME"):
        return Path(os.environ["R01_LOCAL_HOME"]).resolve()
    base = Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local/share"))
    return base / "Omni3" / "R01"


def ejecutar_docker(*args):
    result = subprocess.run(["docker", *args], capture_output=True, text=True, encoding="utf-8", timeout=45)
    if result.returncode:
        raise RuntimeError("Docker no está disponible; abre Docker Desktop")
    return result.stdout.strip()


def instalacion_anterior():
    ids = ejecutar_docker("ps", "-a", "--filter", f"label=com.docker.compose.project={PROJECT}", "-q").split()
    if not ids:
        return []
    return json.loads(ejecutar_docker("inspect", *ids))


def credenciales_validas(config):
    """Prueba solo el login: no imprime claves ni ejecuta cambios de datos."""
    import oracledb
    import pymysql
    try:
        with pymysql.connect(host=config["MARIADB_HOST"], port=int(config["MARIADB_PORT"]),
                             user=config["MARIADB_USER"], password=config["MARIADB_PASSWORD"],
                             database=config["MARIADB_DATABASE"], connect_timeout=3,
                             read_timeout=3, write_timeout=3):
            pass
        with oracledb.connect(host=config["ORACLE_HOST"], port=int(config["ORACLE_PORT"]),
                              service_name=config["ORACLE_SERVICE"], user=config["ORACLE_USER"],
                              password=config["ORACLE_PASSWORD"], tcp_connect_timeout=3,
                              retry_count=0):
            pass
        return True
    except Exception:
        return False


def esperar_servicios(containers):
    """Arranca los contenedores existentes sin recrear sus variables/volúmenes."""
    relevantes = [c for c in containers if c["Config"]["Labels"].get(
        "com.docker.compose.service") in {"mariadb", "oracle"}]
    for c in relevantes:
        if not c["State"]["Running"]:
            ejecutar_docker("start", c["Id"])
    limite = time.monotonic() + 240
    while relevantes:
        actuales = json.loads(ejecutar_docker("inspect", *(c["Id"] for c in relevantes)))
        if all(c["State"].get("Health", {}).get("Status", "healthy") == "healthy" for c in actuales):
            return
        if time.monotonic() >= limite:
            raise RuntimeError("La instalación anterior no está lista; revisa docker compose ps")
        print("[local] Esperando que Oracle y MariaDB de la instalación anterior estén listos...", flush=True)
        time.sleep(5)


def candidatos(root, containers, legacy_file=None):
    """Prioriza el .env ligado al volumen, no uno aleatorio de un clon nuevo."""
    rutas = []
    if legacy_file:
        rutas.append((Path(legacy_file), root))
    for c in containers:
        directorio = c["Config"]["Labels"].get("com.docker.compose.project.working_dir")
        if directorio:
            repo = Path(directorio)
            rutas.append((repo / "api/data/validation.env", repo))
    rutas.append((root / "api/data/validation.env", root))
    vistos = set()
    for path, repo in rutas:
        if path.exists() and path.resolve() not in vistos:
            vistos.add(path.resolve())
            yield dotenv_values(path), repo


def copiar_sqlite(origen, destino):
    """Incluye el WAL y la cola en una copia consistente; mantiene el original."""
    if not origen.exists() or destino.exists():
        return
    temporal = destino.with_suffix(".migrating")
    try:
        with closing(sqlite3.connect(origen.resolve().as_uri() + "?mode=ro", uri=True)) as src:
            with closing(sqlite3.connect(temporal)) as dst:
                src.backup(dst)
        temporal.replace(destino)
    finally:
        temporal.unlink(missing_ok=True)


def preparar(root=ROOT, folder=None, legacy_file=None):
    folder = folder or directorio_compartido()
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / "local.env"
    if target.exists():
        # No generar contraseñas otra vez, aunque se ejecute desde otro clon.
        print(f"[local] Reutilizando la configuración compartida: {target}", flush=True)
        return target
    config = dict(dotenv_values(root / "api/.env.demo.example"))
    containers = instalacion_anterior()
    elegido, origen = None, None
    if containers:
        esperar_servicios(containers)
        for candidato, repo in candidatos(root, containers, legacy_file):
            completo = {**config, **candidato}
            if credenciales_validas(completo):
                elegido, origen = completo, repo
                break
        if elegido is None:
            raise RuntimeError(
                "Ya existen bases de una instalación anterior y no se ha encontrado su "
                "configuración válida. Ejecuta local-environment.py --legacy-env RUTA_AL_ENV_ORIGINAL. "
                "No borres los volúmenes ni generes nuevas claves para esas bases."
            )
        config = elegido
        print("[local] Credenciales existentes verificadas; conservando datos y cola.", flush=True)
    else:
        # Sin contenedores pero con volúmenes: no crear otra identidad encima.
        volumenes = ejecutar_docker("volume", "ls", "--filter", f"label=com.docker.compose.project={PROJECT}", "-q")
        if volumenes:
            raise RuntimeError("Hay volúmenes anteriores sin contenedores. Recupera su configuración antes de arrancar.")
        for key in ("MARIADB_ROOT_PASSWORD", "MARIADB_PASSWORD", "ORACLE_PASSWORD"):
            config[key] = secrets.token_hex(20)
        config["ORACLE_USER_PASSWORD"] = config["ORACLE_PASSWORD"]
    sqlite_destino = folder / "central.sqlite3"
    if origen is not None:
        anterior = Path(config.get("SQLITE_PATH", "data/central.sqlite3"))
        if not anterior.is_absolute():
            anterior = origen / "api" / anterior
        copiar_sqlite(anterior, sqlite_destino)
    config["SQLITE_PATH"] = sqlite_destino.as_posix()
    # Los drivers pertenecen al clon desde el que se lanza la API.
    config["JARS_DIR"] = "jars"
    temporal = folder / "local.env.creating"
    try:
        temporal.write_text("# Instalación local compartida Omni3 R01; no versionar.\n", encoding="utf-8")
        for key in dotenv_values(root / "api/.env.demo.example"):
            if config.get(key) is not None:
                set_key(str(temporal), key, str(config[key]))
        temporal.replace(target)
    finally:
        temporal.unlink(missing_ok=True)
    print(f"[local] Configuración compartida preparada: {target}", flush=True)
    return target


def identidad(sqlite_path):
    return hashlib.sha256(str(Path(sqlite_path).resolve()).casefold().encode()).hexdigest()[:20]


def comprobar_api_anterior(folder):
    """No copiar una cola mientras una API antigua sigue añadiendo tareas."""
    if (folder / "local.env").exists():
        return
    try:
        with urlopen("http://127.0.0.1:8000/health", timeout=3) as response:
            health = json.load(response)
    except (URLError, TimeoutError, ValueError, OSError):
        return
    if health.get("reto") == "R01":
        raise RuntimeError("Detén la API anterior del puerto 8000 antes de migrar la configuración y su cola SQLite.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "api/data/local-environment.json")
    parser.add_argument("--legacy-env", type=Path)
    args = parser.parse_args()
    try:
        comprobar_api_anterior(directorio_compartido())
        path = preparar(legacy_file=args.legacy_env)
        values = dotenv_values(path)
        info = {"env_file": str(path), "compose_project": PROJECT,
                "sqlite_path": values["SQLITE_PATH"], "installation_id": identidad(values["SQLITE_PATH"])}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(info, indent=2), encoding="utf-8")
        return 0
    except Exception as exc:
        # No volcar una excepción del driver que pueda incluir credenciales.
        mensaje = str(exc) if isinstance(exc, RuntimeError) else type(exc).__name__
        print(f"[local] ERROR: {mensaje}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
