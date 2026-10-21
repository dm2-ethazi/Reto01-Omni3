package es.r01.desktop.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo del alta de un estudiante en un origen
 * (POST /connectors/{connector_id}/estudiantes).
 *
 * El backend prohíbe campos extra (extra="forbid"), por eso sólo se emiten
 * los declarados aquí y se omiten los nulos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstudiantePayload {

    /** Id en el origen: opcional, si no llega lo asigna el propio origen. */
    public Integer id;

    public String dni;
    public String nombre;
    public String casa;
    public String patronus;

    public EstudiantePayload() {
    }

    public EstudiantePayload(String dni, String nombre, String casa, String patronus) {
        this.dni = dni;
        this.nombre = nombre;
        this.casa = casa;
        this.patronus = patronus;
    }
}
