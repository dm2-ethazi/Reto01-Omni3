package es.r01.desktop.viewmodel;

import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.modelo.EstudiantePayload;
import es.r01.desktop.modelo.EstudianteUpdate;
import es.r01.desktop.modelo.Pagina;
import es.r01.desktop.servicio.CentralService;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel MVVM de la pantalla principal: expone estado observable (lista,
 * filtros, errores) y únicamente acciones; la Vista se limita a enlazar
 * propiedades y a invocar estos métodos. No conoce controles ni escenas.
 */
public class PrincipalViewModel {

    private static final int TAMANO_PAGINA = 200;

    private final CentralService servicio;

    private final ObservableList<Estudiante> filas = FXCollections.observableArrayList();
    private final StringProperty estado = new SimpleStringProperty("Sin conectar");
    private final StringProperty urlApi = new SimpleStringProperty("http://127.0.0.1:8000");
    private final StringProperty filtroNombre = new SimpleStringProperty("");
    private final StringProperty filtroDni = new SimpleStringProperty("");
    private final StringProperty origenSeleccionado = new SimpleStringProperty("central");
    private final BooleanProperty cargando = new SimpleBooleanProperty(false);
    private final List<String> conectores = new ArrayList<>();

    public PrincipalViewModel(CentralService servicio) {
        this.servicio = servicio;
    }

    // --- Estado observable enlazado por la Vista --------------------------

    public ObservableList<Estudiante> filasProperty() {
        return filas;
    }

    public StringProperty estadoProperty() {
        return estado;
    }

    public StringProperty urlApiProperty() {
        return urlApi;
    }

    public StringProperty filtroNombreProperty() {
        return filtroNombre;
    }

    public StringProperty filtroDniProperty() {
        return filtroDni;
    }

    public StringProperty origenSeleccionadoProperty() {
        return origenSeleccionado;
    }

    public BooleanProperty cargandoProperty() {
        return cargando;
    }

    public List<String> conectoresDisponibles() {
        return new ArrayList<>(conectores);
    }

    // --- Acciones (única superficie que puede llamar la Vista) ------------

    /** Conecta con la URL tecleada y refresca la lista de conectores. */
    public void conectar() {
        String url = urlApi.get() == null ? "" : urlApi.get().trim();
        if (url.isEmpty()) {
            estado.set("URL vacía");
            return;
        }
        servicio.conectar(url);
        cargando.set(true);
        estado.set("Conectando a " + url + " ...");
        servicio.conectores(new CentralService.Callback<>() {
            @Override
            public void onResultado(com.fasterxml.jackson.databind.JsonNode cuerpo) {
                List<String> ids = new ArrayList<>();
                cuerpo.path("conectores").fields().forEachRemaining(entrada -> ids.add(entrada.getKey()));
                javafx.application.Platform.runLater(() -> {
                    conectores.clear();
                    conectores.add("central");
                    conectores.addAll(ids);
                    cargando.set(false);
                    estado.set("Conectado a " + servicio.baseUrl());
                    buscar();
                });
            }

            @Override
            public void onError(Exception error) {
                fallo(error);
            }
        });
    }

    /** Carga la página actual aplicando los filtros de la Vista. */
    public void buscar() {
        String origen = origenSeleccionado.get() == null ? "central" : origenSeleccionado.get();
        String nombre = filtroNombre.get();
        String dni = filtroDni.get();
        cargando.set(true);
        estado.set("Consultando " + origen + " ...");

        CentralService.Callback<Pagina> callback = new CentralService.Callback<>() {
            @Override
            public void onResultado(Pagina pagina) {
                javafx.application.Platform.runLater(() -> {
                    filas.setAll(pagina.items);
                    cargando.set(false);
                    estado.set(String.format("%s: %d de %d filas (backend=%s)", origen,
                            pagina.items.size(), pagina.total,
                            pagina.backend != null ? pagina.backend : "-"));
                });
            }

            @Override
            public void onError(Exception error) {
                fallo(error);
            }
        };

        if ("central".equals(origen)) {
            servicio.listarCentral(nombre, dni, 0, TAMANO_PAGINA, callback);
        } else {
            servicio.listarOrigen(origen, nombre, dni, 0, TAMANO_PAGINA, callback);
        }
    }

    public void consultarSalud() {
        cargando.set(true);
        servicio.salud(new CentralService.Callback<>() {
            @Override
            public void onResultado(com.fasterxml.jackson.databind.JsonNode salud) {
                javafx.application.Platform.runLater(() -> {
                    cargando.set(false);
                    estado.set("status=" + salud.path("status").asText()
                            + " | caidos=" + salud.path("caidos").toString());
                });
            }

            @Override
            public void onError(Exception error) {
                fallo(error);
            }
        });
    }

    public void sincronizar() {
        cargando.set(true);
        estado.set("Sincronizando ...");
        servicio.sincronizar(new CentralService.Callback<>() {
            @Override
            public void onResultado(com.fasterxml.jackson.databind.JsonNode resumen) {
                javafx.application.Platform.runLater(() -> {
                    cargando.set(false);
                    estado.set("sync: insertados=" + resumen.path("insertados").asInt()
                            + " actualizados=" + resumen.path("actualizados").asInt()
                            + " errores=" + resumen.path("errores").asInt());
                    buscar();
                });
            }

            @Override
            public void onError(Exception error) {
                fallo(error);
            }
        });
    }

    /** Limpia los filtros y vuelve a la lista completa. */
    public void limpiar() {
        filtroNombre.set("");
        filtroDni.set("");
        buscar();
    }

    // --- Acciones CRUD sobre un origen ------------------------------------

    /** POST /connectors/{conector}/estudiantes - alta (201). */
    public void crear(String conector, EstudiantePayload datos) {
        if ("central".equals(conector)) {
            estado.set("La alta sólo existe en los orígenes, no en la central");
            return;
        }
        cargando.set(true);
        servicio.crear(conector, datos, ok(creado -> {
            estado.set("Creado id=" + creado.getId() + " en " + conector);
            refrescarSiVisible(conector);
        }));
    }

    /** PUT /connectors/{conector}/estudiantes/{rowId} - parcial. */
    public void actualizar(String conector, int rowId, EstudianteUpdate cambios) {
        if ("central".equals(conector)) {
            estado.set("La edición sólo existe en los orígenes, no en la central");
            return;
        }
        cargando.set(true);
        servicio.actualizar(conector, rowId, cambios, ok(actualizado -> {
            estado.set("Actualizado id=" + actualizado.getId() + " en " + conector);
            refrescarSiVisible(conector);
        }));
    }

    /** DELETE /connectors/{conector}/estudiantes/{rowId} - responde 204. */
    public void borrar(String conector, int rowId) {
        if ("central".equals(conector)) {
            estado.set("El borrado sólo existe en los orígenes, no en la central");
            return;
        }
        cargando.set(true);
        servicio.borrar(conector, rowId, ok(nada -> {
            estado.set("Borrado id=" + rowId + " de " + conector);
            refrescarSiVisible(conector);
        }));
    }

    /** Envuelve la respuesta exitosa: apaga el indicador y relanza en FX. */
    private <T> CentralService.Callback<T> ok(java.util.function.Consumer<T> alExito) {
        return new CentralService.Callback<>() {
            @Override
            public void onResultado(T resultado) {
                javafx.application.Platform.runLater(() -> {
                    cargando.set(false);
                    alExito.accept(resultado);
                });
            }

            @Override
            public void onError(Exception error) {
                fallo(error);
            }
        };
    }

    /** Recarga la lista sólo si el origen afectado es el que se está viendo. */
    private void refrescarSiVisible(String conector) {
        String visible = origenSeleccionado.get() == null ? "central" : origenSeleccionado.get();
        if (conector.equals(visible)) {
            buscar();
        }
    }

    public void apagar() {
        servicio.apagar();
    }

    private void fallo(Exception error) {
        javafx.application.Platform.runLater(() -> {
            cargando.set(false);
            estado.set("ERROR: " + error.getMessage());
        });
    }
}
