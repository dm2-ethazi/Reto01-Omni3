package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.ExcepcionApi;
import com.omni3.gestorhogwartsjavafx.servicio.ServicioAlumnos;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

/**
 * Base de todos los viewmodels de la aplicación (patrón MVVM).
 *
 * <p>Concentra el estado común de pantalla —cargando, mensaje en español,
 * ocupado y {@code request-id}— y las operaciones de transición. La vista
 * <strong>sólo lee</strong> las propiedades: nunca interpreta errores de
 * red ni decide cómo pintarlos (§8 del contrato).</p>
 *
 * <p>Todos los viewmodels reciben el {@link ServicioAlumnos} ya montado,
 * de modo que las pruebas pueden inyectar un doble de pruebas con
 * latencia 0 y un executor síncrono, sin arrancar el toolkit JavaFX.</p>
 */
public abstract class ViewModelBase {

    /** Servicio de fondo compartido por toda la aplicación. */
    protected final ServicioAlumnos servicio;

    /** Estado de la pantalla. */
    private final ObjectProperty<EstadoCarga> estado =
            new SimpleObjectProperty<>(EstadoCarga.INACTIVO);

    /** Mensaje en español para el usuario (resultado o motivo de error). */
    private final StringProperty mensaje = new SimpleStringProperty("");

    /** {@code true} mientras hay una operación en curso. */
    private final BooleanProperty ocupado = new SimpleBooleanProperty(false);

    /** Identificador de la última petición que falló, para soporte. */
    private final StringProperty requestId = new SimpleStringProperty("");

    /**
     * Crea el viewmodel.
     *
     * @param servicio servicio de fondo; no nulo
     * @throws NullPointerException si {@code servicio} es nulo
     */
    protected ViewModelBase(ServicioAlumnos servicio) {
        this.servicio = Objects.requireNonNull(servicio, "El servicio es obligatorio");
    }

    /** Estado actual de la pantalla. */
    public final EstadoCarga getEstado() {
        return estado.get();
    }

    /** Propiedad observable del estado. */
    public final ObjectProperty<EstadoCarga> estadoProperty() {
        return estado;
    }

    /** Mensaje visible para el usuario. */
    public final String getMensaje() {
        return mensaje.get();
    }

    /** Propiedad observable del mensaje. */
    public final StringProperty mensajeProperty() {
        return mensaje;
    }

    /** Indica si hay una operación en marcha. */
    public final boolean isOcupado() {
        return ocupado.get();
    }

    /** Propiedad observable de ocupado. */
    public final BooleanProperty ocupadoProperty() {
        return ocupado;
    }

    /** Identificador de la última petición fallida. */
    public final String getRequestId() {
        return requestId.get();
    }

    /** Propiedad observable del identificador de petición. */
    public final StringProperty requestIdProperty() {
        return requestId;
    }

    /** Servicio de fondo utilizado por este viewmodel. */
    public final ServicioAlumnos getServicio() {
        return servicio;
    }

    /**
     * Pasa la pantalla a «cargando».
     *
     * @param texto mensaje mientras carga; puede ser vacío
     */
    protected void marcarCargando(String texto) {
        estado.set(EstadoCarga.CARGANDO);
        ocupado.set(true);
        mensaje.set(texto == null ? "" : texto);
    }

    /**
     * Pasa la pantalla a «ok» con resultado.
     *
     * @param texto mensaje de éxito; puede ser vacío
     */
    protected void marcarOk(String texto) {
        estado.set(EstadoCarga.OK);
        ocupado.set(false);
        mensaje.set(texto == null ? "" : texto);
    }

    /**
     * Pasa la pantalla a «vacío» (consulta sin resultados).
     *
     * @param texto mensaje para el usuario
     */
    protected void marcarVacio(String texto) {
        estado.set(EstadoCarga.VACIO);
        ocupado.set(false);
        mensaje.set(texto);
    }

    /**
     * Pasa la pantalla a «inactivo».
     *
     * @param texto mensaje informativo
     */
    protected void marcarInactivo(String texto) {
        estado.set(EstadoCarga.INACTIVO);
        ocupado.set(false);
        mensaje.set(texto == null ? "" : texto);
    }

    /**
     * Traduce un error de la capa de API al estado de pantalla.
     *
     * <p>Un conflicto de concurrencia (HTTP 409) se publica como
     * {@link EstadoCarga#CONFLICTO} para que la vista ofrezca recarga sin
     * sobrescribir (§2.3); el resto, como {@link EstadoCarga#ERROR}.</p>
     *
     * @param error error recibido de la capa de acceso; no nulo
     */
    protected void marcarFallo(ExcepcionApi error) {
        Objects.requireNonNull(error, "El error es obligatorio");
        estado.set(error.esConflicto() ? EstadoCarga.CONFLICTO : EstadoCarga.ERROR);
        ocupado.set(false);
        mensaje.set(error.getMensaje());
        requestId.set(error.getRequestId() == null ? "" : error.getRequestId());
    }

    /**
     * Traduce un error al estado de pantalla <strong>sin</strong> posibilidad
     * de conflicto de concurrencia.
     *
     * <p>Se usa en el alta: al no haber versión previa, un 409 de la API
     * significa que el DNI ya existe y no que otra sesión haya modificado la
     * ficha, así que la vista no debe ofrecer recargar (§2.3 se reserva para
     * la edición).</p>
     *
     * @param error error recibido de la capa de acceso; no nulo
     */
    protected void marcarError(ExcepcionApi error) {
        Objects.requireNonNull(error, "El error es obligatorio");
        estado.set(EstadoCarga.ERROR);
        ocupado.set(false);
        mensaje.set(error.getMensaje());
        requestId.set(error.getRequestId() == null ? "" : error.getRequestId());
    }

    /**
     * Reinicia la pantalla a su estado inicial con el mensaje indicado.
     *
     * @param texto mensaje inicial de la pantalla; puede ser vacío o nulo
     */
    protected void reiniciar(String texto) {
        estado.set(EstadoCarga.INACTIVO);
        ocupado.set(false);
        mensaje.set(texto == null ? "" : texto);
        requestId.set("");
    }

    /**
     * Reinicia la pantalla a su estado inicial sin mensaje.
     */
    protected void reiniciar() {
        reiniciar("");
    }
}
