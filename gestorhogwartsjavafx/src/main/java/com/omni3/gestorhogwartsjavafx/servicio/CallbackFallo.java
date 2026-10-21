package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ExcepcionApi;

/**
 * Callback funcional de fallo de una operacion asincrona del
 * {@link ServicioAlumnos}.
 *
 * <p>Recibe siempre una {@link ExcepcionApi} con codigo de negocio,
 * mensaje en espanol, {@code request-id} y estado HTTP, tanto si el fallo
 * procede de la respuesta como si es un error local. Se invoca en el
 * mismo hilo que {@link CallbackExito} (el hilo FX en produccion).</p>
 * @author jairoben
 */
@FunctionalInterface
public interface CallbackFallo {

    /**
     * Se ejecuta cuando la operacion termina con error.
     *
     * @param error error de la capa de API; nunca {@code null}
     */
    void alFallo(ExcepcionApi error);
}
