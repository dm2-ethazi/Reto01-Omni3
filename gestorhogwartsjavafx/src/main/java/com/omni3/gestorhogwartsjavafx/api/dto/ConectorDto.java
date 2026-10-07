package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.omni3.gestorhogwartsjavafx.modelo.EstadoConector;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;

/**
 * Elemento de la respuesta de {@code GET /connectors}: una fuente y su
 * estado (§3).
 *
 * @param origen               nombre del {@link Origen} al que pertenece
 * @param estado               estado textual; se interpreta con
 *                             {@link EstadoConector#desdeTexto(String)}
 * @param mensaje              detalle legible para el usuario
 * @param ultimaSincronizacion instante ISO-8601 de la última sincronización
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConectorDto(
        String origen,
        String estado,
        String mensaje,
        String ultimaSincronizacion) {

    /**
     * Convierte el estado del DTO en el enum de dominio.
     *
     * @return estado de dominio; {@link EstadoConector#DESCONOCIDO} si no se reconoce
     */
    public EstadoConector estadoDeDominio() {
        return EstadoConector.desdeTexto(estado);
    }

    /**
     * Convierte el origen del DTO en el enum de dominio.
     *
     * @return origen de dominio, o {@link Origen#CENTRAL} si no se reconoce
     */
    public Origen origenDeDominio() {
        return Origen.desdeTexto(origen).orElse(Origen.CENTRAL);
    }
}
