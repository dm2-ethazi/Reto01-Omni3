package com.omni3.gestorhogwartsjavafx.controlador;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;
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
import javafx.scene.control.Tooltip;
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

    /** Botón del diálogo: guardar; se crea al conectar (texto traducido). */
    private ButtonType tipoGuardar;

    /** Botón del diálogo: cancelar; se crea al conectar (texto traducido). */
    private ButtonType tipoCancelar;

    @FXML private TextField campoBusqueda;
    @FXML private VBox pantalla;
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
        this.tipoGuardar = new ButtonType(Idiomas.texto("comun.guardar"),
                ButtonType.OK.getButtonData());
        this.tipoCancelar = new ButtonType(Idiomas.texto("comun.cancelar"),
                ButtonType.CANCEL.getButtonData());

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

        registrarAceleradores();
        consulta.buscar();
    }

    /**
     * Atajos de la pantalla de alumnado: Ctrl/Cmd+N (nuevo alumno) y
     * Ctrl/Cmd+F (buscar).
     *
     * <p>Se registran sobre la escena, que es común a todas las
     * pantallas: por eso el manejador comprueba que esta pantalla siga
     * colgada de la escena antes de actuar (tras navegar a otra
     * pantalla el nodo queda suelto y los atajos se ignoran). Cada vez
     * que se vuelve a cargar la pantalla, la entrada se sobrescribe con
     * el controlador nuevo.</p>
     */
    private void registrarAceleradores() {
        javafx.scene.input.KeyCodeCombination ctrlN =
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.N,
                        javafx.scene.input.KeyCombination.SHORTCUT_DOWN);
        javafx.scene.input.KeyCodeCombination ctrlF =
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F,
                        javafx.scene.input.KeyCombination.SHORTCUT_DOWN);
        Runnable atajoNuevo = () -> {
            if (pantalla.getScene() != null) {
                nuevo();
            }
        };
        Runnable atajoBuscar = () -> {
            if (pantalla.getScene() != null) {
                buscar();
            }
        };
        if (pantalla.getScene() != null) {
            pantalla.getScene().getAccelerators().put(ctrlN, atajoNuevo);
            pantalla.getScene().getAccelerators().put(ctrlF, atajoBuscar);
        }
        pantalla.sceneProperty().addListener((observable, antes, escena) -> {
            if (escena != null) {
                escena.getAccelerators().put(ctrlN, atajoNuevo);
                escena.getAccelerators().put(ctrlF, atajoBuscar);
            }
        });
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
            return Idiomas.texto("origen.todas");
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
        // No se puede fijar en FXML: CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        // no es coercible como Callback y FXMLLoader lanza IllegalArgumentException.
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
                        etiquetaConteo.setText(Idiomas.texto("conteo.alumnos",
                                String.valueOf(consulta.getAlumnos().size()))));
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
            mostrarEnBarra(Idiomas.texto("aviso.seleccionar.editar"), "texto-aviso");
            return;
        }
        abrirFormulario(seleccionado.dni());
    }

    /** Confirma y ejecuta la baja del alumno seleccionado. */
    @FXML
    private void baja() {
        AlumnoDto seleccionado = consulta.getSeleccionado();
        if (seleccionado == null) {
            mostrarEnBarra(Idiomas.texto("aviso.seleccionar.baja"), "texto-aviso");
            return;
        }
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle(Idiomas.texto("dialogo.baja.titulo"));
        confirmacion.setHeaderText(Idiomas.texto("dialogo.baja.cabecera", seleccionado.nombre()));
        confirmacion.setContentText(Idiomas.texto("dialogo.baja.cuerpo",
                seleccionado.dni(), seleccionado.origen()));
        estilizarDialogo(confirmacion);
        // Aceptar aplica la baja: se pinta con el color de peligro.
        confirmacion.getDialogPane().lookupButton(ButtonType.OK)
                .getStyleClass().add("boton-peligro");
        ayudar(confirmacion.getDialogPane().lookupButton(ButtonType.OK),
                Idiomas.texto("tooltip.baja.ok"));
        ayudar(confirmacion.getDialogPane().lookupButton(ButtonType.CANCEL),
                Idiomas.texto("tooltip.baja.cancel"));
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
            cargador.setResources(Idiomas.bundle());
            Parent raiz = cargador.load();
            FormularioAlumnoController controlador = cargador.getController();
            controlador.conectar(fabrica);
            controlador.preparar(dni);
            FormularioAlumno formulario = controlador.getFormulario();

            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle(Idiomas.texto(dni == null ? "form.titulo.nuevo" : "form.titulo.editar"));
            dialogo.setHeaderText(null);
            dialogo.getDialogPane().getButtonTypes().addAll(tipoGuardar, tipoCancelar);
            dialogo.getDialogPane().setContent(raiz);
            estilizarDialogo(dialogo);
            dialogo.setResizable(true);
            // Mínimo 480x460: el diálogo no debe poder encogerse hasta
            // desaparecer, pero sigue escalable y maximizable.
            dialogo.setOnShowing(evento -> {
                if (dialogo.getDialogPane().getScene() != null
                        && dialogo.getDialogPane().getScene().getWindow()
                                instanceof javafx.stage.Stage escenario) {
                    escenario.setMinWidth(480);
                    escenario.setMinHeight(460);
                }
            });

            Node botonGuardar = dialogo.getDialogPane().lookupButton(tipoGuardar);
            ayudar(botonGuardar, Idiomas.texto("tooltip.form.guardar"));
            ayudar(dialogo.getDialogPane().lookupButton(tipoCancelar),
                    Idiomas.texto("tooltip.form.cancelar"));
            botonGuardar.addEventFilter(ActionEvent.ACTION, evento -> {
                evento.consume();
                controlador.guardar();
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
            mostrarEnBarra(Idiomas.texto("error.formulario.abrir", excepcion.getMessage()),
                    "texto-error");
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
        aviso.setTitle(Idiomas.texto("dialogo.conflicto.titulo"));
        aviso.setHeaderText(Idiomas.texto("dialogo.conflicto.cabecera"));
        aviso.setContentText(Idiomas.texto("dialogo.conflicto.cuerpo",
                formulario.mensajeProperty().get()));
        ButtonType recargar = new ButtonType(Idiomas.texto("boton.recargar.ficha"));
        ButtonType seguir = new ButtonType(Idiomas.texto("boton.seguir.editando"));
        aviso.getButtonTypes().setAll(recargar, seguir);
        estilizarDialogo(aviso);
        ayudar(aviso.getDialogPane().lookupButton(recargar),
                Idiomas.texto("tooltip.recargar.ficha"));
        ayudar(aviso.getDialogPane().lookupButton(seguir),
                Idiomas.texto("tooltip.seguir.editando"));
        Optional<ButtonType> respuesta = aviso.showAndWait();
        if (respuesta.isPresent() && respuesta.get() == recargar) {
            formulario.recargar();
        }
    }

    /**
     * Asigna un texto de ayuda a un botón de diálogo: al mantener el
     * ratón encima explica qué hace ese botón.
     *
     * @param boton botón del diálogo; si no es un {@link Button}, no hace nada
     * @param texto texto del tooltip
     */
    private static void ayudar(Node boton, String texto) {
        if (boton instanceof Button botonReal) {
            botonReal.setTooltip(new Tooltip(texto));
        }
    }
}
