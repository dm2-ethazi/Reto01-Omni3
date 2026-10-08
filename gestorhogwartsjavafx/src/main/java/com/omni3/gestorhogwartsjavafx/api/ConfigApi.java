package com.omni3.gestorhogwartsjavafx.api;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuración de acceso a la API (§3 del contrato): URL base y timeout.
 *
 * <p>La aplicación trabaja siempre contra la API real, así que la
 * configuración ya no transporta ningún modo ni conmutador de datos.</p>
 *
 * <p>La clase es inmutable: las variantes se obtienen con los métodos
 * {@code conX}.</p>
 */
public final class ConfigApi {

    /** URL por defecto de la API (§1). */
    public static final String URL_POR_DEFECTO = "http://127.0.0.1:8000";

    /** Timeout por defecto de las peticiones. */
    public static final Duration TIMEOUT_POR_DEFECTO = Duration.ofSeconds(10);

    private final String baseUrl;
    private final Duration timeout;

    /**
     * Crea una configuración.
     *
     * @param baseUrl URL base de la API (se le quitan los {@code /} finales)
     * @param timeout timeout de peticiones; positivo
     * @throws IllegalArgumentException si el timeout no es positivo o la
     *                                  URL base queda vacía
     * @throws NullPointerException     si {@code baseUrl} o {@code timeout}
     *                                  son nulos
     */
    public ConfigApi(String baseUrl, Duration timeout) {
        Objects.requireNonNull(baseUrl, "La URL base es obligatoria");
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
        this.timeout = timeout;
    }

    /**
     * Construye la configuración por defecto (§3).
     *
     * @return configuración con URL y timeout por defecto
     */
    public static ConfigApi porDefecto() {
        return new ConfigApi(URL_POR_DEFECTO, TIMEOUT_POR_DEFECTO);
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
     * @param nuevaBaseUrl nueva URL base
     * @return copia con la URL indicada
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
