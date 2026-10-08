package com.omni3.gestorhogwartsjavafx;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.controlador.PrincipalController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Punto de entrada de la aplicación de escritorio (JavaFX).
 *
 * <p>Crea la {@link FabricaAplicacion} —configuración, cliente de acceso
 * a la API, servicio de fondo y viewmodels— y se la entrega al
 * controlador de la vista principal. Al cerrarse la ventana se libera el
 * pool de hilos.</p>
 */
public class App extends Application {

    /** Título de la ventana. */
    public static final String TITULO = "Gestor Hogwarts - Cliente de escritorio";

    /** Fábrica de la aplicación; se cierra al terminar. */
    private FabricaAplicacion fabrica;

    /**
     * Crea la aplicación de escritorio.
     *
     * <p>El constructor no realiza ninguna inicialización: toda la
     * configuración ocurre en {@link #start(Stage)}, que la plataforma
     * JavaFX invoca al lanzar la aplicación.</p>
     */
    public App() {
    }

    @Override
    public void start(Stage escenario) throws Exception {
        fabrica = new FabricaAplicacion();

        FXMLLoader cargador = new FXMLLoader(App.class.getResource("vista/principal.fxml"));
        Parent raiz = cargador.load();

        Object controlador = cargador.getController();
        if (controlador instanceof PrincipalController principal) {
            principal.conectar(fabrica);
        }

        Scene escena = new Scene(raiz);
        escena.getStylesheets().add(App.class.getResource("vista/estilos.css").toExternalForm());

        escenario.setTitle(TITULO);
        escenario.setScene(escena);
        escenario.setMinWidth(1040);
        escenario.setMinHeight(640);
        escenario.setOnCloseRequest(evento -> detener());
        escenario.show();
    }

    @Override
    public void stop() {
        detener();
    }

    /** Cierra el servicio de fondo si todavía está abierto. */
    private void detener() {
        if (fabrica != null) {
            fabrica.cerrar();
            fabrica = null;
        }
    }

    /**
     * Punto de entrada de la aplicación con línea de comandos.
     *
     * <p>Delega en {@link Application#launch(String...)} el arranque del
     * ciclo de vida de JavaFX, que a su vez llama a {@link #start(Stage)}.</p>
     *
     * @param args argumentos de línea de comandos; no se utilizan en esta
     *             aplicación
     */
    public static void main(String[] args) {
        launch(args);
    }
}
