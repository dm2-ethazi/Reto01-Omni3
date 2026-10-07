package com.omni3.gestorhogwartsjavafx.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Modo de acceso a la API (§3 del contrato).
 *
 * <p>La aplicación arranca en {@link #DEMO} por defecto, para poder usarla
 * sin red; con {@link #API} habla con la API real de FastAPI publicada en
 * {@code http://127.0.0.1:8000}. La interfaz de usuario es idéntica en
 * ambos modos.</p>
 */
public enum Modo {

    /** Datos de demostración en memoria (sin red). */
    DEMO,
    /** API REST real sobre {@code http://127.0.0.1:8000}. */
    API;

    /**
     * Resuelve un modo a partir de su texto (nombre del enum, en cualquier
     * mayúscula/minúscula o con espacios alrededor).
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el modo encontrado o vacío si no existe
     */
    public static Optional<Modo> desdeTexto(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String normalizado = texto.trim().toUpperCase(Locale.ROOT);
        for (Modo modo : values()) {
            if (modo.name().equals(normalizado)) {
                return Optional.of(modo);
            }
        }
        return Optional.empty();
    }
}
