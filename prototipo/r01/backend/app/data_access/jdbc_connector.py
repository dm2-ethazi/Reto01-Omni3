"""Único conector JDBC de la aplicación (Derby, HSQLDB, H2 y Oracle).

Toda la variación (URL, driver, jars, credenciales, dialecto y mapeo de
columnas) vive en ``config/connectors.yaml``; aquí sólo hay una implementación.
"""

from __future__ import annotations

import logging
from collections.abc import Mapping, Sequence
from contextlib import contextmanager, suppress
from typing import Any

import jaydebeapi
import jpype

from app.core.exceptions import ErrorOrigen, ValidacionError
from app.data_access.base import (
    CAMPOS_MODELO,
    TIPOS_MODELO,
    ConnectorSpec,
    DataSource,
)
from app.data_access.dialects import Dialect, get_dialect, map_row

logger = logging.getLogger(__name__)


def _desprender_de_la_jvm() -> None:
    """Desprende el hilo actual de la JVM.

    Los hilos del pool de trabajo dejan la JVM adherida y ``shutdownJVM()``
    se bloquea indefinidamente al terminar el proceso (JPype 1.7): se
    desprende siempre que se cierra una conexión.
    """
    try:
        if jpype.isThreadAttachedToJVM():
            jpype.java.lang.Thread.detach()
    except Exception:  # pragma: no cover - el cierre no debe romperse por esto
        logger.debug("No se pudo desprender el hilo de la JVM", exc_info=True)


class JdbcConnector(DataSource):
    """Implementación JDBC (JayDeBeApi + JPype) de :class:`DataSource`."""

    def __init__(self, spec: ConnectorSpec) -> None:
        self._spec = spec
        self._dialect: Dialect = get_dialect(spec.dialect)

    # ------------------------------------------------------------------ #
    # Identidad
    # ------------------------------------------------------------------ #
    @property
    def spec(self) -> ConnectorSpec:
        """Especificación inyectada en la construcción."""
        return self._spec

    @property
    def id(self) -> str:
        """Id del conector (clave en el registro)."""
        return self._spec.id

    # ------------------------------------------------------------------ #
    # Conexión y sentencias
    # ------------------------------------------------------------------ #
    @contextmanager
    def _connection(self):
        """Abre una conexión JDBC y la cierra siempre."""
        try:
            connection = jaydebeapi.connect(
                self._spec.driver_class,
                self._spec.url,
                self._spec.credentials(),
            )
        except Exception as exc:
            logger.error("Conexión fallida con '%s': %s", self.id, exc)
            _desprender_de_la_jvm()
            raise ErrorOrigen(
                f"No se pudo conectar con el origen '{self.id}'",
                {"url": self._spec.url, "error": str(exc)},
            ) from exc
        try:
            yield connection
        finally:
            with suppress(Exception):  # el cierre nunca debe romper el flujo
                connection.close()
            _desprender_de_la_jvm()

    @staticmethod
    def _close_quietly(*cursors: Any) -> None:
        for cursor in cursors:
            if cursor is None:
                continue
            with suppress(Exception):
                cursor.close()

    def _query_raw(self, sql: str, params: Sequence[Any] = ()) -> tuple[list[str], list[tuple[Any, ...]]]:
        """Ejecuta un SELECT y devuelve etiquetas JDBC + filas en crudo."""
        try:
            with self._connection() as connection:
                cursor = connection.cursor()
                try:
                    cursor.execute(sql, tuple(params))
                    labels = [col[0] for col in (cursor.description or [])]
                    raw_rows = list(cursor.fetchall()) if labels else []
                finally:
                    self._close_quietly(cursor)
        except ErrorOrigen:
            raise
        except Exception as exc:
            logger.error("Fallo en '%s' al ejecutar consulta: %s", self.id, exc)
            raise ErrorOrigen(
                f"Consulta fallida en el origen '{self.id}'",
                {"sql": sql, "error": str(exc)},
            ) from exc
        return labels, raw_rows

    def _query(self, sql: str, params: Sequence[Any] = ()) -> list[dict[str, Any]]:
        """Consulta con las filas mapeadas al modelo."""
        labels, raw_rows = self._query_raw(sql, params)
        return [map_row(self._spec.columns, labels, row, TIPOS_MODELO) for row in raw_rows]

    @staticmethod
    def _scalar(raw_rows: list[tuple[Any, ...]]) -> Any:
        """Primer valor de la primera fila (para COUNT y MAX)."""
        if not raw_rows or not raw_rows[0]:
            return None
        return raw_rows[0][0]


    def _execute(self, sql: str, params: Sequence[Any] = ()) -> int:
        """Ejecuta INSERT/UPDATE/DELETE y devuelve las filas afectadas."""
        try:
            with self._connection() as connection:
                cursor = connection.cursor()
                try:
                    cursor.execute(sql, tuple(params))
                    affected = int(cursor.rowcount or 0)
                finally:
                    self._close_quietly(cursor)
        except ErrorOrigen:
            raise
        except Exception as exc:
            logger.error("Fallo en '%s' al ejecutar escritura: %s", self.id, exc)
            raise ErrorOrigen(
                f"Escritura fallida en el origen '{self.id}'",
                {"sql": sql, "error": str(exc)},
            ) from exc
        return affected

    # ------------------------------------------------------------------ #
    # Composición de SQL
    # ------------------------------------------------------------------ #
    @property
    def _table(self) -> str:
        return self._dialect.ident(self._spec.table)

    @property
    def _select_columns(self) -> str:
        return ", ".join(self._dialect.ident(col) for col in self._spec.columns.values())

    def _where(self, filters: Mapping[str, Any] | None) -> tuple[str, list[Any]]:
        """Construye un WHERE parametrizado; los identificadores van con lista blanca."""
        if not filters:
            return "", []
        clauses: list[str] = []
        params: list[Any] = []
        for key, value in filters.items():
            column = self._spec.columns.get(key)
            if column is None:
                raise ValidacionError(
                    f"Filtro no soportado: {key!r}",
                    {"filtros_permitidos": sorted(self._spec.columns)},
                )
            ident = self._dialect.ident(column)
            if value is None or value == "":
                continue
            if key in {"nombre", "patronus"}:
                clauses.append(f"{self._dialect.ident(ident)} LIKE ?")
                params.append(f"%{value}%")
            else:
                clauses.append(f"{self._dialect.ident(ident)} = ?")
                params.append(value)
        return (" WHERE " + " AND ".join(clauses)) if clauses else "", params

    # ------------------------------------------------------------------ #
    # Operaciones DataSource
    # ------------------------------------------------------------------ #
    def ping(self) -> bool:
        """Comprueba conexión + existencia de la tabla de origen."""
        try:
            _, rows = self._query_raw(
                f"SELECT COUNT(*) FROM {self._table} FETCH FIRST 1 ROWS ONLY"
            )
            return bool(rows)
        except Exception:
            return False

    def list(
        self,
        *,
        offset: int = 0,
        limit: int = 50,
        filters: dict[str, Any] | None = None,
    ) -> list[dict[str, Any]]:
        """Lista paginada (OFFSET/FETCH estándar) con filtros parametrizados."""
        where, params = self._where(filters)
        sql = (
            f"SELECT {self._select_columns} FROM {self._table}{where} "
            f"{self._dialect.fetch_clause(offset, limit)}"
        )
        return self._query(sql, params)

    def count(self, filters: dict[str, Any] | None = None) -> int:
        """Total de filas que cumplen los filtros."""
        where, params = self._where(filters)
        _, rows = self._query_raw(f"SELECT COUNT(*) FROM {self._table}{where}", params)
        value = self._scalar(rows)
        return int(value or 0)

    def get(self, row_id: int) -> dict[str, Any] | None:
        """Recupera una fila por su id de origen."""
        where, params = self._where({"id": row_id})
        rows = self._query(
            f"SELECT {self._select_columns} FROM {self._table}{where} "
            f"{self._dialect.fetch_clause(0, 1)}",
            params,
        )
        return rows[0] if rows else None

    def create(self, data: Mapping[str, Any]) -> dict[str, Any]:
        """Alta en el origen; si no llega id se calcula ``MAX(id)+1``."""
        payload = self._validar_payload(data, permitir_id=True)
        if "id" in payload:
            row_id = int(payload["id"])
        else:
            _, rows = self._query_raw(self._dialect.next_id_expression(self._table))
            row_id = int(self._scalar(rows) or 1)


        campos = {key: payload[key] for key in CAMPOS_MODELO if key in payload and key != "id"}
        campos["id"] = row_id
        columnas = ", ".join(self._dialect.ident(self._spec.columns[key]) for key in campos)
        placeholders = ", ".join("?" for _ in campos)
        self._execute(
            f"INSERT INTO {self._table} ({columnas}) VALUES ({placeholders})",
            [campos[key] for key in campos],
        )
        created = self.get(row_id)
        if created is None:  # pragma: no cover - defensa
            raise ErrorOrigen(f"La fila creada en '{self.id}' no es legible")
        return created

    def update(self, row_id: int, data: Mapping[str, Any]) -> dict[str, Any] | None:
        """Actualización parcial (sólo campos enviados)."""
        payload = self._validar_payload(data, permitir_id=False)
        if not payload:
            raise ValidacionError("La actualización no contiene campos válidos")
        sets = ", ".join(f"{self._dialect.ident(self._spec.columns[key])} = ?" for key in payload)
        affected = self._execute(
            f"UPDATE {self._table} SET {sets} WHERE {self._dialect.ident(self._spec.columns['id'])} = ?",
            [*payload.values(), row_id],
        )
        if affected == 0:
            return None
        return self.get(row_id)

    def delete(self, row_id: int) -> bool:
        """Borra la fila indicada."""
        affected = self._execute(
            f"DELETE FROM {self._table} WHERE {self._dialect.ident(self._spec.columns['id'])} = ?",
            [row_id],
        )
        return affected > 0

    # ------------------------------------------------------------------ #
    # Validación (capa de acceso, últimas defensas)
    # ------------------------------------------------------------------ #
    def _validar_payload(
        self,
        data: Mapping[str, Any],
        *,
        permitir_id: bool,
    ) -> dict[str, Any]:
        """Valida estructura y valores antes de tocar la base."""
        desconocidos = [key for key in data if key not in CAMPOS_MODELO]
        if desconocidos:
            raise ValidacionError("Campos no reconocidos", {"campos": desconocidos})
        if not permitir_id and "id" in data:
            raise ValidacionError("El campo 'id' no se puede modificar")

        payload: dict[str, Any] = {}
        for key, value in data.items():
            if value is None:
                continue
            expected = TIPOS_MODELO[key]
            if expected is int:
                try:
                    payload[key] = int(value)
                except (TypeError, ValueError) as exc:
                    raise ValidacionError(f"El campo '{key}' debe ser numérico") from exc
            else:
                text = str(value).strip()
                if not text:
                    continue
                payload[key] = text
        return payload
