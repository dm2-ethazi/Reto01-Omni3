package com.omni3.gestorhogwartsjavafx.modelo;

import java.util.Locale;
import java.util.Optional;

/**
 * Fuentes de datos de las que procede un alumno.
 *
 * <p>Los seis primeros valores son bases de datos reales del entorno
 * (puertos: 1527 Derby, 9001 HSQLDB, 9092 H2, 1521 Oracle, 3306 MariaDB).
 * {@link #CENTRAL} no es una base: identifica la vista unificada de la
 * consulta (§3 del contrato), en la que se mezclan todas las fuentes.</p>
 */
public enum Origen {

    /** Base Derby (histórica). */
    DERBY,
    /** Base HSQLDB (histórica). */
    HSQLDB,
    /** Base H2 (histórica). */
    H2,
    /** Base Oracle (histórica). */
    ORACLE,
    /** Central MariaDB: alumnos nuevos (§2.2). */
    MARIADB,
    /** Base SQLite (histórica). */
    SQLITE,
    /** Vista unificada de todas las fuentes (no es una base real). */
    CENTRAL;

    /**
     * Indica si este origen es la vista unificada {@link #CENTRAL}.
     *
     * @return {@code true} si es la vista unificada
     */
    public boolean esCentral() {
        return this == CENTRAL;
    }

    /**
     * Indica si este origen corresponde a una base de datos real
     * (es decir, a una fuente distinta de la vista unificada).
     *
     * @return {@code true} si es una base de datos real
     */
    public boolean esBaseReal() {
        return this != CENTRAL;
    }

    /**
     * Resuelve un origen a partir de su texto (nombre del enum, en
     * cualquier mayúscula/minúscula o con espacios alrededor).
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el origen encontrado o vacío si no existe
     */
    public static Optional<Origen> desdeTexto(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String normalizado = texto.trim().toUpperCase(Locale.ROOT);
        for (Origen origen : values()) {
            if (origen.name().equals(normalizado)) {
                return Optional.of(origen);
            }
        }
        return Optional.empty();
    }
}
