-- Migración idempotente a la versión 2 del esquema de MariaDB (r01_central).
--
-- Añade a ALUMNOS el borrado lógico (ACTIVO / FECHA_BAJA) y el control de
-- concurrencia optimista (VERSION) más UPDATED_AT.
--
-- Es seguro ejecutarlo tantas veces como se quiera: todas las operaciones
-- usan "ADD COLUMN IF NOT EXISTS" (MariaDB 10.0.2+) o bien comprueban antes
-- la existencia de la columna.
--
-- Uso:
--   mariadb -u r01 -p r01_central < migracion_v2.sql

ALTER TABLE ALUMNOS
    ADD COLUMN IF NOT EXISTS ACTIVO TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE ALUMNOS
    ADD COLUMN IF NOT EXISTS FECHA_BAJA DATETIME NULL;

ALTER TABLE ALUMNOS
    ADD COLUMN IF NOT EXISTS VERSION INT NOT NULL DEFAULT 1;

ALTER TABLE ALUMNOS
    ADD COLUMN IF NOT EXISTS UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP;

-- Registros creados antes de la migración quedan como activos y en versión 1.
UPDATE ALUMNOS SET ACTIVO = 1 WHERE ACTIVO IS NULL;
UPDATE ALUMNOS SET VERSION = 1 WHERE VERSION IS NULL OR VERSION < 1;
