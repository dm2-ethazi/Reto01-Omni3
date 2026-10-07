package com.omni3.gestorhogwartsjavafx.viewmodel;

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

    /** Estado actual de la pantalla. */
    ObjectProperty<EstadoCarga> estadoProperty();

    /** Mensaje en español para el usuario. */
    StringProperty mensajeProperty();

    /** {@code true} cuando todos los campos validan. */
    BooleanProperty validoProperty();

    /** {@code true} mientras hay una operación en curso. */
    BooleanProperty ocupadoProperty();
}
