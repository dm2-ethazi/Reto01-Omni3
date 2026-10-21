package com.omni3.gestorhogwartsjavafx.modelo;

import java.util.Locale;
import java.util.Optional;

/**
 * Clasificación de un alumno según su procedencia (§4 del contrato).
 *
 * <p>Los alumnos históricos proceden de las bases de origen y siempre
 * llevan casa; los alumnos nuevos se dan de alta en la central MariaDB,
 * con patronus obligatorio y sin casa.</p>
 * @author jairoben
 */
public enum TipoAlumno {

    /** Alumno existente en una base de origen (casa obligatoria). */
    HISTORICO,
    /** Alumno nuevo dado de alta en la central (patronus obligatorio, sin casa). */
    NUEVO;

    /**
     * Resuelve un tipo a partir de su texto (nombre del enum, en cualquier
     * mayúscula/minúscula o con espacios alrededor).
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el tipo encontrado o vacío si no existe
     */
    public static Optional<TipoAlumno> desdeTexto(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String normalizado = texto.trim().toUpperCase(Locale.ROOT);
        for (TipoAlumno tipo : values()) {
            if (tipo.name().equals(normalizado)) {
                return Optional.of(tipo);
            }
        }
        return Optional.empty();
    }
}
