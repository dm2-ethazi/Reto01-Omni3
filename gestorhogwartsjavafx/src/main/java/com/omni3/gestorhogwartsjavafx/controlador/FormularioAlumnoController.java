package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
import com.omni3.gestorhogwartsjavafx.viewmodel.AltaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EdicionViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import com.omni3.gestorhogwartsjavafx.viewmodel.FormularioAlumno;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Controlador del formulario de alta y edición de un alumno.
 *
 * <p>Un único formulario atiende los dos casos: el alta se hace siempre
 * en la central MariaDB sin casa y con patronus obligatorio (§2.2), y la
 * edición conserva el origen y la versión previa de la ficha para el
 * control de concurrencia (§2.3).</p>
 */
public class FormularioAlumnoController implements ControladorVista {

    /** Casas de Hogwarts ofrecidas en el desplegable. */
    private static final List<String> CASAS =
            List.of("Gryffindor", "Slytherin", "Hufflepuff", "Ravenclaw");

    @FXML private Label tituloFormulario;
    @FXML private Label textoAyuda;
    @FXML private TextField campoDni;
    @FXML private TextField campoNombre;
    @FXML private ComboBox<String> comboCasa;
    @FXML private TextField campoPatronus;
    @FXML private Label errorDni;
    @FXML private Label errorNombre;
    @FXML private Label errorCasa;
    @FXML private Label errorPatronus;
    @FXML private Label campoOrigen;
    @FXML private Label campoVersion;
    @FXML private Label etiquetaResultado;
    @FXML private Label etiquetaRequestId;

    private FabricaAplicacion fabrica;
    private FormularioAlumno formulario;
    private boolean esAlta;

    /**
     * Crea el controlador del formulario de alta y edición de un alumno.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)} y
     * {@link #preparar(String)}, que FXMLLoader invoca al cargar el FXML.</p>
     */
    public FormularioAlumnoController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.fabrica = fabrica;
        comboCasa.getItems().clear();
        comboCasa.getItems().add("");
        comboCasa.getItems().addAll(CASAS);
        comboCasa.setValue("");
    }

    /**
     * Configura el formulario para un alta o para la edición de una ficha.
     *
     * <p>Debe invocarse después de {@link #conectar(FabricaAplicacion)} y
     * antes de mostrar el diálogo.</p>
     *
     * @param dni DNI de la ficha a editar; {@code null} para un alta nueva
     * @throws IllegalStateException si falta la llamada a
     *         {@link #conectar(FabricaAplicacion)}
     */
    public void preparar(String dni) {
        if (fabrica == null) {
            throw new IllegalStateException("Llame a conectar() antes de preparar()");
        }
        esAlta = dni == null;
        if (esAlta) {
            prepararAlta();
        } else {
            prepararEdicion(dni);
        }
    }

    /** Configura y liga el viewmodel de alta. */
    private void prepararAlta() {
        AltaViewModel alta = fabrica.crearAltaViewModel();
        formulario = alta;
        tituloFormulario.setText(Idiomas.texto("form.titulo.nuevo"));
        textoAyuda.setText(Idiomas.texto("form.ayuda.nuevo"));
        campoDni.setDisable(false);
        campoDni.setText("");
        comboCasa.setDisable(true);
        comboCasa.setValue("");
        campoOrigen.setText(Origen.MARIADB.name());
        campoVersion.setText(Idiomas.texto("form.version.nueva"));

        campoDni.textProperty().bindBidirectional(alta.dniProperty());
        campoNombre.textProperty().bindBidirectional(alta.nombreProperty());
        campoPatronus.textProperty().bindBidirectional(alta.patronusProperty());
        errorDni.textProperty().bind(alta.errorDniProperty());
        errorNombre.textProperty().bind(alta.errorNombreProperty());
        errorCasa.textProperty().bind(alta.errorCasaProperty());
        errorPatronus.textProperty().bind(alta.errorPatronusProperty());
        enlazarResultado(alta);
    }

    /** Configura y liga el viewmodel de edición. */
    private void prepararEdicion(String dni) {
        EdicionViewModel edicion = fabrica.crearEdicionViewModel();
        formulario = edicion;
        tituloFormulario.setText(Idiomas.texto("form.titulo.editar"));
        textoAyuda.setText(Idiomas.texto("form.ayuda.editar"));
        campoDni.setDisable(true);
        campoDni.textProperty().bind(edicion.dniProperty());
        campoNombre.textProperty().bindBidirectional(edicion.nombreProperty());
        campoPatronus.textProperty().bindBidirectional(edicion.patronusProperty());
        // Casa es de solo lectura en edición: se muestra como dato, no como combo editable
        comboCasa.setDisable(true);
        comboCasa.getStyleClass().remove("campo-combo");
        comboCasa.getStyleClass().add("valor-dato");
        comboCasa.valueProperty().bindBidirectional(edicion.casaProperty());
        errorNombre.textProperty().bind(edicion.errorNombreProperty());
        errorCasa.textProperty().bind(edicion.errorCasaProperty());
        errorPatronus.textProperty().bind(edicion.errorPatronusProperty());
        errorDni.setText("");

        edicion.origenTextoProperty().addListener((observable, antes, origen) ->
                campoOrigen.setText(origen == null ? "—" : origen));
        // En edición la casa nunca es editable, independientemente del tipo
        edicion.versionPreviaProperty().addListener((observable, antes, version) ->
                campoVersion.setText(String.valueOf(version)));
        enlazarResultado(edicion);

        edicion.cargar(dni);
    }

    /** Liga el mensaje del formulario, su color de estado y el request-id. */
    private void enlazarResultado(FormularioAlumno vm) {
        etiquetaResultado.textProperty().bind(vm.mensajeProperty());

        // El identificador se pinta al cambiar el estado o al llegar uno
        // nuevo, para que dos errores seguidos no dejen en pantalla el
        // request-id anterior. Solo se muestra si la petición ha fallado.
        Runnable pintarRequestId = () -> {
            EstadoCarga estadoActual = vm.estadoProperty().get();
            boolean fallo = estadoActual == EstadoCarga.ERROR
                    || estadoActual == EstadoCarga.CONFLICTO;
            String identificador = vm.requestIdProperty().get();
            etiquetaRequestId.setText(fallo && identificador != null && !identificador.isEmpty()
                    ? "request-id: " + identificador : "");
        };
        vm.estadoProperty().addListener((observable, antes, estado) -> {
            boolean fallo = estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO;
            String clase = fallo ? "texto-error" : "texto-exito";
            List<String> clases = etiquetaResultado.getStyleClass();
            clases.remove("texto-error");
            clases.remove("texto-exito");
            if (estado == EstadoCarga.OK || fallo) {
                clases.add(clase);
            }
            pintarRequestId.run();
        });
        vm.requestIdProperty().addListener((observable, antes, identificador) ->
                pintarRequestId.run());
    }

    /**
     * Viewmodel en uso; lo usa la vista contenedora para cerrar el diálogo.
     *
     * @return el viewmodel del formulario activo (alta o edición), o
     *         {@code null} si todavía no se ha llamado a
     *         {@link #preparar(String)}
     */
    public FormularioAlumno getFormulario() {
        return formulario;
    }

    /**
     * DNI del formulario; vacío en el alta.
     *
     * @return el DNI mostrado en el campo del formulario; cadena vacía si
     *         el formulario está preparado para un alta nueva
     */
    public String getDni() {
        if (esAlta) {
            return "";
        }
        return campoDni.getText();
    }

    /** Envia el formulario a la API. */
    @FXML
    private void guardar() {
        if (formulario != null) {
            formulario.guardar();
        }
    }
}
