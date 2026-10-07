package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Viewmodel de la pantalla de conectores ({@code GET /connectors}).
 *
 * <p>Lista las fuentes del entorno —Derby, HSQLDB, H2, Oracle, MariaDB y
 * SQLite— con su estado ({@code OK}, {@code DEGRADADO}, {@code CAIDO}) y
 * el detalle que devuelve la API.</p>
 */
public class ConectoresViewModel extends ViewModelBase {

    /** Filas de la tabla de conectores. */
    private final ObservableList<ConectorDto> conectores = FXCollections.observableArrayList();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public ConectoresViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Listo");
    }

    /** Conectores visibles. */
    public ObservableList<ConectorDto> getConectores() {
        return conectores;
    }

    /**
     * Carga el estado de los conectores.
     */
    public void cargar() {
        marcarCargando("Consultando conectores…");
        servicio.listarConectores(
                lista -> {
                    conectores.setAll(lista);
                    if (lista.isEmpty()) {
                        marcarVacio("La API no ha devuelto ningún conector.");
                    } else {
                        marcarOk(lista.size() + " conector(es)");
                    }
                },
                this::marcarFallo);
    }
}
