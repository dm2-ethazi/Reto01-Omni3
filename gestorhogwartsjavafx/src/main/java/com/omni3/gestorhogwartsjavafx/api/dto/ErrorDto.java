package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Error devuelto por la API con el envelope documentado en el §3:
 *
 * <pre>{@code
 * {"error": {"codigo": "...", "mensaje": "...", "detalle": "..."}}
 * }</pre>
 *
 * @param codigo  código de negocio, p. ej. {@code CONFLICTO_VERSION}
 * @param mensaje mensaje en español
 * @param detalle detalle técnico opcional
 * @author jairoben
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorDto(String codigo, String mensaje, String detalle) {

    /**
     * Envoltorio JSON completo del error.
     *
     * @param error bloque {@code error}; puede ser nulo si el backend
     *              envía un cuerpo distinto
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envoltorio(ErrorDto error) {
    }

    /**
     * Extrae el error de un cuerpo JSON con el envelope documentado.
     *
     * @param mapper   Jackson a utilizar
     * @param cuerpo   cuerpo de la respuesta; puede ser nulo o vacío
     * @return el error encontrado, o vacío si el cuerpo no tiene ese formato
     */
    public static Optional<ErrorDto> desdeEnvelope(ObjectMapper mapper, String cuerpo) {
        if (mapper == null || cuerpo == null || cuerpo.isBlank()) {
            return Optional.empty();
        }
        try {
            Envoltorio envoltorio = mapper.readValue(cuerpo, Envoltorio.class);
            return Optional.ofNullable(envoltorio.error());
        } catch (Exception excepcion) {
            return Optional.empty();
        }
    }
}
