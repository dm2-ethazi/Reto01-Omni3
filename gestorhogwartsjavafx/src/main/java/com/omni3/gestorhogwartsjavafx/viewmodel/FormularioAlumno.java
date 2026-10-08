package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

/**
 * Contrato común de los formularios de alumno (alta y edición).
 *
 * <p>Permite que un único controlador de diálogo gestione ambos casos
 * —cargar, validar, guardar y recuperarse de un conflicto de
 * concurrencia— sin distinguir el tipo de viewmodel.</p>
 */
public interface FormularioAlumno {

    /**
     * Valida el formulario en cliente y, si todo es correcto, envía la
     * operación a la API.
     */
    void guardar();

    /**
     * Vuelve a leer la ficha desde la API descartando los cambios
     * locales. En el alta se limpia el formulario.
     */
    void recargar();

    /**
     * Estado actual de la pantalla.
     *
     * @return propiedad observable que sigue el ciclo de carga del formulario
     */
    ObjectProperty<EstadoCarga> estadoProperty();

    /**
     * Ficha creada o guardada; {@code null} mientras no se completa la
     * operación. <strong>Es la señal de éxito</strong>: la vista cierra
     * el diálogo y refresca la tabla cuando recibe un valor distinto de
     * {@code null}, nunca al pasar a {@link EstadoCarga#OK} (que también
     * lo marca la carga de la ficha al abrir la edición).
     *
     * @return propiedad observable de la ficha resultante
     */
    ObjectProperty<AlumnoDto> resultadoProperty();

    /**
     * Mensaje en español para el usuario.
     *
     * @return propiedad observable con el texto mostrado en el diálogo
     */
    StringProperty mensajeProperty();

    /**
     * Identificador de la última petición que falló; vacío si no la hubo.
     * La vista lo muestra junto al error para poder correlacionarlo con
     * los logs (§6 del contrato).
     *
     * @return propiedad observable del identificador de petición
     */
    StringProperty requestIdProperty();

    /**
     * {@code true} cuando todos los campos validan.
     *
     * @return propiedad observable que habilita o deshabilita el botón «Guardar»
     */
    BooleanProperty validoProperty();

    /**
     * {@code true} mientras hay una operación en curso.
     *
     * @return propiedad observable que controla el indicador de espera del formulario
     */
    BooleanProperty ocupadoProperty();
}
