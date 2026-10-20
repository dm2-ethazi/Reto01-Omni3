package com.omni3.gestorhogwartsjavafx.viewmodel;

/**
 * Estados genericos de una pantalla gestionada por un viewmodel.
 *
 * <p>La vista solo lee este enumerado: nunca interpreta errores de red ni
 * decide como pintarlos. Cada viewmodel publica ademas su mensaje de error
 * en espanol y su codigo de negocio.</p>
 */
public enum EstadoCarga {

    /** Sin operacion en marcha y sin resultados todavia. */
    INACTIVO,

    /** Operacion en curso: la vista muestra un indicador de carga. */
    CARGANDO,

    /** Operacion terminada con resultados. */
    OK,

    /** Operacion terminada sin resultados (lista vacia). */
    VACIO,

    /** Operacion terminada con error (red, validacion, 404, etc.). */
    ERROR,

    /**
     * Conflicto de concurrencia (HTTP 409): la ficha se ha recargado y
     * <strong>nunca</strong> se ha sobrescrito (§2.3 del contrato).
     */
    CONFLICTO
}
