package com.omni3.gestorhogwartsjavafx.modelo;

import java.util.Locale;
import java.util.Optional;

/**
 * Estado de un conector (fuente de datos) informado por {@code GET /connectors}.
 */
public enum EstadoConector {

    /** El conector responde correctamente. */
    OK,
    /** El conector responde con problemas parciales o latencias altas. */
    DEGRADADO,
    /** El conector no responde. */
    CAIDO,
    /** Estado no reconocido o aún no informado. */
    DESCONOCIDO;

    /**
     * Resuelve un estado a partir de su texto (nombre del enum, en cualquier
     * mayúscula/minúscula o con espacios alrededor).
     *
     * <p>Cualquier valor no reconocido se interpreta como
     * {@link #DESCONOCIDO} para no romper la interfaz de usuario.</p>
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el estado encontrado, nunca {@code null}
     */
    public static EstadoConector desdeTexto(String texto) {
        if (texto != null) {
            String normalizado = texto.trim().toUpperCase(Locale.ROOT);
            for (EstadoConector estado : values()) {
                if (estado.name().equals(normalizado)) {
                    return estado;
                }
            }
        }
        return DESCONOCIDO;
    }

    /**
     * Versión opcional del mismo conversor que no falla en silencio.
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el estado encontrado o vacío si no existe
     */
    public static Optional<EstadoConector> buscar(String texto) {
        EstadoConector estado = desdeTexto(texto);
        return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(estado);
    }
}
