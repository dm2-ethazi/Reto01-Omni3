package com.omni3.gestorhogwartsjavafx.util;

/**
 * Error controlado cuando la URL de la API no puede resolverse desde el
 * entorno (ni propiedad de sistema, ni variable de entorno, ni
 * {@code config.properties}).
 *
 * <p>La aplicación la captura al arrancar, muestra el aviso traducido y
 * continúa con la API local: nunca se escapa como excepción sin tratar
 * ni rompe la ventana.</p>
 *
 * @author jairoben
 */
public class ExcepcionConfiguracion extends RuntimeException {

    /** Identificador de versión de la serialización. */
    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción con el mensaje ya traducido.
     *
     * @param mensaje mensaje para el usuario; procede del bundle de
     *                idioma y no contiene credenciales
     */
    public ExcepcionConfiguracion(String mensaje) {
        super(mensaje);
    }
}
