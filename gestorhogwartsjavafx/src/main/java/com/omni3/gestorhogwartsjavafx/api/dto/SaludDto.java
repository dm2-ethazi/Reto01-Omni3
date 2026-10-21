package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de {@code GET /health} (§3): estado del sistema y su
 * identificador, que para este reto es siempre {@code R01}.
 *
 * @param estado        estado textual, p. ej. {@code OK}
 * @param identificador identificador del sistema; {@code R01}
 * @param mensaje       descripción legible para el usuario
 * @param momento       instante ISO-8601 de la respuesta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaludDto(
        String estado,
        String identificador,
        String mensaje,
        String momento) {

    /**
     * Comprueba si la salud corresponde al reto R01.
     *
     * @return {@code true} si el identificador es {@code R01}
     */
    public boolean esR01() {
        return "R01".equalsIgnoreCase(identificador);
    }
}
