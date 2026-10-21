package es.r01.desktop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.modelo.EstudiantePayload;
import es.r01.desktop.modelo.EstudianteUpdate;
import es.r01.desktop.modelo.Pagina;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente HTTP mínimo de la API de R01 (capa de acceso del cliente de
 * escritorio). Todo el JSON lo traduce Jackson.
 */
public class CentralClient {
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public CentralClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** GET /health */
    public JsonNode salud() throws IOException, InterruptedException {
        return get("/health");
    }

    /** GET /connectors */
    public JsonNode conectores() throws IOException, InterruptedException {
        return get("/connectors");
    }

    /** GET /central/estudiantes */
    public Pagina central(String nombre, String dni, int offset, int limit)
            throws IOException, InterruptedException {
        StringBuilder ruta = new StringBuilder("/central/estudiantes?offset=")
                .append(offset).append("&limit=").append(limit);
        if (nombre != null && !nombre.isBlank()) {
            ruta.append("&nombre=").append(codificar(nombre));
        }
        if (dni != null && !dni.isBlank()) {
            ruta.append("&dni=").append(codificar(dni));
        }
        return mapper.readValue(get(ruta.toString()).toString(), Pagina.class);
    }

    /** GET /connectors/{id}/estudiantes */
    public Pagina origen(String conectorId, String nombre, String dni, int offset, int limit)
            throws IOException, InterruptedException {
        StringBuilder ruta = new StringBuilder("/connectors/").append(conectorId)
                .append("/estudiantes?offset=").append(offset).append("&limit=").append(limit);
        if (nombre != null && !nombre.isBlank()) {
            ruta.append("&nombre=").append(codificar(nombre));
        }
        if (dni != null && !dni.isBlank()) {
            ruta.append("&dni=").append(codificar(dni));
        }
        return mapper.readValue(get(ruta.toString()).toString(), Pagina.class);
    }

    /** GET /central/estudiantes/{dni} - devuelve null si responde 404. */
    public Estudiante porDni(String dni) throws IOException, InterruptedException {
        HttpResponse<String> respuesta = enviar(
                HttpRequest.newBuilder(URI.create(baseUrl + "/central/estudiantes/" + codificar(dni)))
                        .timeout(Duration.ofSeconds(15)).GET().build());
        if (respuesta.statusCode() == 404) {
            return null;
        }
        comprobar(respuesta);
        return mapper.readValue(respuesta.body(), Estudiante.class);
    }

    // --- CRUD de un origen: /connectors/{id}/estudiantes -----------------

    /** POST /connectors/{connector_id}/estudiantes - alta, responde 201. */
    public Estudiante crearEstudiante(String conectorId, EstudiantePayload datos)
            throws IOException, InterruptedException {
        return mapper.readValue(enviarCuerpo("POST", rutaOrigen(conectorId), mapper.writeValueAsString(datos)),
                Estudiante.class);
    }

    /** GET /connectors/{connector_id}/estudiantes/{row_id}. */
    public Estudiante obtenerEstudiante(String conectorId, int rowId)
            throws IOException, InterruptedException {
        return mapper.readValue(get(rutaOrigen(conectorId) + "/" + rowId).toString(), Estudiante.class);
    }

    /** PUT /connectors/{connector_id}/estudiantes/{row_id} - parcial. */
    public Estudiante actualizarEstudiante(String conectorId, int rowId, EstudianteUpdate cambios)
            throws IOException, InterruptedException {
        return mapper.readValue(
                enviarCuerpo("PUT", rutaOrigen(conectorId) + "/" + rowId, mapper.writeValueAsString(cambios)),
                Estudiante.class);
    }

    /** DELETE /connectors/{connector_id}/estudiantes/{row_id} - responde 204 sin cuerpo. */
    public void borrarEstudiante(String conectorId, int rowId) throws IOException, InterruptedException {
        enviarCuerpo("DELETE", rutaOrigen(conectorId) + "/" + rowId, null);
    }

    // --- Central ----------------------------------------------------------

    /** GET /central/consistencia - conteos y huellas MariaDB vs SQLite. */
    public JsonNode consistencia() throws IOException, InterruptedException {
        return get("/central/consistencia");
    }

    /** POST /sync - sincroniza todos los conectores. */
    public JsonNode sincronizar() throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(baseUrl + "/sync"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> respuesta = enviar(peticion);
        comprobar(respuesta);
        return mapper.readTree(respuesta.body());
    }

    private JsonNode get(String ruta) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> respuesta = enviar(peticion);
        comprobar(respuesta);
        return mapper.readTree(respuesta.body());
    }

    private HttpResponse<String> enviar(HttpRequest peticion) throws IOException, InterruptedException {
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private static void comprobar(HttpResponse<String> respuesta) throws IOException {
        int estado = respuesta.statusCode();
        if (estado >= 400) {
            throw new IOException("La API ha respondido HTTP " + estado + ": " + respuesta.body());
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /** Ruta base del CRUD de un origen: /connectors/{connector_id}/estudiantes. */
    private String rutaOrigen(String conectorId) {
        return "/connectors/" + codificar(conectorId) + "/estudiantes";
    }

    /**
     * Envía una petición con cuerpo JSON opcional y devuelve el cuerpo de la
     * respuesta (vacío en el 204 del DELETE).
     */
    private String enviarCuerpo(String metodo, String ruta, String cuerpo)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publicador = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .method(metodo, publicador)
                .build();
        HttpResponse<String> respuesta = enviar(peticion);
        comprobar(respuesta);
        return respuesta.body() == null ? "" : respuesta.body();
    }
}
