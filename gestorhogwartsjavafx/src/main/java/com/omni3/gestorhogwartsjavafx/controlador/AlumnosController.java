package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.viewmodel.BajaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConsultaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EstadoCarga;
import com.omni3.gestorhogwartsjavafx.viewmodel.FormularioAlumno;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de la pantalla de alumnado: consulta unificada con filtro
 * por origen y operaciones de alta, edición y baja (§2 de la propuesta).
 *
 * <p>La pantalla sólo traduce el estado de los viewmodels a elementos
 * visibles; no contiene lógica de negocio ni toca la red (§8).</p>
 *
 * @author jairoben
 */
public class AlumnosController implements ControladorVista {

    /** Botón del diálogo: guardar. */
    private static final ButtonType TIPO_GUARDAR =
            new ButtonType("Guardar", ButtonType.OK.getButtonData());

    /** Botón del diálogo: cancelar. */
    private static final ButtonType TIPO_CANCELAR =
            new ButtonType("Cancelar", ButtonType.CANCEL.getButtonData());

    @FXML private TextField campoBusqueda;
    @FXML private ComboBox<Origen> comboOrigen;
    @FXML private TableView<AlumnoDto> tabla;
    @FXML private TableColumn<AlumnoDto, String> colDni;
    @FXML private TableColumn<AlumnoDto, String> colNombre;
    @FXML private TableColumn<AlumnoDto, String> colCasa;
    @FXML private TableColumn<AlumnoDto, String> colPatronus;
    @FXML private TableColumn<AlumnoDto, String> colOrigen;
    @FXML private VBox capaCarga;
    @FXML private VBox capaVacio;
    @FXML private VBox capaError;
    @FXML private Label textoCarga;
    @FXML private Label textoVacio;
    @FXML private Label textoError;
    @FXML private Label etiquetaConteo;
    @FXML private Label etiquetaMensaje;
    @FXML private Label etiquetaRequestId;
    @FXML private Button botonEditar;
    @FXML private Button botonBaja;

    private FabricaAplicacion fabrica;
    private ConsultaViewModel consulta;
    private BajaViewModel baja;

    /**
     * Crea el controlador de la pantalla de alumnado.
     *
     * <p>El constructor no realiza ninguna inicialización: la configuración
     * de la vista se produce en {@link #conectar(FabricaAplicacion)}, que
     * FXMLLoader invoca al cargar el FXML.</p>
     */
    public AlumnosController() {
    }

    @Override
    public void conectar(FabricaAplicacion fabrica) {
        this.fabrica = fabrica;
        this.consulta = fabrica.crearConsultaViewModel();
        this.baja = fabrica.crearBajaViewModel();

        configurarOrigenes();
        configurarTabla();
        enlazarEstado();

        campoBusqueda.textProperty().bindBidirectional(consulta.textoBusquedaProperty());
        tabla.getSelectionModel().selectedItemProperty().addListener((observable, antes, fila) -> {
            consulta.seleccionadoProperty().set(fila);
            boolean sinFila = fila == null;
            botonEditar.setDisable(sinFila);
        });
        botonBaja.disableProperty().bind(consulta.ocupadoProperty()
                .or(consulta.seleccionadoProperty().isNull()));

        consulta.buscar();
    }

    /** Rellena el combo de orígenes con las fuentes del entorno. */
    private void configurarOrigenes() {
        comboOrigen.getItems().setAll(Origen.values());
        comboOrigen.setValue(Origen.CENTRAL);
        comboOrigen.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Origen origen) {
                return origen == null ? "" : etiquetaDe(origen);
            }

            @Override
            public Origen fromString(String texto) {
                return Origen.CENTRAL;
            }
        });
        comboOrigen.valueProperty().addListener((observable, antes, origen) -> {
            if (origen == null || consulta == null) {
                return;
            }
            // Si el filtro ya está aplicado en el viewmodel (p. ej. después
            // de Limpiar) no se vuelve a consultar: así limpiar() hace una
            // sola petición en lugar de dos.
            if (consulta.getFiltroOrigen() == origen) {
                return;
            }
            consulta.filtroOrigenProperty().set(origen);
            consulta.buscar();
        });
    }

    /** Etiqueta legible de una fuente, con su puerto si es un servicio. */
    private static String etiquetaDe(Origen origen) {
        if (origen.esCentral()) {
            return "Todas las fuentes";
        }
        return switch (origen) {
            case DERBY -> "Derby (1527)";
            case HSQLDB -> "HSQLDB (9001)";
            case H2 -> "H2 (9092)";
            case ORACLE -> "Oracle (1521)";
            case MARIADB -> "MariaDB (3306)";
            case SQLITE -> "SQLite";
            default -> origen.name();
        };
    }

    /** Define las columnas de la tabla de alumnado. */
    private void configurarTabla() {
        colDni.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor(d.getValue().dni())));
        colNombre.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor(d.getValue().nombre())));
        colCasa.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor(d.getValue().casa())));
        colPatronus.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor(d.getValue().patronus())));
        colOrigen.setCellValueFactory(d -> new ReadOnlyStringWrapper(valor(d.getValue().origen())));

        // Casa como píldora de color según la casa
        colCasa.setCellFactory(column -> new TableCell<AlumnoDto, String>() {
            @Override
            protected void updateItem(String casa, boolean vacio) {
                super.updateItem(casa, vacio);
                if (vacio || casa == null || casa.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label pildora = new Label(casa);
                pildora.getStyleClass().add("pildora-casa");
                String casaLower = casa.toLowerCase();
                if (casaLower.equals("gryffindor") || casaLower.equals("slytherin")
                        || casaLower.equals("ravenclaw") || casaLower.equals("hufflepuff")) {
                    pildora.getStyleClass().add(casaLower);
                } else {
                    pildora.getStyleClass().add("hufflepuff"); // fallback neutro
                }
                setGraphic(pildora);
                setText(null);
            }
        });

        tabla.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tabla.setItems(consulta.getAlumnos());
    }

    /** Texto de celda, nunca {@code null}. */
    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "—" : texto;
    }

    /** Refleja el estado de los viewmodels en las capas de la pantalla. */
    private void enlazarEstado() {
        textoCarga.textProperty().bind(consulta.mensajeProperty());

        // El request-id se repinta al cambiar el estado y también cuando
        // llega uno nuevo: dos errores seguidos mantienen el estado en
        // ERROR y no deben conservar el identificador anterior.
        Runnable pintarRequestId = () -> {
            String identificador = consulta.getRequestId();
            etiquetaRequestId.setText(esFallo(consulta.getEstado()) && !identificador.isEmpty()
                    ? "request-id: " + identificador : "");
        };

        consulta.mensajeProperty().addListener((observable, antes, mensaje) ->
                mostrarEnBarra(mensaje, clasePara(consulta.getEstado())));
        consulta.estadoProperty().addListener((observable, antes, estado) -> {
            boolean cargando = estado == EstadoCarga.CARGANDO;
            capaCarga.setVisible(cargando);
            capaCarga.setManaged(cargando);

            boolean vacio = estado == EstadoCarga.VACIO;
            capaVacio.setVisible(vacio);
            capaVacio.setManaged(vacio);
            if (vacio) {
                textoVacio.setText(consulta.getMensaje());
            }

            boolean error = esFallo(estado);
            capaError.setVisible(error);
            capaError.setManaged(error);
            if (error) {
                textoError.setText(consulta.getMensaje());
            }

            botonEditar.setDisable(cargando || consulta.getSeleccionado() == null);
            mostrarEnBarra(consulta.getMensaje(), clasePara(estado));
            pintarRequestId.run();
        });
        consulta.requestIdProperty().addListener((observable, antes, identificador) ->
                pintarRequestId.run());

        // La baja tiene su propio viewmodel: sin repintar su request-id,
        // un fallo de DELETE dejaba en pantalla el identificador de la
        // última consulta (o ninguno). Se escucha también el propio
        // identificador porque marcarFallo publica primero el estado y
        // después el request-id.
        Runnable pintarRequestIdDeBaja = () -> {
            boolean fallo = esFallo(baja.getEstado());
            String identificador = baja.getRequestId();
            etiquetaRequestId.setText(fallo && !identificador.isEmpty()
                    ? "request-id: " + identificador : "");
        };
        baja.estadoProperty().addListener((observable, antes, estado) -> {
            if (esFallo(estado)) {
                mostrarEnBarra(baja.getMensaje(), "texto-error");
            }
            pintarRequestIdDeBaja.run();
        });
        baja.requestIdProperty().addListener((observable, antes, identificador) ->
                pintarRequestIdDeBaja.run());

        consulta.getAlumnos().addListener(
                (javafx.collections.ListChangeListener<AlumnoDto>) cambios ->
                        etiquetaConteo.setText(consulta.getAlumnos().size() + " alumno(s)"));
    }

    /** {@code true} si el estado representa un error o un conflicto. */
    private static boolean esFallo(EstadoCarga estado) {
        return estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO;
    }

    /** Clase CSS que corresponde al estado de la pantalla. */
    private static String clasePara(EstadoCarga estado) {
        if (estado == EstadoCarga.ERROR || estado == EstadoCarga.CONFLICTO) {
            return "texto-error";
        }
        if (estado == EstadoCarga.OK) {
            return "texto-exito";
        }
        if (estado == EstadoCarga.VACIO) {
            return "texto-aviso";
        }
        return "texto-secundario";
    }

    /** Escribe en la barra inferior evitando duplicar el contador de la izquierda. */
    private void mostrarEnBarra(String texto, String clase) {
        if (texto != null && texto.trim().equals(etiquetaConteo.getText())) {
            // Evita duplicar "N alumno(s)" que ya muestra etiquetaConteo
            etiquetaMensaje.setText("");
        } else {
            List<String> clases = etiquetaMensaje.getStyleClass();
            clases.remove("texto-error");
            clases.remove("texto-exito");
            clases.remove("texto-aviso");
            clases.remove("texto-secundario");
            clases.add(clase);
            etiquetaMensaje.setText(texto == null ? "" : texto);
        }
    }

    /** Ejecuta la búsqueda con el texto actual. */
    @FXML
    private void buscar() {
        consulta.buscar();
    }

    /** Reintenta la última consulta tras un error de red o de la API. */
    @FXML
    private void reintentar() {
        consulta.buscar();
    }

    /** Limpia los filtros y recarga el listado completo. */
    @FXML
    private void limpiar() {
        // Primero se limpia el viewmodel (una consulta) y después se
        // refleja en el combo, cuyo listener ya no vuelve a consultar
        // porque el filtro ya está en «Todas las fuentes».
        consulta.limpiarFiltros();
        comboOrigen.setValue(Origen.CENTRAL);
    }

    /** Abre el formulario de alta de un alumno nuevo. */
    @FXML
    private void nuevo() {
        abrirFormulario(null);
    }

    /** Abre el formulario de edición con la fila seleccionada. */
    @FXML
    private void editar() {
        AlumnoDto seleccionado = consulta.getSeleccionado();
        if (seleccionado == null) {
            mostrarEnBarra("Seleccione un alumno en la tabla para editarlo.", "texto-aviso");
            return;
        }
        abrirFormulario(seleccionado.dni());
    }

    /** Confirma y ejecuta la baja del alumno seleccionado. */
    @FXML
    private void baja() {
        AlumnoDto seleccionado = consulta.getSeleccionado();
        if (seleccionado == null) {
            mostrarEnBarra("Seleccione un alumno en la tabla para darlo de baja.", "texto-aviso");
            return;
        }
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Confirmar baja");
        confirmacion.setHeaderText("Dar de baja a " + seleccionado.nombre());
        confirmacion.setContentText("DNI " + seleccionado.dni()
                + "\nSe aplicará sobre la fuente " + seleccionado.origen() + ".");
        estilizarDialogo(confirmacion);
        // Aceptar aplica la baja: se pinta con el color de peligro.
        confirmacion.getDialogPane().lookupButton(ButtonType.OK)
                .getStyleClass().add("boton-peligro");
        Optional<ButtonType> respuesta = confirmacion.showAndWait();
        if (respuesta.isEmpty() || respuesta.get() != ButtonType.OK) {
            return;
        }
        baja.bajar(seleccionado.dni(), () -> {
            consulta.olvidar(seleccionado.dni());
            consulta.buscar();
        });
    }

    /**
     * Carga y muestra el formulario de alta ({@code dni} nulo) o de
     * edición.
     *
     * @param dni DNI de la ficha a editar; {@code null} para un alta nueva
     */
    private void abrirFormulario(String dni) {
        try {
            FXMLLoader cargador = new FXMLLoader(getClass().getResource(RUTA_VISTAS + "formulario-alumno.fxml"));
            Parent raiz = cargador.load();
            FormularioAlumnoController controlador = cargador.getController();
            controlador.conectar(fabrica);
            controlador.preparar(dni);
            FormularioAlumno formulario = controlador.getFormulario();

            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle(dni == null ? "Nuevo alumno" : "Editar alumno");
            dialogo.setHeaderText(null);
            dialogo.getDialogPane().getButtonTypes().addAll(TIPO_GUARDAR, TIPO_CANCELAR);
            dialogo.getDialogPane().setContent(raiz);
            estilizarDialogo(dialogo);
            dialogo.setResizable(true);

            Node botonGuardar = dialogo.getDialogPane().lookupButton(TIPO_GUARDAR);
            botonGuardar.addEventFilter(ActionEvent.ACTION, evento -> {
                evento.consume();
                formulario.guardar();
            });

            // Se cierra solo cuando la operación de guardado termina bien:
            // el estado OK también lo marca la carga de la ficha al abrir
            // la edición, y eso no debe cerrar el diálogo.
            formulario.resultadoProperty().addListener((observable, antes, ficha) -> {
                if (ficha != null) {
                    dialogo.close();
                    consulta.buscar();
                }
            });

            formulario.estadoProperty().addListener((observable, antes, estado) -> {
                if (estado == EstadoCarga.CONFLICTO) {
                    ofrecerRecarga(formulario);
                }
            });

            dialogo.showAndWait();
        } catch (IOException excepcion) {
            mostrarEnBarra("No se pudo abrir el formulario: " + excepcion.getMessage(), "texto-error");
        }
    }

    /**
     * Ante un conflicto de concurrencia (409), ofrece recargar la ficha
     * descartando los cambios locales o seguir editando (§2.3: nunca se
     * sobrescribe en silencio).
     *
     * @param formulario formulario que ha recibido el conflicto
     */
    private void ofrecerRecarga(FormularioAlumno formulario) {
        Alert aviso = new Alert(Alert.AlertType.WARNING);
        aviso.setTitle("Conflicto de concurrencia");
        aviso.setHeaderText("Otra sesión ha modificado esta ficha");
        aviso.setContentText(formulario.mensajeProperty().get()
                + "\n\nPuede recargar la ficha (se descartan los cambios locales)"
                + " o seguir editando sobre la versión vigente.");
        ButtonType recargar = new ButtonType("Recargar ficha");
        ButtonType seguir = new ButtonType("Seguir editando");
        aviso.getButtonTypes().setAll(recargar, seguir);
        estilizarDialogo(aviso);
        Optional<ButtonType> respuesta = aviso.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == recargar) {
            formulario.recargar();
        }
    }
}
