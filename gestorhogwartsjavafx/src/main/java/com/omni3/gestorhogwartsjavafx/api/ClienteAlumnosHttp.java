package com.omni3.gestorhogwartsjavafx.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.api.dto.ErrorDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación de {@link ClienteAlumnos} contra la API REST real con
 * {@code java.net.http.HttpClient} y Jackson (§3 del contrato).
 *
 * <p>Cada petición lleva una cabecera {@code request-id} generada en el
 * cliente; el mismo identificador se devuelve dentro de la
 * {@link ExcepcionApi} para poder correlacionar los logs. Los errores se
 * mapean desde el envelope
 * {@code {"error":{"codigo","mensaje","detalle"}}}.
 *
 * <p>Esta clase no se prueba con llamadas de red: la API real todavía no
 * existe, por eso la aplicación arranca en modo DEMO.</p>
 */
public class ClienteAlumnosHttp implements ClienteAlumnos {

    /** Configuración (URL base, modo y timeout) de este cliente. */
    private final ConfigApi config;

    /** Cliente HTTP subyacente. */
    private final HttpClient http;

    /** Jackson para leer y escribir JSON. */
    private final ObjectMapper mapper;

    /**
     * Crea el cliente.
     *
     * @param config configuración con la URL base y el timeout; no nula
     * @throws NullPointerException si la configuración es nula
     */
    public ClienteAlumnosHttp(ConfigApi config) {
        this.config = Objects.requireNonNull(config, "La configuración es obligatoria");
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public SaludDto salud() {
        return leer(get("/health"), SaludDto.class);
    }

    @Override
    public List<AlumnoDto> listarAlumnos(Origen origen, String q, int limit, int offset) {
        Map<String, String> parametros = new LinkedHashMap<>();
        if (origen != null && !origen.esCentral()) {
            parametros.put("origen", origen.name());
        }
        if (q != null && !q.isBlank()) {
            parametros.put("q", q.trim());
        }
        if (limit > 0) {
            parametros.put("limit", String.valueOf(limit));
        }
        if (offset > 0) {
            parametros.put("offset", String.valueOf(offset));
        }
        return leerLista(get(con("/students", parametros)), AlumnoDto.class);
    }

    @Override
    public AlumnoDto obtenerAlumno(String dni) {
        return leer(get("/students/" + codificar(dni)), AlumnoDto.class);
    }

    @Override
    public AlumnoDto altaAlumno(AlumnoDto alumno) {
        return leer(post("/students", alumno, Map.of()), AlumnoDto.class);
    }

    @Override
    public AlumnoDto editarAlumno(String dni, AlumnoDto alumno) {
        Map<String, String> cabeceras = Map.of(
                "If-Match", "\"" + alumno.version() + "\"",
                "X-Version-Previa", String.valueOf(alumno.version()));
        return leer(put("/students/" + codificar(dni), alumno, cabeceras), AlumnoDto.class);
    }

    @Override
    public void bajarAlumno(String dni) {
        peticion("DELETE", "/students/" + codificar(dni), null, Map.of());
    }

    @Override
    public List<ConectorDto> listarConectores() {
        return leerLista(get("/connectors"), ConectorDto.class);
    }

    @Override
    public String sincronizar() {
        Cuerpo cuerpo = post("/sync", null, Map.of());
        String texto = cuerpo.texto() == null ? "" : cuerpo.texto().trim();
        return texto.isEmpty() ? "Sincronización iniciada." : texto;
    }

    @Override
    public List<Map<String, Object>> colaRefrescos() {
        Cuerpo cuerpo = get("/admin/refresh-queue");
        try {
            return mapper.readValue(cuerpo.texto(), new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException excepcion) {
            throw new ExcepcionApi(0, "SINTAXIS_JSON",
                    "Respuesta inesperada de la API: " + excepcion.getOriginalMessage(),
                    cuerpo.requestId());
        }
    }

    /** GET genérico. */
    private Cuerpo get(String ruta) {
        return peticion("GET", ruta, null, Map.of());
    }

    /** POST con cuerpo JSON opcional. */
    private Cuerpo post(String ruta, Object cuerpo, Map<String, String> cabeceras) {
        return peticion("POST", ruta, escribir(cuerpo), cabeceras);
    }

    /** PUT con cuerpo JSON y cabeceras adicionales. */
    private Cuerpo put(String ruta, Object cuerpo, Map<String, String> cabeceras) {
        return peticion("PUT", ruta, escribir(cuerpo), cabeceras);
    }

    /**
     * Ejecuta una petición sincrónica y devuelve el cuerpo.
     *
     * @param metodo       método HTTP
     * @param ruta         ruta relativa a la URL base, con su query si procede
     * @param json         cuerpo JSON o {@code null}
     * @param cabeceras    cabeceras adicionales
     * @return cuerpo de la respuesta junto a su request-id
     * @throws ExcepcionApi ante cualquier fallo (HTTP, red o interrupción)
     */
    private Cuerpo peticion(String metodo, String ruta, String json, Map<String, String> cabeceras) {
        String requestId = UUID.randomUUID().toString();
        HttpRequest.Builder constructor = HttpRequest.newBuilder()
                .uri(uri(ruta))
                .timeout(config.getTimeout())
                .header("request-id", requestId)
                .header("Accept", "application/json");
        cabeceras.forEach(constructor::header);
        if (json != null) {
            constructor.header("Content-Type", "application/json");
        }
        constructor.method(metodo, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> respuesta = http.send(constructor.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() >= 400) {
                throw mapearError(respuesta.statusCode(), respuesta.body(), requestId);
            }
            return new Cuerpo(respuesta.body(), requestId);
        } catch (ExcepcionApi excepcion) {
            throw excepcion;
        } catch (IOException excepcion) {
            throw new ExcepcionApi(0, "RED",
                    "No se pudo contactar con la API en " + config.getBaseUrl() + ": " + excepcion.getMessage(),
                    requestId);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
            throw new ExcepcionApi(0, "INTERRUMPIDO", "Petición interrumpida por el llamante.", requestId);
        }
    }

    /**
     * Convierte una respuesta con estado HTTP de error en una
     * {@link ExcepcionApi}, usando el envelope documentado si existe.
     *
     * @param status    estado HTTP
     * @param cuerpo    cuerpo de la respuesta
     * @param requestId identificador de la petición
     * @return la excepción mapeada
     */
    private ExcepcionApi mapearError(int status, String cuerpo, String requestId) {
        Optional<ErrorDto> error = ErrorDto.desdeEnvelope(mapper, cuerpo);
        if (error.isPresent()) {
            ErrorDto detalle = error.get();
            String codigo = detalle.codigo() == null || detalle.codigo().isBlank()
                    ? "HTTP_" + status : detalle.codigo();
            String mensaje = detalle.mensaje() == null || detalle.mensaje().isBlank()
                    ? "La API ha devuelto el estado HTTP " + status + "." : detalle.mensaje();
            String completo = detalle.detalle() == null || detalle.detalle().isBlank()
                    ? mensaje : mensaje + " (" + detalle.detalle() + ")";
            return new ExcepcionApi(status, codigo, completo, requestId);
        }
        String texto = cuerpo == null ? "" : cuerpo.trim().replaceAll("\\s+", " ");
        if (texto.length() > 200) {
            texto = texto.substring(0, 200) + "...";
        }
        String mensaje = texto.isEmpty()
                ? "La API ha devuelto el estado HTTP " + status + "." : texto;
        return new ExcepcionApi(status, "HTTP_" + status, mensaje, requestId);
    }

    /** Serializa un cuerpo JSON; {@code null} produce cuerpo vacío. */
    private String escribir(Object cuerpo) {
        if (cuerpo == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(cuerpo);
        } catch (JsonProcessingException excepcion) {
            throw new ExcepcionApi(0, "SINTAXIS_JSON",
                    "No se pudo serializar la petición: " + excepcion.getOriginalMessage(), null);
        }
    }

    /** Lee una respuesta como objeto. */
    private <T> T leer(Cuerpo cuerpo, Class<T> tipo) {
        try {
            return mapper.readValue(cuerpo.texto(), tipo);
        } catch (JsonProcessingException excepcion) {
            throw new ExcepcionApi(0, "SINTAXIS_JSON",
                    "Respuesta inesperada de la API: " + excepcion.getOriginalMessage(),
                    cuerpo.requestId());
        }
    }

    /** Lee una respuesta como lista. */
    private <T> List<T> leerLista(Cuerpo cuerpo, Class<T> tipo) {
        try {
            return mapper.readValue(cuerpo.texto(),
                    mapper.getTypeFactory().constructCollectionType(List.class, tipo));
        } catch (JsonProcessingException excepcion) {
            throw new ExcepcionApi(0, "SINTAXIS_JSON",
                    "Respuesta inesperada de la API: " + excepcion.getOriginalMessage(),
                    cuerpo.requestId());
        }
    }

    /** URL completa de una ruta relativa. */
    private URI uri(String ruta) {
        String completa = config.getBaseUrl() + (ruta.startsWith("/") ? ruta : "/" + ruta);
        return URI.create(completa);
    }

    /** Concatena una ruta con sus parámetros de consulta codificados. */
    private String con(String ruta, Map<String, String> parametros) {
        if (parametros.isEmpty()) {
            return ruta;
        }
        StringBuilder consulta = new StringBuilder(ruta);
        consulta.append('?');
        boolean primero = true;
        for (Map.Entry<String, String> parametro : parametros.entrySet()) {
            if (!primero) {
                consulta.append('&');
            }
            primero = false;
            consulta.append(codificar(parametro.getKey()))
                    .append('=')
                    .append(codificar(parametro.getValue()));
        }
        return consulta.toString();
    }

    /** Codifica un componente de URL. */
    private String codificar(String valor) {
        String texto = valor == null ? "" : valor;
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    /** Cuerpo de respuesta con su identificador de petición. */
    private record Cuerpo(String texto, String requestId) {
    }
}
