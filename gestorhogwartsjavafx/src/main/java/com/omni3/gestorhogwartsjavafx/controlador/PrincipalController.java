package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

/**
 * Controlador de la vista principal: cabecera, navegación lateral y barra
 * de estado. Es el único punto que conoce la {@link FabricaAplicacion} y
 * el que carga las pantallas dentro del contenedor central.
 *
 * <p>Los {@code fx:id} de {@code principal.fxml} son contrato con esta
 * clase y no deben renombrarse sin actualizar ambos lados.</p>
 *
 * <p>También gestiona el selector de idioma de la barra de estado: cada
 * opción delega en {@link Idiomas#cambiar(String)} y la aplicación
 * recarga la raíz con el nuevo bundle.</p>
 */
public class PrincipalController implements ControladorVista {

    @FXML private VBox raiz;
    @FXML private Label estado;
    @FXML private Label estadoDerecha;
    @FXML private ImageView iconoCabecera;
    @FXML private StackPane contenedorContenido;
    @FXML private Button navEstado;
    @FXML private Button navAlumnos;
    @FXML private MenuButton botonIdioma;

    private FabricaAplicacion fabrica;
    private Button navegacionActiva;

    /** Pantalla a abrir la primera vez; {@code null} = estado del sistema. */
    private String pantallaInicial;

    /** Pantalla visible en este momento ({@code estado} o {@code alumnos}). */
    private String pantallaActual;

    /**
     * Crea el controlador de la vista principal.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public PrincipalController() {
    }

    /**
     * Fija la pantalla que debe abrirse al conectar; la usa la aplicación
     * al reconstruir la raíz tras un cambio de idioma.
     *
     * @param pantalla {@code estado}, {@code alumnos} o {@code null} para
     *                 la pantalla por defecto
     */
    public void setPantallaInicial(String pantalla) {
        this.pantallaInicial = pantalla;
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.fabrica = fabrica;
        iconoCabecera.setImage(new javafx.scene.image.Image(
                getClass().getResource("/com/omni3/gestorhogwartsjavafx/icons/icon-32.png").toExternalForm()));
        botonIdioma.setText(Idiomas.simbolo());
        registrarAceleradorAyuda();
        abrir(pantallaInicial);
    }

    /**
     * Abre una pantalla por su identificador.
     *
     * @param pantalla {@code alumnos} para el listado; cualquier otro
     *                 valor (incluido {@code null}) abre el estado
     */
    public void abrir(String pantalla) {
        if ("alumnos".equals(pantalla)) {
            cargar("alumnos.fxml", navAlumnos, Idiomas.texto("pantalla.alumnos"));
        } else {
            cargarEstado();
        }
    }

    /**
     * Pantalla visible en este momento.
     *
     * @return {@code estado} o {@code alumnos}; {@code null} si todavía
     *         no se ha cargado ninguna
     */
    public String getPantallaActual() {
        return pantallaActual;
    }

    /** Carga la pantalla de estado del sistema. */
    @FXML
    private void navegarEstado() {
        cargarEstado();
    }

    /** Carga la pantalla de alumnado. */
    @FXML
    private void navegarAlumnos() {
        cargar("alumnos.fxml", navAlumnos, Idiomas.texto("pantalla.alumnos"));
    }

    /** Atajo del botón de cabecera: muestra el estado del sistema. */
    @FXML
    private void comprobarSalud() {
        cargarEstado();
    }

    /** Cambia el idioma de la interfaz a castellano. */
    @FXML
    private void idiomaCastellano() {
        Idiomas.cambiar(Idiomas.CASTELLANO);
    }

    /** Cambia el idioma de la interfaz a euskera. */
    @FXML
    private void idiomaEuskara() {
        Idiomas.cambiar(Idiomas.EUSKERA);
    }

    /** Cambia el idioma de la interfaz a inglés. */
    @FXML
    private void idiomaIngles() {
        Idiomas.cambiar(Idiomas.INGLES);
    }

    /**
     * Muestra el diálogo «Acerca de» con los datos de la aplicación
     * (versión, Java y autor). También es el destino del atajo F1.
     */
    @FXML
    private void acercaDe() {
        Alert acerca = new Alert(Alert.AlertType.INFORMATION);
        acerca.setTitle(Idiomas.texto("dialogo.acerca.titulo"));
        acerca.setHeaderText(null);
        acerca.setContentText(Idiomas.texto("dialogo.acerca.cuerpo",
                System.getProperty("java.version", "")));
        estilizarDialogo(acerca);
        acerca.showAndWait();
    }

    /**
     * Registra F1 (ayuda / Acerca de) sobre la escena vigente.
     *
     * <p>La escena existe al cambiar de idioma mucho después de
     * {@link #conectar(FabricaAplicacion)}, así que se observa su
     * propiedad y se registra también en ese momento. Cada raíz nueva
     * sobrescribe la entrada: el manejador siempre es el controlador
     * actual.</p>
     */
    private void registrarAceleradorAyuda() {
        KeyCodeCombination f1 = new KeyCodeCombination(KeyCode.F1);
        Runnable mostrarAcerca = this::acercaDe;
        if (raiz.getScene() != null) {
            raiz.getScene().getAccelerators().put(f1, mostrarAcerca);
        }
        raiz.sceneProperty().addListener((observable, antes, escena) -> {
            if (escena != null) {
                escena.getAccelerators().put(f1, mostrarAcerca);
            }
        });
    }

    /** Carga la pantalla de estado sin cambiar la navegación activa. */
    private void cargarEstado() {
        cargar("estado.fxml", navEstado, Idiomas.texto("boton.salud"));
    }

    /**
     * Carga un FXML en el contenedor central y marca el botón de
     * navegación correspondiente.
     *
     * @param fichero  nombre del FXML dentro del paquete {@code vista}
     * @param boton    botón lateral que originó la carga
     * @param titulo   título de la pantalla para la barra de estado
     */
    private void cargar(String fichero, Button boton, String titulo) {
        if (fabrica == null) {
            return;
        }
        try {
            FXMLLoader cargador = new FXMLLoader(getClass().getResource(RUTA_VISTAS + fichero));
            cargador.setResources(Idiomas.bundle());
            Parent raiz = cargador.load();
            Object controlador = cargador.getController();
            if (controlador instanceof ControladorVista vista) {
                vista.conectar(fabrica);
            }
            contenedorContenido.getChildren().setAll(raiz);

            if (navegacionActiva != null) {
                navegacionActiva.getStyleClass().remove("activo");
            }
            boton.getStyleClass().add("activo");
            navegacionActiva = boton;
            pantallaActual = fichero.replace(".fxml", "");
            estado.setText(titulo);
        } catch (IOException excepcion) {
            estado.setText(Idiomas.texto("error.carga.pantalla", fichero, excepcion.getMessage()));
        }
    }

    /**
     * Texto de la barra de estado.
     *
     * @return el texto que la barra inferior muestra en su lado izquierdo,
     *         es decir, el título de la pantalla actualmente visible
     */
    public String getTitulo() {
        return estado.getText();
    }

    /**
     * Etiqueta de la barra derecha; la usa la aplicación para el pie.
     *
     * @param texto mensaje que se escribe en la barra de estado de la
     *              derecha
     */
    public void setPie(String texto) {
        estadoDerecha.setText(texto);
    }
}
