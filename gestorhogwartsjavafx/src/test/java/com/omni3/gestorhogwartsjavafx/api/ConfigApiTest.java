package com.omni3.gestorhogwartsjavafx.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la configuración de acceso a la API (§3): normalización de
 * la URL base y validación del timeout.
 */
@DisplayName("ConfigApi")
class ConfigApiTest {

    /** Timeout de referencia. */
    private static final Duration TIMEOUT = ConfigApi.TIMEOUT_POR_DEFECTO;

    @Test
    @DisplayName("Se quitan los espacios y las barras finales de la URL")
    void limpiaEspaciosYBarrasFinales() {
        ConfigApi config = new ConfigApi("  http://127.0.0.1:8000///  ", TIMEOUT);

        assertEquals("http://127.0.0.1:8000", config.getBaseUrl());
    }

    @Test
    @DisplayName("Si la URL no trae esquema se le antepone http://")
    void anteponeElEsquemaHttpSiFalta() {
        assertEquals("http://127.0.0.1:8000",
                new ConfigApi("127.0.0.1:8000", TIMEOUT).getBaseUrl());
    }

    @Test
    @DisplayName("Se conserva el esquema https cuando lo trae la URL")
    void conservaElEsquemaHttps() {
        assertEquals("https://api.ejemplo.es",
                new ConfigApi("HTTPS://api.ejemplo.es/", TIMEOUT).getBaseUrl());
    }

    @Test
    @DisplayName("Una URL vacía se rechaza con mensaje en español")
    void rechazaUrlVacia() {
        IllegalArgumentException vacia = assertThrows(IllegalArgumentException.class,
                () -> new ConfigApi("   /  ", TIMEOUT));

        assertTrue(vacia.getMessage().contains("no puede estar vacía"),
                vacia.getMessage());
    }

    @Test
    @DisplayName("Una URL que no forma una URI se rechaza con mensaje en español")
    void rechazaUrlNoParseable() {
        IllegalArgumentException invalida = assertThrows(IllegalArgumentException.class,
                () -> new ConfigApi("http://[::1", TIMEOUT));

        assertTrue(invalida.getMessage().contains("no es válida"), invalida.getMessage());
        assertTrue(invalida.getMessage().contains("http://127.0.0.1:8000"),
                "El mensaje debe dar un ejemplo de URL válida: " + invalida.getMessage());
    }

    @Test
    @DisplayName("Un timeout no positivo se rechaza")
    void rechazaTimeoutNoPositivo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigApi(ConfigApi.URL_POR_DEFECTO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigApi(ConfigApi.URL_POR_DEFECTO, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("conBaseUrl normaliza igual que el constructor")
    void conBaseUrlNormalizaIgual() {
        ConfigApi config = ConfigApi.porDefecto().conBaseUrl("127.0.0.1:9000/");

        assertEquals("http://127.0.0.1:9000", config.getBaseUrl());
        assertEquals(ConfigApi.TIMEOUT_POR_DEFECTO, config.getTimeout());
    }
}
