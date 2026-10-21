"""Capa de datos: modelos, protocolo y repositorios de los 5 motores.

- `base.py`: modelos Pydantic + Protocolo `RepositorioAlumnos`.
- `factory.py`: crea los repositorios activos según DB_ENGINES.
- `jdbc.py`: repositorio genérico JDBC (base de H2, HSQLDB y Derby).
- `oracle.py`, `mariadb.py`, `h2.py`, `hsqldb.py`, `derby.py`: un
  repositorio por motor.
"""
