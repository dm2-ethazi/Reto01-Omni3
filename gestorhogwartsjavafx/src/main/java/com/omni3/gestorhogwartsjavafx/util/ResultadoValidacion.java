package com.omni3.gestorhogwartsjavafx.util;

import java.util.Optional;

/**
 * Resultado inmutable de una validación: o es válido o lleva un mensaje
 * de error en español listo para mostrar al usuario.
 * @author jairoben
 */
public final class ResultadoValidacion {

    /** Instancia compartida para los casos válidos. */
    private static final ResultadoValidacion CORRECTO = new ResultadoValidacion(null);

    /** Mensaje de error en español; {@code null} cuando la validación es correcta. */
    private final String error;

    private ResultadoValidacion(String error) {
        this.error = error;
    }

    /**
     * Crea un resultado de validación correcta.
     *
     * @return resultado válido (instancia compartida)
     */
    public static ResultadoValidacion correcto() {
        return CORRECTO;
    }

    /**
     * Crea un resultado fallido con mensaje de error.
     *
     * @param mensajeError mensaje de error en español; no nulo ni vacío
     * @return resultado inválido con el mensaje indicado
     */
    public static ResultadoValidacion error(String mensajeError) {
        if (mensajeError == null || mensajeError.isBlank()) {
            throw new IllegalArgumentException("El mensaje de error no puede estar vacío");
        }
        return new ResultadoValidacion(mensajeError);
    }

    /**
     * Indica si la validación ha sido satisfactoria.
     *
     * @return {@code true} si no hay errores
     */
    public boolean esValido() {
        return error == null;
    }

    /**
     * Devuelve el mensaje de error en español, si lo hay.
     *
     * @return mensaje de error o vacío si la validación es correcta
     */
    public Optional<String> mensajeError() {
        return Optional.ofNullable(error);
    }

    /**
     * Devuelve el mensaje de error o lanza {@link IllegalStateException}
     * si la validación era correcta. Pensado para encadenar comprobaciones.
     *
     * @return mensaje de error; nunca {@code null}
     * @throws IllegalStateException si la validación es correcta
     */
    public String mensajeErrorObligatorio() {
        if (error == null) {
            throw new IllegalStateException("La validación no tiene errores");
        }
        return error;
    }

    /**
     * Representación textual sobria del resultado.
     *
     * @return {@code "válido"} o {@code "inválido: <motivo>"}
     */
    @Override
    public String toString() {
        return esValido() ? "válido" : "inválido: " + error;
    }
}
