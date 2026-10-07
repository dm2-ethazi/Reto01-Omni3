package com.omni3.gestorhogwartsjavafx.api;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuración de acceso a la API (§3 del contrato): URL base, modo y
 * timeout.
 *
 * <p>El modo se detecta con la prioridad <em>system property</em>
 * {@code -Domni3.modo} &gt; variable de entorno {@code OMNI3_MODO} &gt;
 * {@link Modo#DEMO}. Cualquier valor no reconocido cae a DEMO para no
 * arrancar contra una API inexistente.</p>
 *
 * <p>La clase es inmutable: las variantes se obtienen con los métodos
 * {@code conX}.</p>
 */
public final class ConfigApi {

    /** URL por defecto de la API FastAPI (§1). */
    public static final String URL_POR_DEFECTO = "http://127.0.0.1:8000";

    /** Timeout por defecto de las peticiones. */
    public static final Duration TIMEOUT_POR_DEFECTO = Duration.ofSeconds(10);

    /** Clave de system property que fija el modo. */
    public static final String CLAVE_MODO = "omni3.modo";

    /** Variable de entorno que fija el modo. */
    public static final String ENTORNO_MODO = "OMNI3_MODO";

    private final String baseUrl;
    private final Modo modo;
    private final Duration timeout;

    /**
     * Crea una configuración.
     *
     * @param baseUrl URL base de la API (se le quitan los {@code /} finales)
     * @param modo    modo de acceso; no nulo
     * @param timeout timeout de peticiones; positivo
     * @throws IllegalArgumentException si el timeout no es positivo
     * @throws NullPointerException     si {@code baseUrl} o {@code modo} son nulos
     */
    public ConfigApi(String baseUrl, Modo modo, Duration timeout) {
        Objects.requireNonNull(baseUrl, "La URL base es obligatoria");
        Objects.requireNonNull(modo, "El modo es obligatorio");
        Objects.requireNonNull(timeout, "El timeout es obligatorio");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("El timeout debe ser positivo");
        }
        String limpia = baseUrl.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        if (limpia.isEmpty()) {
            throw new IllegalArgumentException("La URL base no puede estar vacía");
        }
        this.baseUrl = limpia;
        this.modo = modo;
        this.timeout = timeout;
    }

    /**
     * Construye la configuración por defecto detectando el modo en la
     * JVM (§3): {@code -Domni3.modo} &gt; {@code OMNI3_MODO} &gt; DEMO.
     *
     * @return configuración con URL y timeout por defecto
     */
    public static ConfigApi porDefecto() {
        return new ConfigApi(URL_POR_DEFECTO, modoDetectado(), TIMEOUT_POR_DEFECTO);
    }

    /**
     * Detecta el modo actual de la JVM.
     *
     * <p>Orden de prioridad: system property {@code omni3.modo},
     * variable de entorno {@code OMNI3_MODO} y, en su defecto,
     * {@link Modo#DEMO}.</p>
     *
     * @return modo detectado; nunca {@code null}
     */
    public static Modo modoDetectado() {
        String texto = System.getProperty(CLAVE_MODO);
        if (texto == null || texto.isBlank()) {
            texto = System.getenv(ENTORNO_MODO);
        }
        return Modo.desdeTexto(texto).orElse(Modo.DEMO);
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
     * Modo de acceso configurado.
     *
     * @return modo actual
     */
    public Modo getModo() {
        return modo;
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
     * Indica si la configuración trabaja contra datos de demostración.
     *
     * @return {@code true} si el modo es {@link Modo#DEMO}
     */
    public boolean esDemo() {
        return modo == Modo.DEMO;
    }

    /**
     * Devuelve una copia con otra URL base.
     *
     * @param nuevaBaseUrl nueva URL base
     * @return copia con la URL indicada
     */
    public ConfigApi conBaseUrl(String nuevaBaseUrl) {
        return new ConfigApi(nuevaBaseUrl, modo, timeout);
    }

    /**
     * Devuelve una copia con otro modo.
     *
     * @param nuevoModo nuevo modo de acceso
     * @return copia con el modo indicado
     */
    public ConfigApi conModo(Modo nuevoModo) {
        return new ConfigApi(baseUrl, nuevoModo, timeout);
    }

    /**
     * Devuelve otro timeout.
     *
     * @param nuevoTimeout nuevo timeout (positivo)
     * @return copia con el timeout indicado
     */
    public ConfigApi conTimeout(Duration nuevoTimeout) {
        return new ConfigApi(baseUrl, modo, nuevoTimeout);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ConfigApi{baseUrl='%s', modo=%s, timeout=%s}",
                baseUrl, modo, timeout);
    }
}
