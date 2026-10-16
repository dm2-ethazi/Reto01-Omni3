package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entrada del catálogo de patronus de la API ({@code GET /patronus}).
 *
 * <p>El catálogo es monolingüe: la API publica únicamente el nombre
 * del patronus en inglés y la URL de su imagen.</p>
 *
 * @param nomEng nombre del patronus en inglés
 * @param url    URL de la imagen del patronus
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatronusDto(
        @JsonProperty("nom_eng") String nomEng,
        String url) {
}
