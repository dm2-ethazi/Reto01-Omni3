"""Capa de acceso a datos de los orígenes (conectores JDBC)."""

from app.data_access.base import ConnectorSpec, DataSource
from app.data_access.registry import ConnectorRegistry

__all__ = ["ConnectorSpec", "DataSource", "ConnectorRegistry"]
