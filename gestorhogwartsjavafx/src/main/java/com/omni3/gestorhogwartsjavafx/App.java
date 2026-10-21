package com.omni3.gestorhogwartsjavafx;

import com.omni3.gestorhogwartsjavafx.api.ConfigApi;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.controlador.PrincipalController;
import com.omni3.gestorhogwartsjavafx.util.ExcepcionConfiguracion;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
import com.omni3.gestorhogwartsjavafx.util.Registro;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Punto de entrada de la aplicación de escritorio (JavaFX).
 *
 * <p>Crea la {@link FabricaAplicacion} —configuración, cliente de acceso
 * a la API, servicio de fondo y viewmodels— y se la entrega al
 * controlador de la vista principal. Al cerrarse la ventana se libera el
 * pool de hilos.</p>
 *
 * <p>Es también el responsable del cambio de idioma: registra un oyente
 * en {@link Idiomas} que reconstruye la raíz de la escena con los
 * recursos del nuevo idioma, conservando la pantalla visible.</p>
 *
 * <p>Si la URL de la API no puede resolverse del entorno
 * ({@link ExcepcionConfiguracion}), la aplicación no se cierra: muestra
 * un aviso traducido y continúa con la API local.</p>
 *
 * @author jairoben
 */
public class App extends Application {

    /** Fábrica de la aplicación; se cierra al terminar. */
    private FabricaAplicacion fabrica;

    /** Escenario principal; se conserva para cambiar el título. */
    private Stage escenario;

    /** Escena principal; a ella se le sustituye la raíz al cambiar de idioma. */
    private Scene escena;

    /** Controlador de la raíz vigente; aporta la pantalla actual. */
    private PrincipalController controlador;

    /** Aviso de configuración pendiente de mostrar tras abrir la ventana. */
    private Alert avisoConfiguracion;

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
        cargarFuentes();
        try {
            fabrica = new FabricaAplicacion();
        } catch (ExcepcionConfiguracion | IllegalArgumentException excepcion) {
            // Sin configuración válida la aplicación no se cierra: se
            // avisa con un diálogo traducido y se continúa con la API
            // local para que las pantallas sigan abriéndose.
            fabrica = new FabricaAplicacion(ConfigApi.porDefecto());
            avisoConfiguracion = crearAvisoConfiguracion(excepcion);
            Registro.error("Configuración de la API ausente o inválida ("
                    + excepcion.getClass().getSimpleName()
                    + "); se continúa con la API local", null);
        }
        this.escenario = escenario;
        Idiomas.observar(this::recargarIdioma);

        FXMLLoader cargador = new FXMLLoader(App.class.getResource("vista/fxml/principal.fxml"));
        cargador.setResources(Idiomas.bundle());
        Parent raiz = cargador.load();

        Object controladorCargado = cargador.getController();
        if (controladorCargado instanceof PrincipalController principal) {
            principal.conectar(fabrica);
            controlador = principal;
        }

        Scene escena = new Scene(raiz);
        escena.getStylesheets().add(App.class.getResource("vista/css/tema.css").toExternalForm());
        this.escena = escena;

        // Icono de la ventana
        List<Image> iconos = List.of(
                new Image(App.class.getResource("icons/icon-16.png").toExternalForm()),
                new Image(App.class.getResource("icons/icon-32.png").toExternalForm()),
                new Image(App.class.getResource("icons/icon-64.png").toExternalForm()),
                new Image(App.class.getResource("icons/icon-128.png").toExternalForm()),
                new Image(App.class.getResource("icons/icon-256.png").toExternalForm()),
                new Image(App.class.getResource("icons/icon-512.png").toExternalForm())
        );
        escenario.getIcons().addAll(iconos);

        escenario.setTitle(Idiomas.texto("app.titulo"));
        escenario.setScene(escena);
        // Tamaño de apertura cómodo, con mínimos bajos para poder
        // encoger la ventana; el contenido es escalable (FXML flexible).
        escenario.setWidth(1100);
        escenario.setHeight(720);
        escenario.setMinWidth(640);
        escenario.setMinHeight(480);
        escenario.setOnCloseRequest(evento -> detener());
        escenario.show();
        if (avisoConfiguracion != null) {
            avisoConfiguracion.initOwner(escenario);
            avisoConfiguracion.show();
            avisoConfiguracion = null;
        }
        Registro.info("Aplicación iniciada");
    }

    /**
     * Construye el aviso no modal de configuración ausente o inválida.
     *
     * <p>Usa el bundle del idioma vigente, la hoja de estilos del tema y
     * un botón con tooltip, igual que el resto de diálogos de la
     * aplicación. El diálogo se devuelve sin mostrar para poder
     * adjuntarlo a la ventana principal.</p>
     *
     * @param causa excepción capturada al crear la fábrica; su mensaje
     *              ya viene traducido del bundle
     * @return aviso listo para mostrar
     */
    private Alert crearAvisoConfiguracion(RuntimeException causa) {
        ButtonType entendido = new ButtonType(Idiomas.texto("boton.entendido"),
                ButtonBar.ButtonData.OK_DONE);
        Alert aviso = new Alert(Alert.AlertType.WARNING, causa.getMessage(), entendido);
        aviso.setTitle(Idiomas.texto("config.aviso.titulo"));
        aviso.setHeaderText(Idiomas.texto("config.aviso.cabecera"));
        aviso.getDialogPane().getStylesheets().add(
                App.class.getResource("vista/css/tema.css").toExternalForm());
        // Mismo retoque que ControladorVista.estilizarDialogo: el panel
        // tiene esquinas redondeadas y el fondo por defecto es blanco.
        Scene escenaDialogo = aviso.getDialogPane().getScene();
        if (escenaDialogo != null) {
            escenaDialogo.setFill(Color.web("#1C1914"));
        }
        aviso.getDialogPane().sceneProperty().addListener((observable, antes, nueva) -> {
            if (nueva != null) {
                nueva.setFill(Color.web("#1C1914"));
            }
        });
        if (aviso.getDialogPane().lookupButton(entendido) instanceof Button boton) {
            boton.setTooltip(new Tooltip(Idiomas.texto("tooltip.dialogo.aceptar")));
        }
        return aviso;
    }

    /**
     * Reconstruye la interfaz en el nuevo idioma.
     *
     * <p>Se recarga {@code principal.fxml} con los recursos recién
     * cambiados, se restaura la pantalla que estaba visible y se
     * sustituye la raíz de la escena (el {@code Scene} y su hoja de
     * estilos se conservan).</p>
     */
    private void recargarIdioma() {
        if (escena == null || fabrica == null) {
            return;
        }
        try {
            String pantalla = controlador == null ? null : controlador.getPantallaActual();
            FXMLLoader cargador = new FXMLLoader(App.class.getResource("vista/fxml/principal.fxml"));
            cargador.setResources(Idiomas.bundle());
            Parent raiz = cargador.load();

            Object controladorCargado = cargador.getController();
            if (controladorCargado instanceof PrincipalController principal) {
                principal.setPantallaInicial(pantalla);
                principal.conectar(fabrica);
                controlador = principal;
            }
            escena.setRoot(raiz);
            escenario.setTitle(Idiomas.texto("app.titulo"));
        } catch (IOException excepcion) {
            Registro.aviso("No se pudo recargar la interfaz: " + excepcion.getMessage());
        }
    }

    /**
     * Carga las fuentes tipográficas embebidas del tema (Cinzel para
     * títulos, Cormorant Garamond como serif de apoyo).
     *
     * <p>Si el recurso no está disponible la carga se omite sin error:
     * el CSS del tema cae a la serif del sistema ({@code Georgia}).</p>
     */
    private void cargarFuentes() {
        cargarFuente("fonts/Cinzel.ttf");
        cargarFuente("fonts/CormorantGaramond.ttf");
    }

    /** Carga una fuente del classpath; ignora ausencias (fallback CSS). */
    private void cargarFuente(String ruta) {
        try {
            java.net.URL url = App.class.getResource(ruta);
            if (url != null) {
                Font.loadFont(url.openStream(), -1);
            }
        } catch (Exception error) {
            Registro.aviso("No se pudo cargar la fuente " + ruta
                    + ": " + error.getMessage());
        }
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
            Registro.info("Aplicación detenida");
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
