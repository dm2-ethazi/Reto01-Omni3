package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;

/**
 * Contrato de las vistas que necesitan acceso a la fábrica de la
 * aplicación (viewmodels y servicio de fondo).
 *
 * <p>El {@link PrincipalController} carga el FXML, localiza el
 * controlador resultante y le entrega la fábrica antes de mostrar la
 * pantalla, de modo que ningún controlador crea sus propios
 * componentes.</p>
 */
@FunctionalInterface
public interface ControladorVista {

    /**
     * Ruta absoluta, dentro del classpath, del paquete de recursos de
     * las vistas (FXML y CSS). Los controladores viven en el paquete
     * {@code controlador}, así que las cargas son relativas a esta
     * constante.
     */
    String RUTA_VISTAS = "/com/omni3/gestorhogwartsjavafx/vista/";

    /**
     * Entrega la fábrica de la aplicación a la vista.
     *
     * @param fabrica fábrica con configuración, servicio y viewmodels; no nula
     */
    void conectar(FabricaAplicacion fabrica);
}
