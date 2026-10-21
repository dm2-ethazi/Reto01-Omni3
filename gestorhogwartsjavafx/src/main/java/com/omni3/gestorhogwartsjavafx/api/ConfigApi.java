package com.omni3.gestorhogwartsjavafx.api;

import com.omni3.gestorhogwartsjavafx.util.ConfigLoader;
import com.omni3.gestorhogwartsjavafx.util.ExcepcionConfiguracion;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuración de acceso a la API (§3 del contrato): URL base y timeout.
 *
 * <p>La aplicación trabaja siempre contra la API real, así que la
 * configuración ya no transporta ningún modo ni conmutador de datos.</p>
 *
 * <p>La URL base se normaliza al crear la configuración: se le quitan los
 * espacios y las {@code /} finales, se le antepone {@code http://} si no
 * trae esquema (el usuario puede escribir solo {@code 127.0.0.1:8000}) y,
 * si aun así no forma una URI válida, se rechaza con un mensaje en
 * español.</p>
 *
 * <p>La clase es inmutable: las variantes se obtienen con los métodos
 * {@code conX}.</p>
 */
public final class ConfigApi {

    /**
     * URL de la API local de desarrollo (§1 del contrato). Solo la usan
     * {@link #porDefecto()} y las pruebas: en funcionamiento normal la
     * URL real llega del entorno mediante {@link ConfigLoader} y nunca
     * está escrita en el código.
     */
    public static final String URL_POR_DEFECTO = "http://127.0.0.1:8000";

    /** Propiedad de sistema que fija la URL base de la API. */
    public static final String PROP_SISTEMA_URL = ConfigLoader.PROP_SISTEMA_URL;

    /** Variable de entorno que fija la URL base de la API. */
    public static final String VAR_ENTORNO_URL = ConfigLoader.VAR_ENTORNO_URL;

    /** Timeout por defecto de las peticiones. */
    public static final Duration TIMEOUT_POR_DEFECTO = Duration.ofSeconds(10);

    private final String baseUrl;
    private final Duration timeout;

    /**
     * Crea una configuración.
     *
     * @param baseUrl URL base de la API; se normaliza (sin espacios, sin
     *                {@code /} finales y con esquema {@code http://} u
     *                {@code https://})
     * @param timeout timeout de peticiones; positivo
     * @throws IllegalArgumentException si el timeout no es positivo o la
     *                                  URL base queda vacía o no forma una
     *                                  URI válida
     * @throws NullPointerException     si {@code baseUrl} o {@code timeout}
     *                                  son nulos
     */
    public ConfigApi(String baseUrl, Duration timeout) {
        Objects.requireNonNull(baseUrl, "La URL base es obligatoria");
        Objects.requireNonNull(timeout, "El timeout es obligatorio");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(Idiomas.texto("config.timeout"));
        }
        this.baseUrl = normalizar(baseUrl);
        this.timeout = timeout;
    }

    /**
     * Normaliza la URL base: sin espacios ni {@code /} finales, con
     * esquema y validada como URI.
     *
     * @param url URL escrita por el usuario
     * @return URL normalizada
     * @throws IllegalArgumentException si queda vacía o no es una URI válida
     */
    private static String normalizar(String url) {
        String limpia = url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        if (limpia.isEmpty()) {
            throw new IllegalArgumentException(Idiomas.texto("config.url.vacia"));
        }
        String minusculas = limpia.toLowerCase(Locale.ROOT);
        String conEsquema;
        if (minusculas.startsWith("https://")) {
            conEsquema = "https://" + limpia.substring("https://".length());
        } else if (minusculas.startsWith("http://")) {
            conEsquema = "http://" + limpia.substring("http://".length());
        } else {
            conEsquema = "http://" + limpia;
        }
        try {
            URI.create(conEsquema);
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalArgumentException(Idiomas.texto("config.url.invalida", conEsquema));
        }
        return conEsquema;
    }

    /**
     * Construye la configuración de la API local de desarrollo (§3).
     *
     * @return configuración con la URL local y el timeout por defecto
     */
    public static ConfigApi porDefecto() {
        return new ConfigApi(URL_POR_DEFECTO, TIMEOUT_POR_DEFECTO);
    }

    /**
     * Construye la configuración a partir del entorno de ejecución,
     * resuelta por {@link ConfigLoader} sin dejar la URL en el código.
     *
     * <p>La URL base se resuelve con esta prioridad:</p>
     * <ol>
     *   <li>propiedad de sistema {@code -Dgestor.api.url};</li>
     *   <li>variable de entorno {@code GESTOR_API_URL};</li>
     *   <li>clave {@code api.url} de un {@code config.properties} en el
     *       directorio de trabajo o junto al JAR;</li>
     *   <li>la misma clave en un {@code config.properties} del
     *       classpath.</li>
     * </ol>
     *
     * <p>El valor elegido se normaliza igual que en el constructor (sin
     * espacios ni {@code /} finales y con esquema {@code http://}).</p>
     *
     * @return configuración con la URL resuelta del entorno y el timeout
     *         por defecto
     * @throws ExcepcionConfiguracion si ninguna fuente aporta la URL
     * @throws IllegalArgumentException si la URL resuelta no es válida
     */
    public static ConfigApi desdeEntorno() {
        return new ConfigApi(ConfigLoader.cargar(), TIMEOUT_POR_DEFECTO);
    }

    /**
     * URL base de la API sin barra final.
     *
     * @return URL base
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Timeout aplicado a cada petición.
     *
     * @return timeout
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Devuelve una copia con otra URL base.
     *
     * @param nuevaBaseUrl nueva URL base (se normaliza igual que en el
     *                     constructor)
     * @return copia con la URL indicada
     * @throws IllegalArgumentException si la nueva URL no es válida
     */
    public ConfigApi conBaseUrl(String nuevaBaseUrl) {
        return new ConfigApi(nuevaBaseUrl, timeout);
    }

    /**
     * Devuelve otro timeout.
     *
     * @param nuevoTimeout nuevo timeout (positivo)
     * @return copia con el timeout indicado
     */
    public ConfigApi conTimeout(Duration nuevoTimeout) {
        return new ConfigApi(baseUrl, nuevoTimeout);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ConfigApi{baseUrl='%s', timeout=%s}",
                baseUrl, timeout);
    }
}
