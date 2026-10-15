package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
import com.omni3.gestorhogwartsjavafx.modelo.CatalogoPatronus;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Controlador del formulario de alta y edición de un alumno.
 *
 * <p>Un único formulario atiende los dos casos: el alta se hace siempre
 * en la central MariaDB sin casa y con patronus obligatorio (§2.2), y la
 * edición conserva el origen y la versión previa de la ficha para el
 * control de concurrencia (§2.3).</p>
 *
 * <p>El campo de patronus es un desplegable editable con autocompletado
 * contra el catálogo {@code GET /patronus}: mientras el texto escrito
 * coincida con menos de 15 entradas se muestran las sugerencias; con 15
 * o más (o vacío) el desplegable se cierra. El nombre se muestra en el
 * idioma de la interfaz y, al elegirlo, se carga la imagen del patronus
 * en la vista previa.</p>
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
    @FXML private ComboBox<String> comboPatronus;
    @FXML private ImageView imagenPatronus;
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

    /** Catálogo de patronus cargado desde la API; vacío mientras no llega. */
    private CatalogoPatronus catalogo = CatalogoPatronus.vacio();

    /** {@code true} mientras se aplica un cambio de texto por programa. */
    private boolean programatico;

    /** Patronus original de la ficha en edición; se usa para validar solo cambios. */
    private String patronusOriginal;

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

        comboPatronus.setEditable(true);
        comboPatronus.setValue("");
        comboPatronus.getEditor().textProperty().addListener((observable, antes, texto) -> {
            if (programatico) {
                return;
            }
            alEscribirPatronus(texto);
        });
        comboPatronus.valueProperty().addListener((observable, antes, valor) -> {
            if (programatico || valor == null) {
                return;
            }
            comboPatronus.getEditor().setText(valor);
            sincronizarPatronusVM(valor);
            cargarImagenPatronus(valor);
            comboPatronus.hide();
        });
        cargarCatalogo();
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
        patronusOriginal = null;
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
        aplicarTextoPatronus("");

        campoDni.textProperty().bindBidirectional(alta.dniProperty());
        campoNombre.textProperty().bindBidirectional(alta.nombreProperty());
        alta.patronusProperty().addListener((observable, antes, texto) -> {
            if (programatico) {
                return;
            }
            programatico = true;
            try {
                String valor = texto == null ? "" : texto;
                String visible = visibleEnIdiomaDeLaVentana(valor);
                if (!visible.equals(comboPatronus.getEditor().getText())) {
                    comboPatronus.getEditor().setText(visible);
                    comboPatronus.setValue(visible);
                }
            } finally {
                programatico = false;
            }
        });
        errorDni.textProperty().bind(alta.errorDniProperty());
        errorNombre.textProperty().bind(alta.errorNombreProperty());
        errorCasa.textProperty().bind(alta.errorCasaProperty());
        // Listener en lugar de bind: guardar() pinta aquí el error de
        // catálogo y un bind impediría setText (propiedad vinculada).
        alta.errorPatronusProperty().addListener((observable, antes, texto) ->
                errorPatronus.setText(texto == null ? "" : texto));
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
        edicion.patronusProperty().addListener((observable, antes, texto) -> {
            if (programatico) {
                return;
            }
            programatico = true;
            try {
                String valor = texto == null ? "" : texto;
                String visible = visibleEnIdiomaDeLaVentana(valor);
                if (!visible.equals(comboPatronus.getEditor().getText())) {
                    comboPatronus.getEditor().setText(visible);
                    comboPatronus.setValue(visible);
                }
                if (patronusOriginal == null) {
                    patronusOriginal = valor;
                }
            } finally {
                programatico = false;
            }
        });
        // Casa es de solo lectura en edición: se muestra como dato, no como combo editable
        comboCasa.setDisable(true);
        comboCasa.getStyleClass().remove("campo-combo");
        comboCasa.getStyleClass().add("valor-dato");
        comboCasa.valueProperty().bindBidirectional(edicion.casaProperty());
        errorNombre.textProperty().bind(edicion.errorNombreProperty());
        errorCasa.textProperty().bind(edicion.errorCasaProperty());
        // Listener en lugar de bind: guardar() pinta aquí el error de
        // catálogo y un bind impediría setText (propiedad vinculada).
        edicion.errorPatronusProperty().addListener((observable, antes, texto) ->
                errorPatronus.setText(texto == null ? "" : texto));
        errorDni.setText("");

        edicion.origenTextoProperty().addListener((observable, antes, origen) ->
                campoOrigen.setText(origen == null ? "—" : origen));
        // En edición la casa nunca es editable, independientemente del tipo
        edicion.versionPreviaProperty().addListener((observable, antes, version) ->
                campoVersion.setText(String.valueOf(version)));
        enlazarResultado(edicion);

        edicion.cargar(dni);
    }

    /** Carga el catálogo de patronus desde la API, si aún no está. */
    private void cargarCatalogo() {
        fabrica.getServicio().listarPatronus(
                entradas -> {
                    catalogo = CatalogoPatronus.de(entradas);
                    programatico = true;
                    try {
                        String actual = comboPatronus.getEditor().getText();
                        if (actual != null && !actual.isBlank()) {
                            aplicarTextoPatronus(actual);
                        }
                    } finally {
                        programatico = false;
                    }
                },
                error -> catalogo = CatalogoPatronus.vacio());
    }

    /** Al escribir en el combo: sincroniza el VM, filtra y precarga imagen. */
    private void alEscribirPatronus(String texto) {
        String valor = texto == null ? "" : texto;
        sincronizarPatronusVM(valor);
        aplicarTextoPatronus(valor);
        cargarImagenPatronus(valor);
    }

    /**
     * Escribe el texto en el combo y actualiza las sugerencias.
     *
     * <p>El desplegable solo se abre cuando hay entre 1 y 14
     * coincidencias; con 15 o más (o ninguna) se cierra.</p>
     */
    private void aplicarTextoPatronus(String texto) {
        programatico = true;
        try {
            if (!texto.equals(comboPatronus.getEditor().getText())) {
                comboPatronus.getEditor().setText(texto);
            }
            List<String> sugerencias = catalogo.sugerencias(texto, Idiomas.codigo());
            if (!sugerencias.isEmpty()
                    && sugerencias.size() < CatalogoPatronus.MAXIMO_SUGERENCIAS) {
                comboPatronus.getItems().setAll(sugerencias);
                comboPatronus.show();
            } else {
                comboPatronus.getItems().clear();
                comboPatronus.hide();
            }
        } finally {
            programatico = false;
        }
    }

    /** Pasa el texto del combo al viewmodel en uso. */
    private void sincronizarPatronusVM(String texto) {
        if (formulario instanceof AltaViewModel alta) {
            alta.patronusProperty().set(texto);
        } else if (formulario instanceof EdicionViewModel edicion) {
            edicion.patronusProperty().set(texto);
        }
    }

    /**
     * Nombre visible del patronus en el idioma de la ventana.
     *
     * <p>Si el texto (venga del catálogo en cualquier idioma) coincide
     * con una entrada, se muestra el nombre localizado; si no, se
     * devuelve tal cual para no perder lo que el usuario escribió.</p>
     *
     * @param valor texto actual del campo; puede ser {@code null}
     * @return el nombre en el idioma de la interfaz, o el original
     */
    private String visibleEnIdiomaDeLaVentana(String valor) {
        if (valor == null || valor.isBlank() || catalogo.total() == 0) {
            return valor == null ? "" : valor;
        }
        return catalogo.porNombre(valor)
                .map(entrada -> CatalogoPatronus.nombreEn(entrada, Idiomas.codigo()))
                .orElse(valor);
    }

    /** Carga la imagen del patronus si el nombre está en el catálogo. */
    private void cargarImagenPatronus(String nombre) {
        Optional<PatronusDto> entrada = catalogo.porNombre(nombre);
        if (entrada.isEmpty() || entrada.get().url() == null || entrada.get().url().isBlank()) {
            ocultarImagenPatronus();
            return;
        }
        try {
            Image imagen = new Image(entrada.get().url(), true);
            imagen.errorProperty().addListener((observable, antes, fallo) -> {
                if (Boolean.TRUE.equals(fallo)) {
                    ocultarImagenPatronus();
                }
            });
            imagenPatronus.setImage(imagen);
            imagenPatronus.setVisible(true);
        } catch (IllegalArgumentException excepcion) {
            ocultarImagenPatronus();
        }
    }

    /** Oculta la vista previa de la imagen del patronus. */
    private void ocultarImagenPatronus() {
        imagenPatronus.setImage(null);
        imagenPatronus.setVisible(false);
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

    /**
     * Envia el formulario a la API.
     *
     * <p>En edición, si el patronus ha cambiado respecto al original, se
     * comprueba que exista en el catálogo en el idioma de la interfaz
     * antes de enviar; si no se muestra el error bajo el campo.</p>
     */
    @FXML
    private void guardar() {
        if (formulario == null) {
            return;
        }
        String patronus = comboPatronus.getEditor().getText();
        if (patronus != null && !patronus.isBlank()) {
            // El VM guarda el valor original (puede venir en otro idioma);
            // al guardar se envía el texto visible, localizado.
            sincronizarPatronusVM(patronus.trim());
            boolean enAlta = esAlta;
            boolean cambioEditable = enAlta || !Objects.equals(patronus.trim(), patronusOriginal);
            if (cambioEditable
                    && catalogo.total() > 0
                    && catalogo.porNombreEn(patronus.trim(), Idiomas.codigo()).isEmpty()) {
                errorPatronus.setText(Idiomas.texto("valid.patronus.catalogo"));
                return;
            }
        }
        errorPatronus.setText("");
        formulario.guardar();
    }
}
