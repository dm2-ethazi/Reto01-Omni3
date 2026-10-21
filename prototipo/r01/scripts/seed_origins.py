"""R01 - Siembra manual de los cuatro origenes JDBC con infra/seeds/*.sql.

El docker-compose actual arranca los servidores JDBC sin cargar las semillas
(los init.sh de java-servers no estan cableados), por lo que este script
aplica seeds/derby.sql, hsqldb.sql, h2.sql y oracle.sql usando los mismos
drivers y conectores que el backend (config/connectors.yaml).

Uso:
    r01\\backend\\.venv\\Scripts\\python.exe r01\\scripts\\seed_origins.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

R01_DIR = Path(__file__).resolve().parents[1]
BACKEND_DIR = R01_DIR / "backend"
SEEDS_DIR = R01_DIR / "infra" / "seeds"
INFRA_ENV = R01_DIR / "infra" / ".env"

sys.path.insert(0, str(BACKEND_DIR))

ORIGENES = ("derby", "hsqldb", "h2", "oracle")

# Derby crea la base en el primer enlace (create=true no esta en connectors.yaml).
URL_EXTRA = {"derby": ";create=true"}
# HSQLDB arranca con SA sin contrasena; la semilla la fija en r01pass.
CREDENCIALES_SEMILLA = {"hsqldb": ("SA", "")}


def propiedades(user: str, password: str) -> dict[str, str]:
    """Propiedades JDBC omitiendo vacios (igual que ConnectorSpec.credentials)."""
    props: dict[str, str] = {}
    if user:
        props["user"] = user
    if password:
        props["password"] = password
    return props


def cargar_env() -> None:
    """Carga backend/.env en el entorno sin pisar variables ya definidas."""
    if not BACKEND_DIR.joinpath(".env").exists():
        return
    for linea in (BACKEND_DIR / ".env").read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        clave, _, valor = linea.partition("=")
        os.environ.setdefault(clave.strip(), valor.strip())


def valor_infra(clave: str) -> str:
    """Lee una variable de r01/infra/.env (credenciales del contenedor)."""
    if not INFRA_ENV.exists():
        return ""
    for linea in INFRA_ENV.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if linea.startswith(clave + "="):
            return linea.partition("=")[2].strip()
    return ""


def sentencias(sql: str) -> list[str]:
    """Divide el script en sentencias (los ficheros no usan ';' internos)."""
    resultado: list[str] = []
    for trozo in sql.split(";"):
        lineas = [
            linea
            for linea in trozo.splitlines()
            if linea.strip() and not linea.strip().startswith("--")
        ]
        cuerpo = "\n".join(lineas).strip()
        if cuerpo:
            resultado.append(cuerpo)
    return resultado


def otorgar_privilegios_oracle(jaydebeapi, url: str) -> None:
    """Si APP_USER no tiene permisos, los otorga SYSTEM (como 001_seed.sh)."""
    system_password = valor_infra("ORACLE_PASSWORD")
    if not system_password:
        return
    conn = jaydebeapi.connect(
        "oracle.jdbc.OracleDriver", url, propiedades("SYSTEM", system_password)
    )
    cursor = conn.cursor()
    app_user = valor_infra("ORACLE_USER") or "ravenclaw"
    for grante in (
        "GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW TO " + app_user,
        "GRANT UNLIMITED TABLESPACE TO " + app_user,
    ):
        cursor.execute(grante)
    cursor.close()
    conn.close()
    print(f"  privilegios otorgados a {app_user} por SYSTEM")


def sembrar(jaydebeapi, registro, origenes: tuple[str, ...]) -> int:
    fallos = 0
    for cid in origenes:
        fichero = SEEDS_DIR / f"{cid}.sql"
        spec = registro.get(cid).spec
        url = spec.url + URL_EXTRA.get(cid, "")
        user, password = CREDENCIALES_SEMILLA.get(cid, (spec.user, spec.password))
        print(f"== {cid}: {fichero.name} -> {url}")
        if not fichero.exists():
            print("  ! falta el fichero de semilla")
            fallos += 1
            continue
        try:
            conn = jaydebeapi.connect(
                spec.driver_class, url, propiedades(user, password)
            )
        except Exception as exc:
            print(f"  ! conexion fallida: {exc}")
            fallos += 1
            continue

        fallo_sentencia: Exception | None = None
        cursor = conn.cursor()
        try:
            for sentencia in sentencias(fichero.read_text(encoding="utf-8")):
                cursor.execute(sentencia)
        except Exception as exc:
            fallo_sentencia = exc
        finally:
            cursor.close()
            conn.close()

        if fallo_sentencia is None:
            print("  OK - semilla cargada")
            continue

        if cid == "oracle" and "ORA-01031" in str(fallo_sentencia):
            print("  privilegios insuficientes, reintentando con SYSTEM...")
            try:
                otorgar_privilegios_oracle(jaydebeapi, spec.url)
                conn = jaydebeapi.connect(
                    spec.driver_class, url, propiedades(user, password)
                )
                cursor = conn.cursor()
                try:
                    for sentencia in sentencias(fichero.read_text(encoding="utf-8")):
                        cursor.execute(sentencia)
                finally:
                    cursor.close()
                    conn.close()
                print("  OK - semilla cargada tras otorgar privilegios")
                continue
            except Exception as exc:
                print(f"  ! reintento fallido: {exc}")

        print(f"  ! fallo: {fallo_sentencia}")
        fallos += 1
    return fallos


def main() -> int:
    cargar_env()

    import jaydebeapi

    from app.data_access.registry import ConnectorRegistry

    registro = ConnectorRegistry.load(
        BACKEND_DIR / "config" / "connectors.yaml",
        BACKEND_DIR / "drivers",
    )
    if registro.avisos:
        for aviso in registro.avisos:
            print(f"aviso registry: {aviso}")

    origenes = tuple(sys.argv[1:]) or ORIGENES
    desconocidos = [c for c in origenes if c not in ORIGENES]
    if desconocidos:
        print(f"Origenes desconocidos: {desconocidos} (validos: {ORIGENES})")
        return 2

    fallos = sembrar(jaydebeapi, registro, origenes)
    if fallos:
        print(f"RESULTADO: {fallos} origen(es) con fallo")
        return 1
    print(f"RESULTADO: {len(origenes)} origen(es) procesados sin fallo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
