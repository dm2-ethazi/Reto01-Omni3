-- R01 - Semilla del conector HSQLDB (origen "hsqldb")
CREATE TABLE ESTUDIANTE (
    ID INT PRIMARY KEY,
    DNI VARCHAR(9) NOT NULL,
    NOMBRE VARCHAR(100) NOT NULL,
    CASA VARCHAR(30) NOT NULL,
    PATRONUS VARCHAR(50) NOT NULL,
    CONSTRAINT UQ_HSQL_DNI UNIQUE (DNI)
);

INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (11, '11223344B', 'Javier Mora', 'Gryffindor', 'Jabali');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (12, '22334455Y', 'Laura Vidal', 'Slytherin', 'Mangosta');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (13, '33445566R', 'Marcos Ibanez', 'Hufflepuff', 'Castor');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (14, '44556677L', 'Noelia Cano', 'Ravenclaw', 'Delfin');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (15, '55667788Z', 'Oscar Lara', 'Gryffindor', 'Toro');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (16, '66778899D', 'Patricia Sanz', 'Slytherin', 'Hidra');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (17, '77889900D', 'Quintin Rey', 'Hufflepuff', 'Ardilla');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (18, '88990011K', 'Rocio Pena', 'Ravenclaw', 'Buho Real');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (19, '99001122Z', 'Sergio Mena', 'Gryffindor', 'Caballo');
INSERT INTO ESTUDIANTE (ID, DNI, NOMBRE, CASA, PATRONUS) VALUES (20, '10101010P', 'Teresa Otero', 'Slytherin', 'Zorro');

-- Cambia la contrasena del usuario SA en la primera inicializacion.
-- La conexion abierta por el sembrador sigue siendo valida.
SET PASSWORD 'r01pass';
