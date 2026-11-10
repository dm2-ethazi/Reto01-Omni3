"""Reglas de la propuesta v6: fuentes autoritativas y refrescos recuperables."""

import hashlib
import json
import logging
import sqlite3
import time

from ..config import settings
from ..db.base import Alumno, ColaLlena, Conflicto, EscrituraIncierta
from ..db.origin_write import CAMPOS_ORIGEN, aplicar_origen, contenido
from ..db.sqlite import CAMPOS, instante, persistible

logger = logging.getLogger(__name__)
MAX_INTENTOS = 5


class ErrorServicio(Exception):
    def __init__(self, status, mensaje):
        self.status = status
        self.mensaje = mensaje
        super().__init__(mensaje)


class ServicioAlumnos:
    def __init__(self, local, maria, fuentes, capacidad=None):
        self.local = local
        self.maria = maria
        self.fuentes = fuentes
        self.capacidad = settings.refresh_queue_max if capacidad is None else capacidad
        self.maria_preparada = False

    def preparar_maria(self):
        if self.maria is None:
            raise ConnectionError("MariaDB no está configurada")
        if not self.maria_preparada:
            self.maria.preparar()
            self.maria_preparada = True

    def iniciar(self):
        """Recupera refrescos; no reejecuta nunca una escritura de usuario."""
        try:
            self.preparar_maria()
            alumnos = self.maria.listar_central()
            self.local.guardar_catalogo(self.maria.listar_patronus())
            with self.local.conexion(escritura=True) as db:
                for alumno in alumnos:
                    if not db.execute(
                        "SELECT 1 FROM OUTBOX WHERE ALUMNO_ID=?", (alumno.id,)
                    ).fetchone():
                        self.local.guardar(db, alumno)
                        self.local.seguimiento(db, alumno, "SYNCED", sincronizado=True)
        except Exception as exc:
            logger.warning(
                "central_inicio estado=degradado causa=%s", type(exc).__name__
            )
        self.reintentar()

    def catalogo(self):
        try:
            self.preparar_maria()
            catalogo = self.maria.listar_patronus()
            self.local.guardar_catalogo(catalogo)
            return catalogo
        except Exception:
            return self.local.catalogo()

    def validar_patronus(self, valor, obligatorio):
        if not valor:
            if obligatorio:
                raise ErrorServicio(
                    422, "El Patronus es obligatorio para alumnos nuevos"
                )
            return
        catalogo = self.catalogo()
        if not catalogo:
            raise ErrorServicio(503, "No hay catálogo de Patronus disponible")
        if valor not in {p.nom_eng for p in catalogo}:
            raise ErrorServicio(422, "El Patronus no existe en el catálogo")

    def _leer_local(self, dni=None):
        try:
            if dni is not None:
                with self.local.conexion() as db:
                    return self.local.obtener(db, dni), True
            return self.local.listar_central(), True
        except Exception as exc:
            raise ErrorServicio(503, "La copia SQLite no está disponible") from exc

    def _leer_central(self, dni=None, lectura="auto"):
        if lectura == "sqlite":
            return self._leer_local(dni)
        try:
            self.preparar_maria()
            datos = (
                self.maria.obtener_central(dni)
                if dni is not None
                else self.maria.listar_central()
            )
            return datos, False
        except Exception:
            try:
                return self._leer_local(dni)
            except Exception as exc:
                raise ErrorServicio(
                    503, "No hay una central de lectura disponible"
                ) from exc

    def enriquecer(self, alumno, cache=False):
        try:
            estado, fecha = self.local.estado_alumno(alumno.id)
            url = {p.nom_eng: p.url for p in self.local.catalogo()}.get(alumno.patronus)
        except Exception:
            estado, fecha, url = "UNKNOWN", None, None
        return alumno.model_copy(
            update={
                "desde_cache": cache,
                "sincronizacion": estado,
                "sincronizado_en": fecha,
                "patronus_url": url,
            }
        )

    def obtener(self, dni):
        alumno, cache = self._leer_central(dni)
        if alumno is None or not alumno.activo:
            raise ErrorServicio(404, "Alumno no encontrado en la vista central")
        return self.enriquecer(alumno, cache)

    def listar(self, nombre=None):
        return self.listar_con_estado(nombre)[0]

    def listar_con_estado(self, nombre=None, lectura="auto"):
        alumnos, cache = self._leer_central(lectura=lectura)
        return [
            self.enriquecer(a, cache)
            for a in alumnos
            if a.activo
            and (not nombre or a.nombre.casefold().startswith(nombre.casefold()))
        ], cache

    def _reservar(self, source, nuevo, previo, operacion, confirmada=False):
        try:
            with self.local.conexion(escritura=True) as db:
                return self.local.reservar(
                    db,
                    source,
                    nuevo,
                    previo,
                    operacion,
                    self.capacidad,
                    confirmada=confirmada,
                )
        except Conflicto as exc:
            raise ErrorServicio(409, str(exc)) from exc
        except (ColaLlena, sqlite3.Error, OSError) as exc:
            raise ErrorServicio(
                503, "No se puede registrar el refresco; no se ha modificado la fuente"
            ) from exc

    def _escribir(self, source, previo, nuevo, operacion, escritura):
        seq = self._reservar(source, nuevo, previo, operacion)
        error = None
        releido = None
        try:
            # La reserva ya está confirmada en disco. Este bloqueo serializa
            # escrituras/recuperación entre todos los procesos del mismo servidor.
            with self.local.conexion(escritura=True) as db:
                tarea = db.execute(
                    "SELECT * FROM OUTBOX WHERE SEQ=?", (seq,)
                ).fetchone()
                try:
                    releido = escritura()
                except EscrituraIncierta:
                    # No emitir jamás otro UPDATE/INSERT: la recuperación solo
                    # leerá la fuente para averiguar qué confirmó el commit.
                    db.execute(
                        "UPDATE OUTBOX SET LAST_ERROR='COMMIT_UNKNOWN',NEXT_ATTEMPT=? WHERE SEQ=?",
                        (time.time() + settings.refresh_prepare_timeout_seconds, seq),
                    )
                    error = ErrorServicio(
                        503,
                        "Resultado del commit desconocido; consulta el estado antes de repetir",
                    )
                except Exception as exc:
                    self.local.cancelar(db, tarea)
                    error = ErrorServicio(
                        409 if isinstance(exc, Conflicto) else 503,
                        str(exc)
                        if isinstance(exc, Conflicto)
                        else "La fuente no responde; escritura rechazada",
                    )
                else:
                    self.local.confirmar(db, seq, releido)
        except sqlite3.Error as exc:
            # Una caída local después del commit remoto deja la reserva anterior
            # recuperable. Nunca devolver éxito si se desconoce la confirmación.
            raise ErrorServicio(
                503, "Refresco reservado; debe comprobarse el resultado de la operación"
            ) from exc
        if error:
            raise error
        try:
            self.reintentar()
        except Exception as exc:
            # La fuente ya confirmó y el refresco ya era durable: una avería de
            # la réplica no convierte esa escritura en una operación rechazada.
            logger.warning(
                "refresco_inmediato seq=%s causa=%s", seq, type(exc).__name__
            )
        return self.enriquecer(releido)

    def crear(self, datos):
        # MariaDB debe estar disponible incluso si existe catálogo en caché.
        try:
            self.preparar_maria()
            if self.maria.obtener_central(datos.id) is not None:
                raise ErrorServicio(409, "El DNI ya existe, incluso si está inactivo")
        except ErrorServicio:
            raise
        except Exception as exc:
            raise ErrorServicio(
                503, "MariaDB no responde; no se aceptan altas en caché"
            ) from exc
        self.validar_patronus(datos.patronus, obligatorio=True)
        with self.local.conexion() as db:
            if self.local.obtener(db, datos.id) is not None:
                raise ErrorServicio(409, "El DNI ya consta en la copia central")
        for nombre, fuente in self.fuentes.items():
            try:
                existente = fuente.obtener(datos.id)
            except Exception as exc:
                # Un origen caído se aísla; la unicidad también se comprueba en
                # la información central disponible y la PK de MariaDB.
                logger.warning(
                    "duplicados origen=%s causa=%s", nombre, type(exc).__name__
                )
                continue
            if existente:
                raise ErrorServicio(409, "El DNI ya existe en una fuente histórica")
        nuevo = Alumno(
            **datos.model_dump(),
            origen="mariadb",
            tipo="NUEVO",
            actualizado_en=instante(),
        )
        return self._escribir(
            "mariadb", None, nuevo, "CREATE", lambda: self.maria.mutar(None, nuevo)
        )

    def modificar(self, dni, cambios, version, baja=False):
        if version is None:
            raise ErrorServicio(428, "Envía la versión leída en version o If-Match")
        actual = persistible(self.obtener(dni))
        if actual.version != version:
            raise ErrorServicio(409, "El alumno ha cambiado; recarga antes de editar")
        if actual.tipo == "NUEVO":
            try:
                self.preparar_maria()
                principal = self.maria.obtener_central(dni)
            except Exception as exc:
                raise ErrorServicio(
                    503, "MariaDB no responde; escritura rechazada"
                ) from exc
            if principal is None or persistible(principal) != actual:
                raise ErrorServicio(409, "El alumno ha cambiado en MariaDB")
        if not baja and "patronus" in cambios:
            self.validar_patronus(cambios["patronus"], actual.tipo == "NUEVO")
        nuevo = actual.model_copy(
            update={
                **cambios,
                "activo": not baja,
                "version": version + 1,
                "actualizado_en": instante(),
            }
        )
        if actual.tipo == "NUEVO":
            escritura = lambda: self.maria.mutar(actual, nuevo)
        else:
            fuente = self.fuentes.get(actual.origen)
            if fuente is None:
                raise ErrorServicio(503, "Origen no configurado; escritura rechazada")
            escritura = lambda: aplicar_origen(fuente, actual, nuevo)
        resultado = self._escribir(
            actual.origen, actual, nuevo, "DELETE" if baja else "UPDATE", escritura
        )
        return None if baja else resultado

    def importar(self, origenes=None, ids=None, resolver_conflictos=False):
        nombres = list(self.fuentes) if origenes is None else origenes
        if any(n not in self.fuentes for n in nombres):
            raise ErrorServicio(422, "Origen desconocido o no configurado")
        informe = {}
        for nombre in nombres:
            anteriores, _ = self._leer_central()
            versiones = {a.id: a.version for a in anteriores}
            try:
                alumnos = self.fuentes[nombre].listar()
            except Exception as exc:
                informe[nombre] = {"estado": "caido", "importados": 0}
                logger.warning(
                    "importacion origen=%s causa=%s", nombre, type(exc).__name__
                )
                continue
            cuenta = conflictos = bajas = 0
            presentes = {a.id for a in alumnos}
            if ids is not None:
                # Permite refrescar una baja externa de un alumno ya importado,
                # sin inferir eliminaciones por ausencia de lotes parciales.
                for previo in anteriores:
                    if (
                        previo.id in ids
                        and previo.origen == nombre
                        and previo.activo
                        and previo.id not in presentes
                    ):
                        alumnos.append(previo.model_copy(update={"activo": False}))
            for alumno in alumnos:
                if ids is not None and alumno.id not in ids:
                    continue
                vigente, _ = self._leer_central(alumno.id)
                if vigente and (
                    vigente.origen != nombre or (not vigente.activo and alumno.activo)
                ):
                    conflictos += 1
                    continue
                if (
                    vigente
                    and contenido(vigente) == contenido(alumno)
                    and vigente.activo == alumno.activo
                ):
                    continue
                if vigente and versiones.get(alumno.id) != vigente.version:
                    conflictos += 1
                    continue
                nuevo = persistible(alumno).model_copy(
                    update={
                        "origen": nombre,
                        "tipo": "HISTORICO",
                        "version": vigente.version + 1 if vigente else 1,
                        "actualizado_en": instante(),
                    }
                )
                try:
                    # La fuente ya se ha leído: esta tarea solo replica ese dato.
                    self._reservar(nombre, nuevo, vigente, "IMPORT", confirmada=True)
                except ErrorServicio as exc:
                    if exc.status == 409:
                        conflictos += 1
                        continue
                    raise
                cuenta += 1
                bajas += int(not nuevo.activo)
            informe[nombre] = {
                "estado": "ok",
                "leidos": len(presentes),
                "importados": cuenta,
                "conflictos": conflictos,
                "bajas_detectadas": bajas,
            }
        self.reintentar()
        return {"fuentes": informe, "pendientes": len(self.local.cola())}

    def _recuperar_reserva(self, db, tarea):
        """Una reserva abandonada solo autoriza leer, nunca escribir su fuente."""
        nuevo = Alumno.model_validate_json(tarea["PAYLOAD"])
        previo = (
            Alumno.model_validate_json(tarea["EXPECTED"]) if tarea["EXPECTED"] else None
        )
        if tarea["SOURCE"] == "mariadb":
            self.preparar_maria()
            actual = self.maria.obtener_central(nuevo.id)
            aplicado = actual is not None and (
                previo is None or actual.version > previo.version
            )
        else:
            fuente = self.fuentes.get(tarea["SOURCE"])
            if fuente is None:
                raise ConnectionError("Origen no configurado")
            actual = fuente.obtener(nuevo.id)
            aplicado = (actual is None and not nuevo.activo) or (
                actual is not None and contenido(actual) != contenido(previo)
            )
        if not aplicado:
            self.local.cancelar(db, tarea)
            return None
        if actual is not None:
            if tarea["SOURCE"] == "mariadb":
                nuevo = persistible(actual)
            else:
                nuevo = nuevo.model_copy(
                    update={c: getattr(actual, c) for c in CAMPOS_ORIGEN}
                )
        self.local.confirmar(db, tarea["SEQ"], nuevo)
        return nuevo

    def reintentar(self, forzar=False):
        enviados = cancelados = 0
        with self.local.conexion() as db:
            ids = [
                f[0]
                for f in db.execute(
                    "SELECT SEQ FROM OUTBOX WHERE STATUS IN ('PREPARED','PENDING') ORDER BY SEQ LIMIT 200"
                )
            ]
        destinos_caidos = set()
        for seq in ids:
            with self.local.conexion(escritura=True) as db:
                tarea = db.execute(
                    "SELECT * FROM OUTBOX WHERE SEQ=?", (seq,)
                ).fetchone()
                if tarea is None or tarea["STATUS"] == "FAILED":
                    continue
                ahora = time.time()
                if (
                    tarea["STATUS"] == "PREPARED"
                    and ahora
                    < tarea["CREATED_AT"] + settings.refresh_prepare_timeout_seconds
                ):
                    continue
                if (not forzar and tarea["NEXT_ATTEMPT"] > ahora) or tarea[
                    "SOURCE"
                ] in destinos_caidos:
                    continue
                try:
                    confirmada = bool(tarea["CONFIRMED"])
                    if not tarea["CONFIRMED"]:
                        nuevo = self._recuperar_reserva(db, tarea)
                        if nuevo is None:
                            cancelados += 1
                            continue
                        confirmada = True
                    else:
                        nuevo = Alumno.model_validate_json(tarea["PAYLOAD"])
                    # La réplica SQLite puede quedar disponible aunque MariaDB
                    # esté caída; la tarea continúa hasta completar ambos destinos.
                    self.local.guardar(db, nuevo)
                    if tarea["SOURCE"] != "mariadb":
                        self.preparar_maria()
                        self.maria.aplicar(nuevo)
                    self.local.seguimiento(db, nuevo, "SYNCED", sincronizado=True)
                    db.execute("DELETE FROM OUTBOX WHERE SEQ=?", (seq,))
                    enviados += 1
                    logger.info(
                        "refresco seq=%s origen=%s estado=SYNCED", seq, tarea["SOURCE"]
                    )
                except Exception as exc:
                    intentos = tarea["ATTEMPTS"] + 1
                    estado = (
                        "FAILED"
                        if intentos >= MAX_INTENTOS
                        else ("PENDING" if confirmada else "PREPARED")
                    )
                    db.execute(
                        "UPDATE OUTBOX SET ATTEMPTS=?,NEXT_ATTEMPT=?,STATUS=?,LAST_ERROR=?,UPDATED_AT=? WHERE SEQ=?",
                        (
                            intentos,
                            ahora + min(300, 2**intentos),
                            estado,
                            type(exc).__name__,
                            instante(),
                            seq,
                        ),
                    )
                    alumno = Alumno.model_validate_json(tarea["PAYLOAD"])
                    self.local.seguimiento(db, alumno, estado, seq)
                    destinos_caidos.add(tarea["SOURCE"])
                    logger.warning(
                        "refresco seq=%s estado=%s intento=%s causa=%s",
                        seq,
                        estado,
                        intentos,
                        type(exc).__name__,
                    )
        return {
            "enviados": enviados,
            "cancelados": cancelados,
            "pendientes": len(self.local.cola()),
        }

    def relanzar(self, seq=None):
        """Reintento administrativo de refrescos, nunca de escrituras rechazadas."""
        with self.local.conexion(escritura=True) as db:
            tareas = db.execute(
                "SELECT * FROM OUTBOX WHERE STATUS='FAILED'"
                + (" AND SEQ=?" if seq is not None else ""),
                (seq,) if seq is not None else (),
            ).fetchall()
            elegibles = [t for t in tareas if t["SOURCE"] is not None]
            activas = db.execute(
                "SELECT COUNT(*) FROM OUTBOX WHERE STATUS IN ('PREPARED','PENDING')"
            ).fetchone()[0]
            if activas + len(elegibles) > self.capacidad:
                raise ErrorServicio(
                    503, "No hay capacidad para relanzar estos refrescos"
                )
            if seq is not None and not tareas:
                raise ErrorServicio(
                    404, "No existe una tarea FAILED con ese identificador"
                )
            if seq is not None and not elegibles:
                raise ErrorServicio(
                    409,
                    "La tarea procede de la versión anterior y requiere revisión manual",
                )
            for tarea in elegibles:
                estado = "PENDING" if tarea["CONFIRMED"] else "PREPARED"
                db.execute(
                    "UPDATE OUTBOX SET STATUS=?,ATTEMPTS=0,NEXT_ATTEMPT=0,CREATED_AT=0,LAST_ERROR=NULL WHERE SEQ=?",
                    (estado, tarea["SEQ"]),
                )
                self.local.seguimiento(
                    db,
                    Alumno.model_validate_json(tarea["PAYLOAD"]),
                    estado,
                    tarea["SEQ"],
                )
        return self.reintentar(forzar=True)

    def coherencia(self):
        resultados = {}
        conjuntos = {}
        for nombre, lectura in (
            (
                "mariadb",
                lambda: (self.preparar_maria(), self.maria.listar_central())[1],
            ),
            ("sqlite", self.local.listar_central),
        ):
            try:
                alumnos = lectura()
                registros = {a.id: {c: getattr(a, c) for c in CAMPOS} for a in alumnos}
                serializado = json.dumps(
                    [registros[k] for k in sorted(registros)],
                    ensure_ascii=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
                resultados[nombre] = {
                    "estado": "ok",
                    "registros": len(registros),
                    "huella": hashlib.sha256(serializado.encode("utf-8")).hexdigest(),
                }
                conjuntos[nombre] = registros
            except Exception:
                resultados[nombre] = {
                    "estado": "caido",
                    "registros": None,
                    "huella": None,
                }
        completo = len(conjuntos) == 2
        maria, sqlite = conjuntos.get("mariadb", {}), conjuntos.get("sqlite", {})
        diferencias = (
            {
                "faltan_en_mariadb": sorted(sqlite.keys() - maria.keys()),
                "faltan_en_sqlite": sorted(maria.keys() - sqlite.keys()),
                "distintos": sorted(
                    k for k in maria.keys() & sqlite.keys() if maria[k] != sqlite[k]
                ),
            }
            if completo
            else None
        )
        return {
            "reto": "R01",
            "comprobado_en": instante(),
            "completo": completo,
            "coinciden": completo and not any(diferencias.values()),
            "centrales": resultados,
            "divergencias": diferencias,
        }

    def conectores(self):
        estados = []
        for nombre, fuente in self.fuentes.items():
            inicio = time.monotonic()
            try:
                cantidad, estado = len(fuente.listar()), "ok"
            except Exception:
                cantidad, estado = None, "caido"
            estados.append(
                {
                    "origen": nombre,
                    "estado": estado,
                    "registros": cantidad,
                    "latencia_ms": round((time.monotonic() - inicio) * 1000, 1),
                }
            )
        return estados

    def salud(self):
        try:
            self.local.listar_central()
            cola = self.local.estadisticas(self.capacidad)
            sqlite = "ok"
        except Exception:
            sqlite, cola = (
                "caido",
                {
                    "activas": 0,
                    "fallidas": 0,
                    "aviso": True,
                    "capacidad": self.capacidad,
                },
            )
        try:
            self.preparar_maria()
            self.maria.listar_central()
            maria = "ok"
        except Exception:
            maria = "caido"
        conectores = self.conectores()
        degradado = (
            sqlite != "ok"
            or maria != "ok"
            or cola["aviso"]
            or cola["fallidas"]
            or any(c["estado"] != "ok" for c in conectores)
        )
        return {
            "reto": "R01",
            "status": "degraded" if degradado else "ok",
            "timestamp": instante(),
            "centrales": {"sqlite": sqlite, "mariadb": maria},
            "conectores": conectores,
            "pendientes_reintento": cola["activas"],
            "cola": cola,
        }
