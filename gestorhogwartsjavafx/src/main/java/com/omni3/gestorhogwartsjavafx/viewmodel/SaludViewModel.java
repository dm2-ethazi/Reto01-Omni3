package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Viewmodel de la pantalla de estado del sistema ({@code GET /health}).
 *
 * <p>La respuesta identifica el reto con el código {@code R01} y describe
 * el estado de cada base (§8 de la propuesta).</p>
 * @author jairoben
 */
public class SaludViewModel extends ViewModelBase {

    // Última salud recibida; {@code null} si todavía no se ha consultado.
    private final ObjectProperty<SaludDto> salud = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public SaludViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar(Idiomas.texto("salud.inicial"));
    }

    /**
     * Última respuesta de salud.
     *
     * @return el DTO recibido de {@code /health}; {@code null} si aún no se ha consultado o la última llamada falló
     */
    public SaludDto getSalud() {
        return salud.get();
    }

    /**
     * Propiedad observable de la salud; la vista se suscribe a sus
     * cambios para repintar el estado de las bases cada vez que llega
     * una respuesta nueva de {@code /health}.
     *
     * @return la propiedad enlazada con el último estado recibido, que pasa a {@code null} si la consulta falla
     */
    public ObjectProperty<SaludDto> saludProperty() {
        return salud;
    }

    /**
     * Consulta el estado del sistema.
     */
    public void comprobar() {
        marcarCargando(Idiomas.texto("salud.cargando"));
        servicio.salud(
                respuesta -> {
                    salud.set(respuesta);
                    marcarOk(Idiomas.texto(respuesta.esR01() ? "salud.ok.r01" : "salud.ok.api"));
                },
                error -> {
                    salud.set(null);
                    marcarFallo(error);
                });
    }
}
