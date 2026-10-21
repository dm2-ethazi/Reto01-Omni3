package es.r01.desktop.vista;

import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.servicio.CentralService;
import es.r01.desktop.viewmodel.PrincipalViewModel;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controlador de la Vista MVVM: sólo declara controles, enlaza propiedades
 * del {@link PrincipalViewModel} y reenvía eventos. Sin lógica de negocio.
 */
public class PrincipalController implements Initializable {

    @FXML private TextField campoUrl;
    @FXML private TextField campoNombre;
    @FXML private TextField campoDni;
    @FXML private ComboBox<String> comboOrigen;
    @FXML private TableView<Estudiante> tabla;
    @FXML private Label estado;
    @FXML private Button botonBuscar;
    @FXML private Button botonConectar;
    @FXML private Button botonSincronizar;
    @FXML private Button botonSalud;
    @FXML private Button botonLimpiar;

    private PrincipalViewModel viewModel;

    @Override
    public void initialize(URL ubicacion, ResourceBundle recursos) {
        viewModel = new PrincipalViewModel(new CentralService());

        definirColumnas();
        enlazarEstado();
        enlazarEventos();
    }

    private void definirColumnas() {
        anadirColumna("Origen", "origen");
        anadirColumna("Id", "id");
        anadirColumna("DNI", "dni");
        anadirColumna("Nombre", "nombre");
        anadirColumna("Casa", "casa");
        anadirColumna("Patronus", "patronus");
    }

    private void anadirColumna(String titulo, String campo) {
        TableColumn<Estudiante, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(new PropertyValueFactory<>(campo));
        tabla.getColumns().add(columna);
    }

    private void enlazarEstado() {
        campoUrl.textProperty().bindBidirectional(viewModel.urlApiProperty());
        campoNombre.textProperty().bindBidirectional(viewModel.filtroNombreProperty());
        campoDni.textProperty().bindBidirectional(viewModel.filtroDniProperty());
        estado.textProperty().bind(viewModel.estadoProperty());
        // La tabla observa la lista expuesta por el ViewModel.
        tabla.setItems(viewModel.filasProperty());

        // Los botones se desactivan mientras hay una petición en vuelo.
        botonBuscar.disableProperty().bind(viewModel.cargandoProperty());
        botonConectar.disableProperty().bind(viewModel.cargandoProperty());
        botonSincronizar.disableProperty().bind(viewModel.cargandoProperty());
        botonSalud.disableProperty().bind(viewModel.cargandoProperty());
        botonLimpiar.disableProperty().bind(viewModel.cargandoProperty());
        comboOrigen.disableProperty().bind(viewModel.cargandoProperty());
    }

    private void enlazarEventos() {
        comboOrigen.valueProperty().bindBidirectional(viewModel.origenSeleccionadoProperty());
        botonBuscar.setOnAction(evento -> viewModel.buscar());
        botonConectar.setOnAction(evento -> viewModel.conectar());
        botonSincronizar.setOnAction(evento -> viewModel.sincronizar());
        botonSalud.setOnAction(evento -> viewModel.consultarSalud());
        botonLimpiar.setOnAction(evento -> viewModel.limpiar());
        campoUrl.setOnAction(evento -> viewModel.conectar());
        campoNombre.setOnAction(evento -> viewModel.buscar());
        campoDni.setOnAction(evento -> viewModel.buscar());

        // Refresca el combo cuando el ViewModel descubre los conectores.
        viewModel.conectoresDisponibles().clear();
        Platform.runLater(() -> {
            comboOrigen.getItems().setAll(viewModel.conectoresDisponibles());
            if (comboOrigen.getValue() == null) {
                comboOrigen.setValue("central");
            }
        });
    }

    /** Invocada por la Vista al cerrarse para liberar el hilo de la API. */
    public void cerrar() {
        viewModel.apagar();
    }
}
