package es.r01.desktop.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import es.r01.desktop.api.CentralClient;
import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.modelo.EstudiantePayload;
import es.r01.desktop.modelo.EstudianteUpdate;
import es.r01.desktop.modelo.Pagina;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Capa de servicios (Model) del patrón MVVM: envuelve {@link CentralClient}
 * y ejecuta cada llamada en un hilo de fondo, devolviendo el resultado por
 * medio de un callback para que el ViewModel nunca bloquee el hilo de FX.
 */
public class CentralService {

    /** Callback genérico de resultado: éxito o error legible. */
    public interface Callback<T> {
        void onResultado(T resultado);

        void onError(Exception error);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread hilo = new Thread(runnable, "r01-api");
        hilo.setDaemon(true);
        return hilo;
    });

    private volatile CentralClient cliente = new CentralClient("http://127.0.0.1:8000");

    public String baseUrl() {
        return cliente.baseUrl();
    }

    /** Reapunta el cliente a otra URL de la API. */
    public void conectar(String baseUrl) {
        this.cliente = new CentralClient(baseUrl);
    }

    public void apagar() {
        executor.shutdownNow();
    }

    public void salud(Callback<JsonNode> callback) {
        ejecutar(() -> cliente.salud(), callback);
    }

    public void conectores(Callback<JsonNode> callback) {
        ejecutar(() -> cliente.conectores(), callback);
    }

    public void sincronizar(Callback<JsonNode> callback) {
        ejecutar(() -> cliente.sincronizar(), callback);
    }

    public void listarCentral(String nombre, String dni, int offset, int limit, Callback<Pagina> callback) {
        ejecutar(() -> cliente.central(nombre, dni, offset, limit), callback);
    }

    public void listarOrigen(String conectorId, String nombre, String dni, int offset, int limit,
            Callback<Pagina> callback) {
        ejecutar(() -> cliente.origen(conectorId, nombre, dni, offset, limit), callback);
    }

    /** 404 se traduce en {@code null} para que el ViewModel lo muestre. */
    public void porDni(String dni, Callback<Estudiante> callback) {
        ejecutar(() -> cliente.porDni(dni), callback);
    }

    // --- CRUD de un origen ------------------------------------------------

    public void crear(String conectorId, EstudiantePayload datos, Callback<Estudiante> callback) {
        ejecutar(() -> cliente.crearEstudiante(conectorId, datos), callback);
    }

    public void obtener(String conectorId, int rowId, Callback<Estudiante> callback) {
        ejecutar(() -> cliente.obtenerEstudiante(conectorId, rowId), callback);
    }

    public void actualizar(String conectorId, int rowId, EstudianteUpdate cambios,
            Callback<Estudiante> callback) {
        ejecutar(() -> cliente.actualizarEstudiante(conectorId, rowId, cambios), callback);
    }

    /** DELETE: el 204 no trae cuerpo, el callback recibe {@code null}. */
    public void borrar(String conectorId, int rowId, Callback<Void> callback) {
        ejecutar(() -> {
            cliente.borrarEstudiante(conectorId, rowId);
            return null;
        }, callback);
    }

    public void consistencia(Callback<JsonNode> callback) {
        ejecutar(() -> cliente.consistencia(), callback);
    }

    private <T> void ejecutar(Operacion<T> operacion, Callback<T> callback) {
        executor.execute(() -> {
            try {
                callback.onResultado(operacion.call());
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    /** Operación bloqueante de la API, ejecutada siempre fuera del hilo de FX. */
    private interface Operacion<T> {
        T call() throws IOException, InterruptedException;
    }
}
