package es.r01.desktop.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Página devuelta por /central/estudiantes y /connectors/{id}/estudiantes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Pagina {
    public List<Estudiante> items = List.of();
    public int total;
    public int offset;
    public int limit;
    public String backend;
    public String conector;
}
