package es.r01.desktop.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Fila de estudiante tal y como la devuelve la API de R01. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Estudiante {
    public int id;
    public String origen;
    public Integer id_origen;
    public String dni;
    public String nombre;
    public String casa;
    public String patronus;

    public int getId() {
        return id;
    }

    public String getOrigen() {
        return origen == null ? "" : origen;
    }

    public String getDni() {
        return dni;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCasa() {
        return casa;
    }

    public String getPatronus() {
        return patronus;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", nombre, dni);
    }
}
