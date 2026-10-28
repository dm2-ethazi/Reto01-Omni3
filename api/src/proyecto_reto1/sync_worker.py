"""Worker de sincronización con reintentos y backoff exponencial.

Reintenta periódicamente las operaciones de la cola `pending_sync` cuando
MariaDB vuelve a estar disponible:

- Se aplican **en orden** (FIFO por id) y, al completarse, se eliminan de la cola.
- Entre intentos se espera un backoff exponencial
  (`backoff_inicial * 2 ** intentos`, limitado por `backoff_max`).
- Cuando se agotan los intentos (`max_intentos`), la operación queda marcada
  como `fallido` y permanece visible en `GET /status`.
- Tras aplicar operaciones, el respaldo SQLite se refresca con el estado real
  de MariaDB para que vuelvan a coincidir.

Arranque:
    lo hace `main.py` con el evento de lifespan de FastAPI.
"""

import logging
import threading
from datetime import datetime, timedelta

from .db.mariadb import RepositorioMariaDB
from .db.sqlite_backup import RespaldoSQLite

logger = logging.getLogger(__name__)


class WorkerSincronizacion:
    """Hilo de fondo que vacía la cola `pending_sync` contra MariaDB.

    Atributos:
        mariadb: Repositorio central.
        respaldo: Respaldo SQLite (dueño de la cola).
        intervalo: Segundos entre pasadas del worker.
        backoff_inicial: Segundos base para el backoff exponencial.
        backoff_max: Techo del backoff (segundos).
        max_intentos: Intentos máximos antes de marcar la operación como fallida.
    """

    def __init__(
        self,
        mariadb: RepositorioMariaDB | None,
        respaldo: RespaldoSQLite,
        intervalo: float,
        backoff_inicial: float,
        backoff_max: float,
        max_intentos: int,
    ):
        """Crea el worker (todavía no arranca).

        Args:
            mariadb: Repositorio central de MariaDB.
            respaldo: Instancia del respaldo SQLite.
            intervalo: Segundos entre pasadas.
            backoff_inicial: Segundos del primer reintento.
            backoff_max: Techo del backoff en segundos.
            max_intentos: Intentos máximos por operación.
        """
        self.mariadb = mariadb
        self.respaldo = respaldo
        self.intervalo = intervalo
        self.backoff_inicial = backoff_inicial
        self.backoff_max = backoff_max
        self.max_intentos = max_intentos
        self._parar = threading.Event()
        self._hilo: threading.Thread | None = None

    # ------------------------------------------------------------------ #
    #  Ciclo de vida                                                      #
    # ------------------------------------------------------------------ #

    def arrancar(self) -> None:
        """Arranca el hilo daemon de sincronización (una sola vez)."""
        if self._hilo is not None:
            return
        self._hilo = threading.Thread(
            target=self._bucle, name="worker-sincronizacion", daemon=True
        )
        self._hilo.start()
        logger.info("Worker de sincronización arrancado (intervalo %.0fs)", self.intervalo)

    def detener(self, esperar: float = 5.0) -> None:
        """Detiene el hilo y espera a que termine."""
        self._parar.set()
        if self._hilo is not None:
            self._hilo.join(timeout=esperar)
            self._hilo = None

    def _bucle(self) -> None:
        """Bucle principal: procesa pendientes cada `intervalo` segundos."""
        while not self._parar.is_set():
            try:
                self.procesar_pendientes()
            except Exception as error:  # el worker nunca debe morirse
                logger.error("Error en el worker de sincronización: %s", error)
            self._parar.wait(self.intervalo)

    # ------------------------------------------------------------------ #
    #  Reintentos                                                         #
    # ------------------------------------------------------------------ #

    def _backoff(self, intentos: int) -> float:
        """Calcula el backoff exponencial para el número de intentos dado."""
        return min(self.backoff_inicial * (2**intentos), self.backoff_max)

    def procesar_pendientes(self) -> int:
        """Aplica en orden las operaciones pendientes cuyo momento llegó.

        Returns:
            Número de operaciones aplicadas con éxito y eliminadas de la cola.
        """
        pendientes = self.respaldo.pendientes()
        if not pendientes:
            return 0
        if self.mariadb is None or not self.mariadb.ping():
            logger.debug("MariaDB sigue caído; %d operaciones pendientes", len(pendientes))
            return 0

        ahora = datetime.now()
        aplicadas = 0
        for operacion in pendientes:
            if operacion["estado"] == "fallido":
                continue  # Fallo definitivo: queda marcado para /status.
            if datetime.fromisoformat(operacion["proximo_intento"]) > ahora:
                continue
            try:
                self._aplicar(operacion)
            except Exception as error:
                intentos = operacion["intentos"] + 1
                agotado = intentos >= self.max_intentos
                proximo = ahora + timedelta(seconds=self._backoff(intentos))
                logger.warning(
                    "Reintento fallido (%d/%d) para operación %d: %s",
                    intentos,
                    self.max_intentos,
                    operacion["id"],
                    error,
                )
                self.respaldo.registrar_fallo(operacion["id"], str(error), proximo, agotado)
                continue
            self.respaldo.eliminar_pendiente(operacion["id"])
            aplicadas += 1
            logger.info("Operación %d (%s) sincronizada con MariaDB", operacion["id"], operacion["operacion"])

        if aplicadas:
            self._refrescar_respaldo()
        return aplicadas

    def _aplicar(self, operacion: dict) -> None:
        """Aplica una operación concreta de la cola en MariaDB.

        Args:
            operacion: Dict devuelto por `RespaldoSQLite.pendientes()`.

        Raises:
            Exception: Cualquier error de MariaDB (el caller gestiona el backoff).
        """
        payload = operacion["payload"]
        if operacion["operacion"] == "crear":
            from .db.base import AlumnoEntrada

            self.mariadb.restaurar(AlumnoEntrada(**payload), version=payload.get("version"))
        elif operacion["operacion"] == "actualizar":
            self.mariadb.aplicar_actualizacion(
                {"id": payload["id"], **payload["campos"]}, payload.get("version")
            )
        elif operacion["operacion"] == "eliminar":
            self.mariadb.aplicar_baja(
                payload["id"], payload.get("version"), payload.get("fecha_baja")
            )
        else:
            raise ValueError(f"Operación desconocida en pending_sync: {operacion['operacion']}")

    def _refrescar_respaldo(self) -> None:
        """Vuelca el estado real de MariaDB al respaldo tras sincronizar."""
        try:
            alumnos = self.mariadb.listar(incluir_inactivos=True)
            self.respaldo.sincronizar_desde_mariadb(alumnos)
            logger.info("Respaldo SQLite refrescado (%d registros)", len(alumnos))
        except Exception as error:
            logger.warning("No se pudo refrescar el respaldo: %s", error)
