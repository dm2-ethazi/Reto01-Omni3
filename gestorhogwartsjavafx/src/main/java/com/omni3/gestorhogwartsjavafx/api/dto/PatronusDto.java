package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entrada del catálogo de patronus de la API ({@code GET /patronus}).
 *
 * <p>Cada entrada trae el nombre en los tres idiomas admitidos por la
 * interfaz y la URL de su imagen, tal y como los publica la tabla
 * {@code PATRONUS_IMG} de la central MariaDB.</p>
 *
 * @param nomEsp nombre del patronus en español
 * @param nomEng nombre del patronus en inglés
 * @param nomEus nombre del patronus en euskera
 * @param url    URL de la imagen del patronus
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatronusDto(
        @JsonProperty("nom_esp") String nomEsp,
        @JsonProperty("nom_eng") String nomEng,
        @JsonProperty("nom_eus") String nomEus,
        String url) {
}
