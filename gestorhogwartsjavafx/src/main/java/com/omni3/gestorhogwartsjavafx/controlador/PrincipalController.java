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
    @FXML private Label etiquetaModo;
    @FXML private Label estado;
    @FXML private Label estadoDerecha;
    @FXML private StackPane contenedorContenido;
    @FXML private Button navEstado;
    @FXML private Button navAlumnos;
    @FXML private Button navConectores;
    @FXML private Button navSincronizar;

    private FabricaAplicacion fabrica;
    private Button navegacionActiva;

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.fabrica = fabrica;
        campoUrl.setText(fabrica.getConfig().getBaseUrl());
        etiquetaModo.setText("MODO " + fabrica.getConfig().getModo().name());
        etiquetaModo.getStyleClass().remove("badge-api");
        etiquetaModo.getStyleClass().add(fabrica.getConfig().esDemo() ? "badge-demo" : "badge-api");
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

    /** Aplica la URL de la API escrita en la cabecera. */
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
        if (texto.endsWith("/")) {
            texto = texto.substring(0, texto.length() - 1);
            campoUrl.setText(texto);
        }
        estado.setText("URL de la API: " + texto);
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

    /** Texto de la barra de estado. */
    public String getTitulo() {
        return estado.getText();
    }

    /** Etiqueta de la barra derecha; la usa la aplicación para el pie. */
    public void setPie(String texto) {
        estadoDerecha.setText(texto);
    }
}
