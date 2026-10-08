package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;

/**
 * Viewmodel de la pantalla de sincronización.
 *
 * <p>Agrupa dos operaciones de administración: disparar la sincronización
 * de las fuentes históricas hacia la vista unificada
 * ({@code POST /sync}) y consultar la cola persistente de refrescos
 * ({@code GET /admin/refresh-queue}).</p>
 */
public class SincronizacionViewModel extends ViewModelBase {

    /** Entradas de la cola de refrescos. */
    private final ObservableList<Map<String, Object>> cola =
            FXCollections.observableArrayList();

    // Mensaje devuelto por la última sincronización.
    private final ObjectProperty<String> ultimoResultado = new SimpleObjectProperty<>("");

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public SincronizacionViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Listo");
    }

    /**
     * Entradas de la cola.
     *
     * @return la lista observable con las tareas de refresco pendientes;
     *         vacía si todavía no se ha consultado la cola.
     */
    public ObservableList<Map<String, Object>> getCola() {
        return cola;
    }

    /**
     * Mensaje de la última sincronización.
     *
     * @return el texto devuelto por la última operación {@code POST /sync},
     *         o cadena vacía si todavía no se ha lanzado ninguna.
     */
    public String getUltimoResultado() {
        return ultimoResultado.get();
    }

    /**
     * Propiedad observable del último resultado.
     *
     * @return la propiedad {@code ObjectProperty} que notifica a la vista
     *         la llegada de un mensaje nuevo de sincronización.
     */
    public ObjectProperty<String> ultimoResultadoProperty() {
        return ultimoResultado;
    }

    /**
     * Dispara la sincronización ({@code POST /sync}).
     */
    public void sincronizar() {
        marcarCargando("Sincronizando fuentes históricas…");
        servicio.sincronizar(
                mensaje -> {
                    ultimoResultado.set(mensaje);
                    marcarOk(mensaje);
                },
                this::marcarFallo);
    }

    /**
     * Carga la cola de refrescos pendientes ({@code GET /admin/refresh-queue}).
     */
    public void cargarCola() {
        marcarCargando("Consultando la cola de refrescos…");
        servicio.colaRefrescos(
                entradas -> {
                    List<Map<String, Object>> copia = entradas == null ? List.of() : entradas;
                    cola.setAll(copia);
                    if (copia.isEmpty()) {
                        marcarVacio("No hay refrescos pendientes.");
                    } else {
                        marcarOk(copia.size() + " tarea(s) en cola");
                    }
                },
                this::marcarFallo);
    }
}
