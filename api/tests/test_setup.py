"""CSV originales y migración segura de la instalación entre clones."""

import importlib.util
import shutil
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from uuid import UUID

import pytest
from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[2]


def cargar_script(nombre):
    spec = importlib.util.spec_from_file_location(nombre.replace("-", "_"), ROOT / "scripts" / f"{nombre}.py")
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


seed = cargar_script("seed-origins")
local = cargar_script("local-environment")


def repo_temporal(path):
    (path / "api").mkdir(parents=True)
    shutil.copyfile(ROOT / "api/.env.demo.example", path / "api/.env.demo.example")
    return path


def test_csv_originales_uuid_y_campos_sin_datos_inventados():
    datos = seed.cargar_csv(ROOT / "database/origins/data")
    assert {k: len(v) for k, v in datos.items()} == {"oracle": 203, "h2": 212, "hsqldb": 267, "derby": 303}
    filas = [f for grupo in datos.values() for f in grupo]
    assert len({f[0] for f in filas}) == 985
    assert all(str(UUID(f[0])) == f[0] and f[2] is None and f[4] is None for f in filas)
    assert datos["derby"][0][1] == "Aamir Loonat"
    assert datos["derby"][0][6] == "Gryffindor"
    assert any(f[7] and len(f[7]) > 500 for f in datos["derby"])
    assert any(f[3] == "S" and f[9] for f in filas)


def test_csv_con_identificadores_duplicados_se_rechaza_antes_de_cargar(tmp_path):
    for archivo in (ROOT / "database/origins/data").glob("*.csv"):
        shutil.copyfile(archivo, tmp_path / archivo.name)
    archivo = tmp_path / "gryffindor.csv"
    with archivo.open(encoding="utf-8-sig") as f:
        text = f.read()
    filas = seed.cargar_csv(tmp_path)["derby"]
    archivo.write_text(text.replace(filas[1][0], filas[0][0]), encoding="utf-8")
    with pytest.raises(ValueError, match="duplicado"):
        seed.cargar_csv(tmp_path)


def test_login_invalido_no_espera_180_segundos(monkeypatch):
    @contextmanager
    def conexion():
        raise Exception(1045, "access denied")
        yield  # pragma: no cover

    monkeypatch.setattr(seed.time, "sleep", lambda _: pytest.fail("No reintentar un login inválido"))
    with pytest.raises(ValueError, match="credenciales rechazadas"):
        with seed.esperar_conexion(SimpleNamespace(_conexion=conexion), 180, "mariadb"):
            pytest.fail("No debe conectarse")


@pytest.mark.parametrize("ejemplo_editado", [False, True])
def test_semilla_conserva_ediciones_y_solo_retira_ejemplos_intactos(monkeypatch, ejemplo_editado):
    filas = seed.cargar_csv(ROOT / "database/origins/data")["h2"][:2]
    monkeypatch.setattr(seed, "preparar_tabla", lambda *args: None)
    with sqlite3.connect(":memory:") as db:
        db.execute("CREATE TABLE ALUMNOS (" + ",".join(f"{c} TEXT" for c in seed.COLUMNAS) + ",PRIMARY KEY(ID))")
        existente = list(filas[0])
        existente[1] = "Edición conservada"
        ejemplo = [*seed.EJEMPLOS["h2"], *([None] * len(seed.EXTRAS))]
        if ejemplo_editado:
            ejemplo[seed.COLUMNAS.index("ALIAS")] = "Alias escrito por otra persona"
        db.executemany("INSERT INTO ALUMNOS VALUES (" + ",".join("?" for _ in seed.COLUMNAS) + ")", [existente, ejemplo])
        nuevos, anteriores, retirados = seed.sembrar(db, "h2", filas)
        assert nuevos == anteriores == 1
        assert retirados == (0 if ejemplo_editado else 1)
        assert db.execute("SELECT NOMBRE FROM ALUMNOS WHERE ID=?", (filas[0][0],)).fetchone()[0] == "Edición conservada"
        assert db.execute("SELECT * FROM ALUMNOS WHERE ID=?", (filas[1][0],)).fetchone() == filas[1]


def test_dos_clones_comparten_claves_y_sqlite(tmp_path, monkeypatch):
    repo1 = repo_temporal(tmp_path / "clon1")
    repo2 = repo_temporal(tmp_path / "clon2")
    shared = tmp_path / "compartido"
    monkeypatch.setattr(local, "instalacion_anterior", lambda: [])
    monkeypatch.setattr(local, "ejecutar_docker", lambda *args: "")
    first = local.preparar(repo1, shared)
    valores = dotenv_values(first)
    monkeypatch.setattr(local, "instalacion_anterior", lambda: pytest.fail("No redescubrir si ya existe configuración"))
    second = local.preparar(repo2, shared)
    assert first == second and dotenv_values(second) == valores
    assert valores["ORACLE_PASSWORD"] == valores["ORACLE_USER_PASSWORD"]
    assert valores["SQLITE_PATH"] == (shared / "central.sqlite3").as_posix()
    assert "change-demo" not in valores["MARIADB_PASSWORD"]


def test_migrar_volumen_elige_claves_validas_y_conserva_cola(tmp_path, monkeypatch):
    antiguo = repo_temporal(tmp_path / "antiguo")
    nuevo = repo_temporal(tmp_path / "nuevo")
    data = antiguo / "api/data"
    data.mkdir()
    template = (antiguo / "api/.env.demo.example").read_text(encoding="utf-8")
    (data / "validation.env").write_text(template.replace("change-demo-maria", "clave-anterior"), encoding="utf-8")
    with sqlite3.connect(data / "validation-final.sqlite3") as db:
        db.execute("CREATE TABLE OUTBOX (SEQ INTEGER PRIMARY KEY, STATUS TEXT)")
        db.execute("INSERT INTO OUTBOX VALUES (1,'PENDING')")
    containers = [{"Config": {"Labels": {"com.docker.compose.project.working_dir": str(antiguo)}}}]
    monkeypatch.setattr(local, "instalacion_anterior", lambda: containers)
    monkeypatch.setattr(local, "esperar_servicios", lambda _: None)
    monkeypatch.setattr(local, "credenciales_validas", lambda c: c["MARIADB_PASSWORD"] == "clave-anterior")
    shared = tmp_path / "compartido"
    target = local.preparar(nuevo, shared)
    assert dotenv_values(target)["MARIADB_PASSWORD"] == "clave-anterior"
    for path in (data / "validation-final.sqlite3", shared / "central.sqlite3"):
        with sqlite3.connect(path) as db:
            assert db.execute("SELECT STATUS FROM OUTBOX").fetchone() == ("PENDING",)


def test_no_sustituir_credenciales_si_volumen_no_es_accesible(tmp_path, monkeypatch):
    root = repo_temporal(tmp_path / "clon")
    monkeypatch.setattr(local, "instalacion_anterior", lambda: [{"Config": {"Labels": {}}}])
    monkeypatch.setattr(local, "esperar_servicios", lambda _: None)
    monkeypatch.setattr(local, "credenciales_validas", lambda _: False)
    shared = tmp_path / "compartido"
    with pytest.raises(RuntimeError, match="configuración válida"):
        local.preparar(root, shared)
    assert not (shared / "local.env").exists()


def test_volumen_sin_configuracion_no_recibe_claves_nuevas(tmp_path, monkeypatch):
    root = repo_temporal(tmp_path / "clon")
    monkeypatch.setattr(local, "instalacion_anterior", lambda: [])
    monkeypatch.setattr(local, "ejecutar_docker", lambda *args: "volumen-anterior")
    with pytest.raises(RuntimeError, match="volúmenes anteriores"):
        local.preparar(root, tmp_path / "compartido")
