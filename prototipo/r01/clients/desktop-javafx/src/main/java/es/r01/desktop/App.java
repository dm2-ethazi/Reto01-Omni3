package es.r01.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import es.r01.desktop.api.CentralClient;
import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.modelo.Pagina;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Cliente de escritorio del reto R01: consulta la central doble y los
 * orígenes (Derby/HSQLDB/H2/Oracle) a través de la API REST del backend.
 */
public class App extends Application {
    private static final String URL_POR_DEFECTO = "http://127.0.0.1:8000";

    private CentralClient cliente;
    private final TextField campoUrl = new TextField(URL_POR_DEFECTO);
    private final TextField campoNombre = new TextField();
    private final TextField campoDni = new TextField();
    private final ComboBox<String> comboOrigen = new ComboBox<>();
    private final TableView<Estudiante> tabla = new TableView<>();
    private final Label estado = new Label("Sin conectar");

    @Override
    public void start(Stage escenario) {
        cliente = new CentralClient(URL_POR_DEFECTO);
        campoUrl.setPromptText("http://127.0.0.1:8000");
        campoNombre.setPromptText("Nombre (parcial)");
        campoDni.setPromptText("DNI");
        campoDni.setPrefWidth(120);
        comboOrigen.getItems().add("central");
        comboOrigen.setValue("central");

        Button botonRefrescar = new Button("Limpiar");
        botonRefrescar.getStyleClass().addAll("boton", "boton-secundario");
        botonRefrescar.setOnAction(evento -> limpiar());
        Button botonSalud = new Button("Salud");
        botonSalud.getStyleClass().addAll("boton", "boton-secundario");
        botonSalud.setOnAction(evento -> consultarSalud());
        Button botonSincronizar = new Button("Sincronizar");
        botonSincronizar.getStyleClass().addAll("boton", "boton-primario");
        botonSincronizar.setOnAction(evento -> sincronizar());
        Button botonBuscar = new Button("Buscar");
        botonBuscar.getStyleClass().addAll("boton", "boton-primario");
        botonBuscar.setOnAction(evento -> cargar());
        campoUrl.setOnAction(evento -> conectar());
        Button botonConectar = new Button("Conectar");
        botonConectar.getStyleClass().addAll("boton", "boton-primario");
        botonConectar.setOnAction(evento -> conectar());

        Label etiquetaApi = new Label("API:");
        etiquetaApi.getStyleClass().add("etiqueta-campo");
        Label etiquetaOrigen = new Label("Origen:");
        etiquetaOrigen.getStyleClass().add("etiqueta-campo");
        campoUrl.getStyleClass().add("campo");
        campoNombre.getStyleClass().add("campo");
        campoDni.getStyleClass().add("campo");
        comboOrigen.getStyleClass().add("campo-combo");

        HBox filaConexion = new HBox(8,
                etiquetaApi, campoUrl, botonConectar,
                etiquetaOrigen, comboOrigen);
        filaConexion.setAlignment(Pos.CENTER_LEFT);
        HBox filaAcciones = new HBox(8,
                campoNombre, campoDni, botonBuscar,
                botonRefrescar, botonSalud, botonSincronizar);
        filaAcciones.setAlignment(Pos.CENTER_LEFT);

        VBox tarjeta = new VBox(8,
                new Label("CONEXIÓN Y FILTROS"), filaConexion, filaAcciones);
        tarjeta.getStyleClass().add("tarjeta");
        tarjeta.setPadding(new Insets(12, 14, 14, 14));
        ((Label) tarjeta.getChildren().get(0)).getStyleClass().add("etiqueta-seccion");

        VBox cuerpo = new VBox(10, tarjeta, tabla);
        cuerpo.getStyleClass().add("panel-central");
        cuerpo.setPadding(new Insets(14, 16, 10, 16));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        definirColumnas();
        tabla.getStyleClass().add("tabla");

        Label titulo = new Label("R01 Central");
        titulo.getStyleClass().add("cabecera-titulo");
        Label subtitulo = new Label(
                "Integración de orígenes JDBC · Cliente de escritorio · Reto R01");
        subtitulo.getStyleClass().add("cabecera-subtitulo");
        VBox cabecera = new VBox(titulo, subtitulo);
        cabecera.getStyleClass().add("cabecera");
        cabecera.setPadding(new Insets(14, 18, 12, 18));

        estado.getStyleClass().add("estado-texto");
        HBox barraEstado = new HBox(estado);
        barraEstado.getStyleClass().add("barra-estado");
        barraEstado.setPadding(new Insets(8, 16, 8, 16));

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("raiz");
        raiz.setTop(cabecera);
        raiz.setCenter(cuerpo);
        raiz.setBottom(barraEstado);

        Scene escena = new Scene(raiz, 1080, 620);
        URL hoja = getClass().getResource("/es/r01/desktop/vista/estilos.css");
        if (hoja != null) {
            escena.getStylesheets().add(hoja.toExternalForm());
        }

        escenario.setTitle("R01 - Clientes de la central (JavaFX)");
        escenario.setScene(escena);
        escenario.show();

        conectar();
    }

    /** Borra los filtros de búsqueda sin tocar la conexión. */
    private void limpiar() {
        campoNombre.clear();
        campoDni.clear();
        cargar();
    }

    private void definirColumnas() {
        añadirColumna("Origen", "origen");
        añadirColumna("Id", "id");
        añadirColumna("DNI", "dni");
        añadirColumna("Nombre", "nombre");
        añadirColumna("Casa", "casa");
        añadirColumna("Patronus", "patronus");
    }

    private void añadirColumna(String titulo, String campo) {
        TableColumn<Estudiante, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(new PropertyValueFactory<>(campo));
        tabla.getColumns().add(columna);
    }

    private void conectar() {
        cliente = new CentralClient(campoUrl.getText().trim());
        enHilo(() -> {
            JsonNode conectores = cliente.conectores();
            List<String> ids = new ArrayList<>();
            conectores.path("conectores").fields().forEachRemaining(ent -> ids.add(ent.getKey()));
            Platform.runLater(() -> {
                String actual = comboOrigen.getValue();
                comboOrigen.getItems().setAll(concatenar("central", ids));
                comboOrigen.setValue(actual != null && (actual.equals("central") || ids.contains(actual))
                        ? actual : "central");
            });
            return null;
        }, "Conectado a " + cliente.baseUrl());
        cargar();
    }

    private static List<String> concatenar(String primero, List<String> resto) {
        List<String> todos = new ArrayList<>();
        todos.add(primero);
        todos.addAll(resto);
        return todos;
    }

    private void cargar() {
        String seleccion = comboOrigen.getValue() == null ? "central" : comboOrigen.getValue();
        String nombre = campoNombre.getText();
        String dni = campoDni.getText();
        enHilo(() -> {
            Pagina pagina = seleccion.equals("central")
                    ? cliente.central(nombre, dni, 0, 200)
                    : cliente.origen(seleccion, nombre, dni, 0, 200);
            Platform.runLater(() -> {
                tabla.getItems().setAll(pagina.items);
                estado.setText(String.format(
                        "%s: %d de %d filas (backend=%s)",
                        seleccion, pagina.items.size(), pagina.total,
                        pagina.backend != null ? pagina.backend : "-"));
            });
            return null;
        }, null);
    }

    private void consultarSalud() {
        enHilo(() -> {
            JsonNode salud = cliente.salud();
            Platform.runLater(() -> estado.setText(
                    "status=" + salud.path("status").asText()
                            + " | caidos=" + salud.path("caidos").toString()));
            return null;
        }, null);
    }

    private void sincronizar() {
        enHilo(() -> {
            JsonNode resumen = cliente.sincronizar();
            Platform.runLater(() -> estado.setText(
                    "sync: insertados=" + resumen.path("insertados").asInt()
                            + " actualizados=" + resumen.path("actualizados").asInt()
                            + " errores=" + resumen.path("errores").asInt()));
            return null;
        }, null);
    }

    private void enHilo(java.util.concurrent.Callable<Object> tarea, String mensajeOk) {
        Thread hilo = new Thread(() -> {
            try {
                tarea.call();
                if (mensajeOk != null) {
                    Platform.runLater(() -> estado.setText(mensajeOk));
                }
            } catch (Exception exc) {
                Platform.runLater(() -> estado.setText("ERROR: " + exc.getMessage()));
            }
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
