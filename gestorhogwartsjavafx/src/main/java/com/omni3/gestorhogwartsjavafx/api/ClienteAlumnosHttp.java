package com.omni3.gestorhogwartsjavafx.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación de {@link ClienteAlumnos} contra la API REST real con
 * {@code java.net.http.HttpClient} y Jackson.
 *
 * <p>La API desplegada (rama {@code Api}, FastAPI de
 * {@code proyecto_reto1}) publica únicamente:</p>
 *
 * <ul>
 *   <li>{@code GET    /alumnos} — listado completo;</li>
 *   <li>{@code GET    /alumnos/{dni}} — ficha;</li>
 *   <li>{@code POST   /alumnos} — alta;</li>
 *   <li>{@code PUT    /alumnos/{dni}} — edición;</li>
 *   <li>{@code DELETE /alumnos/{dni}} — baja.</li>
 * </ul>
 *
 * <p>Los campos del backend son {@code id, nombre, apellido, sexo,
 * patronus, casa}, donde {@code casa} identifica la fuente que responde
 * ({@code Hogwarts}=MariaDB central, {@code Gryffindor}=Derby,
 * {@code Hufflepuff}=H2, {@code Slytherin}=HSQLDB,
 * {@code Ravenclaw}=Oracle). Este cliente traduce ese modelo al del
 * dominio ({@code dni, nombre, casa, origen, tipo, version,
 * actualizadoEn, activo}), aplicando además el filtro de
 * origen/búsqueda/paginación en cliente porque la API no lo soporta.</p>
 *
 * <p>Las operaciones sin endpoint publicado (conectores, sincronización
 * y cola) fallan con {@link ExcepcionApi} de código
 * {@code NO_IMPLEMENTADO} y un mensaje claro en español, de modo que la
 * pantalla correspondiente lo muestre en su estado de error.</p>
 *
 * <p>Cada petición lleva una cabecera {@code request-id} generada en el
 * cliente; el mismo identificador se devuelve dentro de la
 * {@link ExcepcionApi} para poder correlacionar los logs. Los errores se
 * leen del envelope documentado {@code {"error":{...}}} o, si la API
 * responde con el formato FastAPI {@code {"detail":"..."}}, del campo
 * {@code detail}.</p>
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

    /**
     * Estado del sistema. La API no publica {@code /health}, así que se
     * comprueba la salud real consultando {@code GET /alumnos}: si
     * responde, la API y su base de datos están operativas.
     *
     * @return salud actual del sistema
     * @throws ExcepcionApi si la consulta falla o la API no responde
     */
    @Override
    public SaludDto salud() {
        List<AlumnoApi> alumnos = leerLista(get("/alumnos"), AlumnoApi.class);
        return new SaludDto(
                "OK",
                "R01",
                "API de proyecto_reto1 operativa · " + alumnos.size() + " alumno(s) consultados.",
                Instant.now().toString());
    }

    /**
     * Consulta unificada: {@code GET /alumnos} y filtrado en cliente.
     *
     * @param origen origen a filtrar; {@code null} o {@link Origen#CENTRAL} para todas las fuentes
     * @param q      texto de búsqueda sobre DNI o nombre; vacío para todo
     * @param limit  máximo de filas a devolver; {@code <= 0} sin límite
     * @param offset número de filas a saltar; {@code <= 0} sin salto
     * @return alumnos que cumplen el filtro
     * @throws ExcepcionApi si la consulta falla
     */
    @Override
    public List<AlumnoDto> listarAlumnos(Origen origen, String q, int limit, int offset) {
        List<AlumnoDto> todos = leerLista(get("/alumnos"), AlumnoApi.class).stream()
                .map(this::aDto)
                .collect(Collectors.toList());
        String texto = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String fuente = origen == null || origen.esCentral() ? null : origen.name();
        return todos.stream()
                .filter(alumno -> fuente == null || fuente.equals(alumno.origen()))
                .filter(alumno -> texto.isEmpty()
                        || alumno.dni().toLowerCase(Locale.ROOT).contains(texto)
                        || alumno.nombre().toLowerCase(Locale.ROOT).contains(texto))
                .skip(offset > 0 ? offset : 0)
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .collect(Collectors.toList());
    }

    /**
     * Ficha de un alumno ({@code GET /alumnos/{dni}}).
     *
     * @param dni DNI del alumno
     * @return la ficha
     * @throws ExcepcionApi con status 404 si no existe
     */
    @Override
    public AlumnoDto obtenerAlumno(String dni) {
        return aDto(leer(get("/alumnos/" + codificar(normalizarId(dni))), AlumnoApi.class));
    }

    /**
     * Alta de alumno ({@code POST /alumnos}) con el mínimo que exige la
     * API: {@code id} (el DNI), {@code nombre} y {@code patronus}.
     *
     * @param alumno datos del alumno; su DNI no puede estar dado de alta
     * @return el alumno dado de alta, con los datos que devuelve la API
     * @throws ExcepcionApi con status 400 si no valida o 409 si el DNI existe
     */
    @Override
    public AlumnoDto altaAlumno(AlumnoDto alumno) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", normalizarId(alumno.dni()));
        datos.put("nombre", alumno.nombre());
        if (alumno.patronus() != null && !alumno.patronus().isBlank()) {
            datos.put("patronus", alumno.patronus());
        }
        return aDto(leer(post("/alumnos", datos, Map.of()), AlumnoApi.class));
    }

    /**
     * Edición de la ficha ({@code PUT /alumnos/{dni}}).
     *
     * <p>La API trabaja con {@code nombre} y {@code apellido} por
     * separado y no admite cambiar la fuente ({@code casa}), así que el
     * cliente recupera la ficha vigente, conserva el apellidos si el
     * texto editado sigue terminando en él (y si no, lo deja en
     * {@code nombre}) y envía también el patronus, que se limpia cuando
     * llega vacío.</p>
     *
     * @param dni    DNI del alumno a editar
     * @param alumno datos nuevos (nombre completo y patronus)
     * @return la ficha actualizada tal y como la devuelve la API
     * @throws ExcepcionApi con status 400 si no valida o 404 si no existe
     */
    @Override
    public AlumnoDto editarAlumno(String dni, AlumnoDto alumno) {
        String id = normalizarId(dni);
        AlumnoApi vigente = leer(get("/alumnos/" + codificar(id)), AlumnoApi.class);
        String nuevo = normalizarTexto(alumno.nombre());
        String apellidos = normalizarTexto(vigente.apellido());
        String nombre;
        String apellido = apellidos;
        String sufijo = " " + apellidos;
        if (!apellidos.isEmpty() && nuevo.length() > sufijo.length()
                && nuevo.regionMatches(true, nuevo.length() - sufijo.length(), sufijo, 0, sufijo.length())) {
            nombre = nuevo.substring(0, nuevo.length() - sufijo.length()).trim();
        } else {
            nombre = nuevo;
            apellido = "";
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", nombre);
        datos.put("apellido", apellido);
        datos.put("patronus", alumno.patronus() == null || alumno.patronus().isBlank()
                ? null : alumno.patronus().trim());
        return aDto(leer(peticion("PUT", "/alumnos/" + codificar(id), escribir(datos), Map.of()),
                AlumnoApi.class));
    }

    /**
     * Baja del alumno ({@code DELETE /alumnos/{dni}}).
     *
     * <p>La API elimina el registro de todas las fuentes que lo
     * contengan y responde {@code 204}; si ninguna lo tiene devuelve
     * {@code 404}.</p>
     *
     * @param dni DNI del alumno a dar de baja
     * @throws ExcepcionApi con status 404 si no existe
     */
    @Override
    public void bajarAlumno(String dni) {
        peticion("DELETE", "/alumnos/" + codificar(normalizarId(dni)), null, Map.of());
    }

    /**
     * Conectores no disponibles: la API actual no publica
     * {@code GET /connectors}.
     *
     * @return nunca devuelve: siempre lanza la excepción
     * @throws ExcepcionApi con status 501 y mensaje explicativo
     */
    @Override
    public List<ConectorDto> listarConectores() {
        throw noDisponible("GET", "/connectors");
    }

    /**
     * Sincronización no disponible: la API actual no publica
     * {@code POST /sync}.
     *
     * @return nunca devuelve: siempre lanza la excepción
     * @throws ExcepcionApi con status 501 y mensaje explicativo
     */
    @Override
    public String sincronizar() {
        throw noDisponible("POST", "/sync");
    }

    /**
     * Cola de refrescos no disponible: la API actual no publica
     * {@code GET /admin/refresh-queue}.
     *
     * @return nunca devuelve: siempre lanza la excepción
     * @throws ExcepcionApi con status 501 y mensaje explicativo
     */
    @Override
    public List<Map<String, Object>> colaRefrescos() {
        throw noDisponible("GET", "/admin/refresh-queue");
    }

    /**
     * Excepción para las operaciones que la API desplegada no publica.
     *
     * @param metodo método HTTP que se habría usado
     * @param ruta   ruta que se habría llamado
     * @return excepción lista para lanzar
     */
    private ExcepcionApi noDisponible(String metodo, String ruta) {
        return new ExcepcionApi(501, "NO_IMPLEMENTADO",
                "La API actual no publica " + metodo + " " + ruta
                        + ": esta operación no está disponible todavía.",
                null);
    }

    /**
     * Traduce un alumno de la API al modelo del dominio.
     *
     * <p>Reglas de tolerancia: si el id o el nombre vienen vacíos se
     * conservan tal cual (la UI mostrará la fila incompleta) y nunca se
     * lanza por validación del dominio, porque los datos ya han pasado
     * por el backend.</p>
     *
     * @param alumno alumno de la API; no nulo
     * @return DTO equivalente para la UI
     */
    private AlumnoDto aDto(AlumnoApi alumno) {
        String id = alumno.id() == null ? "" : normalizarId(alumno.id());
        String nombre = nombreCompleto(alumno.nombre(), alumno.apellido());
        String casa = alumno.casa();
        Origen origen = origenDeCasa(casa);
        String tipo = casa == null || casa.isBlank()
                ? com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno.NUEVO.name()
                : com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno.HISTORICO.name();
        return new AlumnoDto(id, nombre, casa, alumno.patronus(), origen.name(), tipo, 0L, "", true);
    }

    /** Une nombre y apellidos separando con un espacio. */
    private static String nombreCompleto(String nombre, String apellido) {
        String base = nombre == null ? "" : nombre.trim();
        String sobrenombre = apellido == null ? "" : apellido.trim();
        if (sobrenombre.isEmpty()) {
            return base;
        }
        return base.isEmpty() ? sobrenombre : base + " " + sobrenombre;
    }

    /**
     * Traduce la etiqueta de fuente que devuelve la API al origen del
     * dominio. Una fuente desconocida se asimila a la central y una
     * fuente ausente a MariaDB, que es la base de los alumnos nuevos.
     *
     * @param casa etiqueta de la API; puede ser nula
     * @return origen correspondiente
     */
    private static Origen origenDeCasa(String casa) {
        if (casa == null || casa.isBlank()) {
            return Origen.MARIADB;
        }
        String texto = casa.trim().toUpperCase(Locale.ROOT);
        if (texto.contains("HOGWARTS")) {
            return Origen.MARIADB;
        }
        if (texto.contains("GRYFFINDOR")) {
            return Origen.DERBY;
        }
        if (texto.contains("HUFFLEPUFF")) {
            return Origen.H2;
        }
        if (texto.contains("SLYTHERIN")) {
            return Origen.HSQLDB;
        }
        if (texto.contains("RAVENCLAW")) {
            return Origen.ORACLE;
        }
        if (texto.contains("SQLITE")) {
            return Origen.SQLITE;
        }
        return Origen.CENTRAL;
    }

    /** Normaliza un identificador: sin espacios y en mayúsculas. */
    private static String normalizarId(String id) {
        return id == null ? "" : id.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    /** Normaliza un texto libre: sin espacios sobrantes. */
    private static String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }

    /** GET genérico. */
    private Cuerpo get(String ruta) {
        return peticion("GET", ruta, null, Map.of());
    }

    /** POST con cuerpo JSON opcional. */
    private Cuerpo post(String ruta, Object cuerpo, Map<String, String> cabeceras) {
        return peticion("POST", ruta, escribir(cuerpo), cabeceras);
    }

    /**
     * Ejecuta una petición sincrónica y devuelve el cuerpo.
     *
     * @param metodo    método HTTP
     * @param ruta      ruta relativa a la URL base
     * @param json      cuerpo JSON o {@code null}
     * @param cabeceras cabeceras adicionales
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
     * {@link ExcepcionApi}. Se aceptan los dos formatos que puede enviar
     * el backend: el envelope documentado {@code {"error":{...}}} y el
     * FastAPI {@code {"detail":"..."}}.
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
                    ? codigoPorEstado(status) : detalle.codigo();
            String mensaje = detalle.mensaje() == null || detalle.mensaje().isBlank()
                    ? "La API ha devuelto el estado HTTP " + status + "." : detalle.mensaje();
            String completo = detalle.detalle() == null || detalle.detalle().isBlank()
                    ? mensaje : mensaje + " (" + detalle.detalle() + ")";
            return new ExcepcionApi(status, codigo, completo, requestId);
        }
        String detail = detalleFastApi(cuerpo);
        if (detail != null && !detail.isBlank()) {
            return new ExcepcionApi(status, codigoPorEstado(status), detail, requestId);
        }
        String texto = cuerpo == null ? "" : cuerpo.trim().replaceAll("\\s+", " ");
        if (texto.length() > 200) {
            texto = texto.substring(0, 200) + "...";
        }
        String mensaje = texto.isEmpty()
                ? "La API ha devuelto el estado HTTP " + status + "." : texto;
        return new ExcepcionApi(status, codigoPorEstado(status), mensaje, requestId);
    }

    /** Lee el campo {@code detail} de un error FastAPI, si existe. */
    private String detalleFastApi(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> documento = mapper.readValue(cuerpo, Map.class);
            Object detalle = documento.get("detail");
            if (detalle == null) {
                return null;
            }
            return detalle instanceof String texto ? texto : String.valueOf(detalle);
        } catch (JsonProcessingException excepcion) {
            return null;
        }
    }

    /** Asigna un código de negocio legible a un estado HTTP de error. */
    private static String codigoPorEstado(int status) {
        return switch (status) {
            case 400 -> "VALIDACION";
            case 401, 403 -> "AUTORIZACION";
            case 404 -> "NO_ENCONTRADO";
            case 409 -> "CONFLICTO";
            case 500 -> "ERROR_API";
            case 501 -> "NO_IMPLEMENTADO";
            case 503 -> "SIN_CONEXION";
            default -> "HTTP_" + status;
        };
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

    /** Codifica un componente de URL. */
    private String codificar(String valor) {
        String texto = valor == null ? "" : valor;
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    /** Cuerpo de respuesta con su identificador de petición. */
    private record Cuerpo(String texto, String requestId) {
    }

    /**
     * Forma exacta de un alumno en la API de {@code proyecto_reto1}.
     *
     * <p>Se ignoran los campos que la API pueda añadir (p. ej.
     * {@code sexo}) para que una ampliación del modelo no rompa la
     * lectura.</p>
     *
     * @param id       DNI del alumno
     * @param nombre   nombre
     * @param apellido apellidos; puede no venir
     * @param sexo     código de sexo; no se usa en la UI
     * @param patronus patronus
     * @param casa     etiqueta de la fuente que responde, p. ej. {@code Hogwarts}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlumnoApi(
            String id,
            String nombre,
            String apellido,
            String sexo,
            String patronus,
            String casa) {
    }
}
