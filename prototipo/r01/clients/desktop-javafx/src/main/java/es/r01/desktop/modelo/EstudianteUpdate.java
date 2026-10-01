package es.r01.desktop.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de la actualización parcial
 * (PUT /connectors/{connector_id}/estudiantes/{row_id}).
 *
 * El backend sólo aplica los campos informados, así que los nulos se
 * omiten del JSON (NON_NULL) y nunca se envía el campo id.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstudianteUpdate {

    public String dni;
    public String nombre;
    public String casa;
    public String patronus;

    public EstudianteUpdate() {
    }

    public EstudianteUpdate(String dni, String nombre, String casa, String patronus) {
        this.dni = dni;
        this.nombre = nombre;
        this.casa = casa;
        this.patronus = patronus;
    }
}
