package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.modelo.EstadoConector;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConectoresViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controlador de la pantalla de conectores ({@code GET /connectors}).
 *
 * <p>Muestra las fuentes del entorno —Derby, HSQLDB, H2, Oracle, MariaDB
 * y SQLite— con su estado y el detalle que devuelve la API.</p>
 */
public class ConectoresController implements ControladorVista {

    @FXML private TableView<ConectorDto> tabla;
    @FXML private TableColumn<ConectorDto, String> colOrigen;
    @FXML private TableColumn<ConectorDto, String> colEstado;
    @FXML private TableColumn<ConectorDto, String> colMensaje;
    @FXML private TableColumn<ConectorDto, String> colSincronizacion;
    @FXML private VBox capaVacio;
    @FXML private Label textoVacio;
    @FXML private Label etiquetaConteo;
    @FXML private Label etiquetaMensaje;
    @FXML private Button botonRefrescar;
    @FXML private ProgressIndicator indicadorCarga;

    private ConectoresViewModel viewmodel;

    /**
     * Crea el controlador de la pantalla de conectores.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public ConectoresController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.viewmodel = fabrica.crearConectoresViewModel();

        colOrigen.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().origen() == null ? "—" : d.getValue().origen()));
        colEstado.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().estado() == null ? "—" : d.getValue().estado()));
        colMensaje.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                d.getValue().mensaje() == null ? "—" : d.getValue().mensaje()));
        colSincronizacion.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                cortar(d.getValue().ultimaSincronizacion())));
        colEstado.setCellFactory(column -> new EstadoCelda());
        tabla.setItems(viewmodel.getConectores());

        viewmodel.mensajeProperty().addListener((observable, antes, mensaje) ->
                etiquetaMensaje.setText(mensaje == null ? "" : mensaje));
        viewmodel.estadoProperty().addListener((observable, antes, estado) -> {
            boolean cargando = estado == EstadoCarga.CARGANDO;
            indicadorCarga.setVisible(cargando);
            botonRefrescar.setDisable(cargando);

            boolean vacio = estado == EstadoCarga.VACIO;
            capaVacio.setVisible(vacio);
            capaVacio.setManaged(vacio);
            if (vacio) {
                textoVacio.setText(viewmodel.getMensaje());
            }

            String clase = claseEstado(estado, viewmodel.getMensaje());
            List<String> clases = etiquetaMensaje.getStyleClass();
            clases.remove("texto-error");
            clases.remove("texto-exito");
            clases.remove("texto-aviso");
            clases.remove("texto-secundario");
            clases.add(clase);
        });
        viewmodel.getConectores().addListener(
                (javafx.collections.ListChangeListener<ConectorDto>) cambios ->
                        etiquetaConteo.setText(viewmodel.getConectores().size() + " conector(es)"));

        cargar();
    }

    /** Recarga el estado de los conectores. */
    @FXML
    private void cargar() {
        if (viewmodel != null) {
            viewmodel.cargar();
        }
    }

    /** Recorta el instante ISO a fecha y hora. */
    private static String cortar(String instante) {
        if (instante == null || instante.isBlank()) {
            return "—";
        }
        return instante.length() > 19 ? instante.substring(0, 19) : instante;
    }

    /**
     * Celda del estado que aplica la clase CSS correspondiente: los
     * colores viven en estilos.css (§5: sin hex sueltos en Java).
     */
    private static final class EstadoCelda extends javafx.scene.control.TableCell<ConectorDto, String> {

        /** Clases de estado que esta celda puede llevar. */
        private static final List<String> CLASES = List.of(
                "celda-exito", "celda-aviso", "celda-error", "celda-neutro");

        @Override
        protected void updateItem(String item, boolean vacio) {
            super.updateItem(item, vacio);
            getStyleClass().removeAll(CLASES);
            if (vacio || item == null) {
                setText(null);
                return;
            }
            setText(item);
            EstadoConector estado = EstadoConector.desdeTexto(item);
            String clase = switch (estado) {
                case OK -> "celda-exito";
                case DEGRADADO -> "celda-aviso";
                case CAIDO -> "celda-error";
                default -> "celda-neutro";
            };
            getStyleClass().add(clase);
        }
    }
}
