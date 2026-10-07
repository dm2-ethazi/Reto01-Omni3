package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Viewmodel de la pantalla de estado del sistema ({@code GET /health}).
 *
 * <p>La respuesta identifica el reto con el código {@code R01} y describe
 * el estado de cada base (§8 de la propuesta).</p>
 */
public class SaludViewModel extends ViewModelBase {

    /** Última salud recibida; {@code null} si todavía no se ha consultado. */
    private final ObjectProperty<SaludDto> salud = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public SaludViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Aún no se ha consultado el estado del sistema.");
    }

    /** Última respuesta de salud. */
    public SaludDto getSalud() {
        return salud.get();
    }

    /** Propiedad observable de la salud. */
    public ObjectProperty<SaludDto> saludProperty() {
        return salud;
    }

    /**
     * Consulta el estado del sistema.
     */
    public void comprobar() {
        marcarCargando("Consultando /health…");
        servicio.salud(
                respuesta -> {
                    salud.set(respuesta);
                    marcarOk("Estado recibido de " + (respuesta.esR01() ? "R01" : "la API") + ".");
                },
                error -> {
                    salud.set(null);
                    marcarFallo(error);
                });
    }
}
