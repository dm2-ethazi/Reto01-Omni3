package com.omni3.gestorhogwartsjavafx.servicio;

/**
 * Callback funcional de exito de una operacion asincrona del
 * {@link ServicioAlumnos}.
 *
 * <p>Se invoca siempre en el hilo que fije el publicador del servicio
 * (el hilo FX en produccion, {@code Platform.runLater}), nunca en el hilo
 * de red, de modo que quien lo recibe puede tocar propiedades observables
 * sin bloquear la interfaz (§8 del contrato).</p>
 *
 * @param <T> tipo de resultado que entrega la operacion
 * @author jairoben
 */
@FunctionalInterface
public interface CallbackExito<T> {

    /**
     * Se ejecuta cuando la operacion termina correctamente.
     *
     * @param resultado resultado devuelto por la capa de acceso a la API
     */
    void alExito(T resultado);
}
