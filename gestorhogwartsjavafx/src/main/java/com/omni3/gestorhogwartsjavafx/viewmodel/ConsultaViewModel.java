package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Locale;

/**
 * Viewmodel de la pantalla de consulta unificada de alumnado.
 *
 * <p>Gestiona la búsqueda libre (DNI o nombre), el filtro por fuente de
 * origen, la lista resultante y los estados vacío/cargando/error. La
 * consulta se despacha siempre en el hilo de fondo del
 * {@link ServicioAlumnos} (§8).</p>
 */
public class ConsultaViewModel extends ViewModelBase {

    /** Máximo de filas que se piden a la API en una consulta. */
    public static final int LIMITE_POR_DEFECTO = 500;

    /** Filas de la tabla. */
    private final ObservableList<AlumnoDto> alumnos = FXCollections.observableArrayList();

    /** Texto de búsqueda libre sobre DNI o nombre. */
    private final StringProperty textoBusqueda = new SimpleStringProperty("");

    /** Filtro por origen; {@link Origen#CENTRAL} significa «todas». */
    private final ObjectProperty<Origen> filtroOrigen =
            new SimpleObjectProperty<>(Origen.CENTRAL);

    /** Fila seleccionada en la tabla; puede ser nula. */
    private final ObjectProperty<AlumnoDto> seleccionado = new SimpleObjectProperty<>();

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     */
    public ConsultaViewModel(ServicioAlumnos servicio) {
        super(servicio);
        reiniciar("Listo");
    }

    /** Filas visibles en la tabla. */
    public ObservableList<AlumnoDto> getAlumnos() {
        return alumnos;
    }

    /** Texto de búsqueda. */
    public String getTextoBusqueda() {
        return textoBusqueda.get();
    }

    /** Propiedad observable de la búsqueda. */
    public StringProperty textoBusquedaProperty() {
        return textoBusqueda;
    }

    /** Filtro de origen activo. */
    public Origen getFiltroOrigen() {
        return filtroOrigen.get();
    }

    /** Propiedad observable del filtro de origen. */
    public ObjectProperty<Origen> filtroOrigenProperty() {
        return filtroOrigen;
    }

    /** Fila seleccionada. */
    public AlumnoDto getSeleccionado() {
        return seleccionado.get();
    }

    /** Propiedad observable de la selección. */
    public ObjectProperty<AlumnoDto> seleccionadoProperty() {
        return seleccionado;
    }

    /**
     * Ejecuta la consulta con el texto de búsqueda y el filtro actuales.
     */
    public void buscar() {
        marcarCargando("Consultando alumnado…");
        Origen origen = filtroOrigen.get();
        String texto = textoBusqueda.get() == null ? "" : textoBusqueda.get().trim();
        servicio.listarAlumnos(origen, texto, LIMITE_POR_DEFECTO, 0,
                lista -> {
                    alumnos.setAll(lista);
                    if (lista.isEmpty()) {
                        marcarVacio(origen.esCentral() && texto.isEmpty()
                                ? "No hay alumnos registrados todavía."
                                : "Ningún alumno coincide con el filtro aplicado.");
                    } else {
                        marcarOk(lista.size() + " alumno(s)");
                    }
                },
                this::marcarFallo);
    }

    /**
     * Limpia el texto de búsqueda y el filtro de origen y vuelve a
     * consultar el listado completo.
     */
    public void limpiarFiltros() {
        textoBusqueda.set("");
        filtroOrigen.set(Origen.CENTRAL);
        buscar();
    }

    /**
     * Borra la tabla y deja la pantalla inactiva.
     */
    public void vaciar() {
        alumnos.clear();
        seleccionado.set(null);
        reiniciar("Listo");
    }

    /**
     * Elimina de la tabla local la fila dada de baja, sin volver a
     * consultar a la API.
     *
     * @param dni DNI del alumno que ya no debe aparecer
     */
    public void olvidar(String dni) {
        if (dni == null) {
            return;
        }
        String normalizado = dni.trim().toUpperCase(Locale.ROOT);
        alumnos.removeIf(alumno -> normalizado.equals(alumno.dni()));
        if (alumnos.isEmpty()) {
            marcarVacio("No hay alumnos registrados todavía.");
        }
    }

    /**
     * Sustituye una fila de la tabla por la versión recién guardada.
     *
     * @param actualizado alumno devuelto por la API tras la edición
     */
    public void reemplazar(AlumnoDto actualizado) {
        if (actualizado == null) {
            return;
        }
        for (int i = 0; i < alumnos.size(); i++) {
            if (alumnos.get(i).dni().equals(actualizado.dni())) {
                alumnos.set(i, actualizado);
                seleccionado.set(actualizado);
                return;
            }
        }
        alumnos.add(actualizado);
        seleccionado.set(actualizado);
    }
}
