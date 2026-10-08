package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * Controlador de la vista principal: cabecera, navegación lateral y barra
 * de estado. Es el único punto que conoce la {@link FabricaAplicacion} y
 * el que carga las pantallas dentro del contenedor central.
 *
 * <p>Los {@code fx:id} de {@code principal.fxml} son contrato con esta
 * clase y no deben renombrarse sin actualizar ambos lados.</p>
 */
public class PrincipalController implements ControladorVista {

    @FXML private TextField campoUrl;
    @FXML private Label estado;
    @FXML private Label estadoDerecha;
    @FXML private StackPane contenedorContenido;
    @FXML private Button navEstado;
    @FXML private Button navAlumnos;
    @FXML private Button navConectores;
    @FXML private Button navSincronizar;

    private FabricaAplicacion fabrica;
    private Button navegacionActiva;

    /**
     * Crea el controlador de la vista principal.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public PrincipalController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.fabrica = fabrica;
        campoUrl.setText(fabrica.getConfig().getBaseUrl());
        cargarEstado();
    }

    /** Carga la pantalla de estado del sistema. */
    @FXML
    private void navegarEstado() {
        cargar("estado.fxml", navEstado, "Estado del sistema");
    }

    /** Carga la pantalla de alumnado. */
    @FXML
    private void navegarAlumnos() {
        cargar("alumnos.fxml", navAlumnos, "Alumnos");
    }

    /** Carga la pantalla de conectores. */
    @FXML
    private void navegarConectores() {
        cargar("conectores.fxml", navConectores, "Conectores");
    }

    /** Carga la pantalla de sincronización. */
    @FXML
    private void navegarSincronizar() {
        cargar("sincronizar.fxml", navSincronizar, "Sincronización");
    }

    /** Atajo del botón de cabecera: muestra el estado del sistema. */
    @FXML
    private void comprobarSalud() {
        cargarEstado();
    }

    /** Aplica la URL de la API escrita en la cabecera y comprueba la conexión. */
    @FXML
    private void aplicarUrl() {
        if (fabrica == null) {
            return;
        }
        String texto = campoUrl.getText() == null ? "" : campoUrl.getText().trim();
        if (texto.isEmpty()) {
            campoUrl.setText(fabrica.getConfig().getBaseUrl());
            return;
        }
        try {
            String aplicada = fabrica.aplicarUrl(texto).getBaseUrl();
            campoUrl.setText(aplicada);
            estado.setText("URL de la API: " + aplicada + " · Comprobando conexión…");
            // La pantalla de estado vuelve a consultar la salud con la nueva URL.
            cargarEstado();
        } catch (IllegalArgumentException excepcion) {
            campoUrl.setText(fabrica.getConfig().getBaseUrl());
            estado.setText(excepcion.getMessage());
        }
    }

    /** Carga la pantalla de estado sin cambiar la navegación activa. */
    private void cargarEstado() {
        cargar("estado.fxml", navEstado, "Estado del sistema");
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
            estado.setText(titulo);
        } catch (IOException excepcion) {
            estado.setText("No se pudo cargar " + fichero + ": " + excepcion.getMessage());
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
