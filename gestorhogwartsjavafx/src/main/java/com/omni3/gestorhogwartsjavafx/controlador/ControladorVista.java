package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import javafx.scene.control.Dialog;

/**
 * Contrato de las vistas que necesitan acceso a la fábrica de la
 * aplicación (viewmodels y servicio de fondo).
 *
 * <p>El {@link PrincipalController} carga el FXML, localiza el
 * controlador resultante y le entrega la fábrica antes de mostrar la
 * pantalla, de modo que ningún controlador crea sus propios
 * componentes.</p>
 *
 * @author jairoben
 */
@FunctionalInterface
public interface ControladorVista {

    /**
     * Ruta absoluta, dentro del classpath, del paquete de recursos de
     * las vistas (FXML y CSS). Los controladores viven en el paquete
     * {@code controlador}, así que las cargas son relativas a esta
     * constante.
     */
    String RUTA_VISTAS = "/com/omni3/gestorhogwartsjavafx/vista/fxml/";

    /**
     * Entrega la fábrica de la aplicación a la vista.
     *
     * @param fabrica fábrica con configuración, servicio y viewmodels; no nula
     */
    void conectar(FabricaAplicacion fabrica);

    /**
     * Aplica la hoja de estilos de la aplicación a un diálogo nativo
     * y le asigna el icono de la aplicación mediante initOwner.
     *
     * <p>Sin esto, los {@code Alert} se dibujan con el gris y los
     * azules saturados del tema por defecto, fuera de la paleta
     * "azul tenue" (§5 del contrato).</p>
     *
     * <p>También pinta el fondo de la escena del diálogo con el color
     * oscuro del tema: el panel usa esquinas redondeadas y el relleno
     * blanco por defecto de la escena asomaba en las esquinas.</p>
     *
     * @param dialogo diálogo a estilizar; no nulo
     */
    default void estilizarDialogo(Dialog<?> dialogo) {
        dialogo.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/omni3/gestorhogwartsjavafx/vista/css/tema.css").toExternalForm());

        // El panel del diálogo tiene esquinas redondeadas (-fx-background
        // -radius) y el relleno por defecto de la escena es blanco: al
        // crear la escena se pinta de -fondo para que las esquinas
        // redondeadas no asomen en blanco.
        javafx.scene.Scene escenaPrevia = dialogo.getDialogPane().getScene();
        if (escenaPrevia != null) {
            escenaPrevia.setFill(javafx.scene.paint.Color.web("#1C1914"));
        }
        dialogo.getDialogPane().sceneProperty().addListener((observable, antes, escena) -> {
            if (escena != null) {
                escena.setFill(javafx.scene.paint.Color.web("#1C1914"));
            }
        });

        // Asignar owner para heredar el icono de la ventana principal
        javafx.stage.Window owner = null;
        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
            if (w instanceof javafx.stage.Stage && w.isShowing()) {
                owner = w;
                break;
            }
        }
        if (owner != null) {
            dialogo.initOwner(owner);
        }
    }

    /**
     * Devuelve la clase CSS apropiada para un mensaje de estado.
     * El 501 NO_IMPLEMENTADO se muestra como aviso informativo, no como error.
     *
     * @param estado estado de carga actual
     * @param mensaje mensaje asociado (puede ser {@code null})
     * @return clase CSS: "texto-aviso" para 501/NO_IMPLEMENTADO, "texto-error" para otros fallos,
     *         "texto-exito" para OK, "texto-secundario" en caso contrario
     */
    default String claseEstado(EstadoCarga estado, String mensaje) {
        if (estado == EstadoCarga.OK) {
            return "texto-exito";
        }
        if (estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO) {
            // 501 NO_IMPLEMENTADO se trata como aviso informativo
            if (mensaje != null) {
                String m = mensaje.toLowerCase();
                if (m.contains("501") || m.contains("no implementado") || m.contains("no_implementado")) {
                    return "texto-aviso";
                }
            }
            return "texto-error";
        }
        if (estado == EstadoCarga.VACIO) {
            return "texto-aviso";
        }
        return "texto-secundario";
    }
}
