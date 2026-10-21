-- Minimal schema required by api/src/proyecto_reto1/db/mariadb.py.
-- Applied by the MariaDB image only when its data volume is first created.
CREATE TABLE IF NOT EXISTS ALUMNOS (
    ID VARCHAR(10) NOT NULL PRIMARY KEY,
    NOMBRE VARCHAR(100) NOT NULL,
    APELLIDO VARCHAR(100) NULL,
    SEXO CHAR(1) NULL,
    PATRONUS VARCHAR(100) NULL,
    IMAGEN VARCHAR(500) NULL,
    CONSTRAINT chk_alumnos_sexo CHECK (SEXO IS NULL OR SEXO IN ('H', 'M', 'S'))
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS PATRONUS_IMG (
    ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    NOM_ENG VARCHAR(100) NOT NULL UNIQUE,
    URL VARCHAR(500) NOT NULL
) CHARACTER SET utf8mb4;

-- Example catalog entry. Image URL is intentionally empty: no image asset is supplied.
INSERT IGNORE INTO PATRONUS_IMG (NOM_ENG, URL) VALUES ('Otter', '');
