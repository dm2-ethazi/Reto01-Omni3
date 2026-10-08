package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import com.omni3.gestorhogwartsjavafx.viewmodel.SincronizacionViewModel;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controlador de la pantalla de sincronización.
 *
 * <p>Dispara {@code POST /sync} y consulta la cola persistente de
 * refrescos {@code GET /admin/refresh-queue}. Las columnas de la cola se
 * construyen a partir de las claves que devuelve la API, de modo que la
 * vista no se rompe si el backend añade campos.</p>
 */
public class SincronizacionController implements ControladorVista {

    @FXML private Button botonSincronizar;
    @FXML private Button botonCola;
    @FXML private Label campoResultado;
    @FXML private Label etiquetaCola;
    @FXML private Label etiquetaMensaje;
    @FXML private TableView<Map<String, Object>> tablaCola;
    @FXML private VBox capaVacio;
    @FXML private Label textoVacio;
    @FXML private ProgressIndicator indicadorCarga;

    private SincronizacionViewModel viewmodel;

    /**
     * Crea el controlador de la pantalla de sincronización.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public SincronizacionController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.viewmodel = fabrica.crearSincronizacionViewModel();
        tablaCola.setItems(viewmodel.getCola());

        viewmodel.ultimoResultadoProperty().addListener((observable, antes, resultado) ->
                campoResultado.setText(resultado == null || resultado.isBlank() ? "—" : resultado));
        viewmodel.mensajeProperty().addListener((observable, antes, mensaje) ->
                etiquetaMensaje.setText(mensaje == null ? "" : mensaje));
        viewmodel.estadoProperty().addListener((observable, antes, estado) -> {
            boolean cargando = estado == EstadoCarga.CARGANDO;
            indicadorCarga.setVisible(cargando);
            botonSincronizar.setDisable(cargando);
            botonCola.setDisable(cargando);

            boolean vacio = estado == EstadoCarga.VACIO;
            capaVacio.setVisible(vacio);
            capaVacio.setManaged(vacio);
            if (vacio) {
                textoVacio.setText(viewmodel.getMensaje());
            }

            boolean fallo = estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO;
            List<String> clases = etiquetaMensaje.getStyleClass();
            clases.remove("texto-error");
            clases.remove("texto-exito");
            if (fallo) {
                clases.add("texto-error");
            } else if (estado == EstadoCarga.OK) {
                clases.add("texto-exito");
            }
        });

        viewmodel.getCola().addListener(
                (javafx.collections.ListChangeListener<Map<String, Object>>) cambios -> {
                    reconstruirColumnas();
                    etiquetaCola.setText(viewmodel.getCola().size() + " tarea(s)");
                });

        viewmodel.cargarCola();
    }

    /** Dispara la sincronización. */
    @FXML
    private void sincronizar() {
        viewmodel.sincronizar();
    }

    /** Recarga la cola de refrescos pendientes. */
    @FXML
    private void cargarCola() {
        viewmodel.cargarCola();
    }

    /**
     * Crea una columna por cada clave presente en las filas de la cola,
     * conservando el orden en el que aparecen.
     */
    private void reconstruirColumnas() {
        tablaCola.getColumns().clear();
        Set<String> claves = new LinkedHashSet<>();
        for (Map<String, Object> fila : viewmodel.getCola()) {
            claves.addAll(fila.keySet());
        }
        for (String clave : claves) {
            TableColumn<Map<String, Object>, String> columna = new TableColumn<>(clave);
            columna.setCellValueFactory(datos -> {
                Object valor = datos.getValue().get(clave);
                return new ReadOnlyStringWrapper(valor == null ? "—" : String.valueOf(valor));
            });
            columna.setPrefWidth(clave.length() > 12 ? 190 : 130);
            tablaCola.getColumns().add(columna);
        }
    }
}
