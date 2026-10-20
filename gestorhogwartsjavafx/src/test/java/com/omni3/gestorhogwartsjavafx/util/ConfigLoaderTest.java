package com.omni3.gestorhogwartsjavafx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del {@link ConfigLoader}: orden de prioridad de las fuentes,
 * lectura del fichero y error controlado cuando no hay configuración.
 *
 * <p>Los casos usan el núcleo {@code resolver(...)} con fuentes
 * inyectadas, así que las pruebas no dependen de si el equipo que las
 * ejecute tiene un {@code config.properties} real.</p>
 */
@DisplayName("ConfigLoader")
class ConfigLoaderTest {

    /** Ruta inexistente para simular ausencia de fichero. */
    private final Path inexistente = Path.of("no-existe-config.properties");

    /** Directorio temporal para el fichero de prueba. */
    @TempDir
    Path directorio;

    @Test
    @DisplayName("La propiedad de sistema tiene prioridad sobre el resto de fuentes")
    void prioridadPropiedadSistema() {
        String url = ConfigLoader.resolver(
                Optional.of("http://propiedad:1111"),
                Optional.of("http://entorno:2222"),
                List.of(inexistente),
                Optional.of("http://classpath:3333"));

        assertEquals("http://propiedad:1111", url);
    }

    @Test
    @DisplayName("La variable de entorno tiene prioridad sobre el fichero y el classpath")
    void prioridadVariableEntorno() {
        String url = ConfigLoader.resolver(
                Optional.empty(),
                Optional.of("http://entorno:2222"),
                List.of(inexistente),
                Optional.of("http://classpath:3333"));

        assertEquals("http://entorno:2222", url);
    }

    @Test
    @DisplayName("Lee la clave api.url del config.properties indicado")
    void leeElFicheroConfigProperties() throws IOException {
        Path fichero = directorio.resolve("config.properties");
        Files.writeString(fichero,
                "# comentario\napi.url=  http://127.0.0.1:9999/  \n",
                StandardCharsets.UTF_8);

        String url = ConfigLoader.resolver(Optional.empty(), Optional.empty(),
                List.of(inexistente, fichero), Optional.empty());

        assertEquals("http://127.0.0.1:9999/", url);
    }

    @Test
    @DisplayName("Un fichero sin la clave api.url se salta y sigue buscando")
    void ficheroSinClaveSigueBuscando() throws IOException {
        Path incompleto = directorio.resolve("incompleto.properties");
        Files.writeString(incompleto, "otra.clave=sin sentido\n",
                StandardCharsets.UTF_8);
        Path completo = directorio.resolve("completo.properties");
        Files.writeString(completo, "api.url=http://de-fichero:4444\n",
                StandardCharsets.UTF_8);

        String url = ConfigLoader.resolver(Optional.empty(), Optional.empty(),
                List.of(incompleto, completo), Optional.empty());

        assertEquals("http://de-fichero:4444", url);
    }

    @Test
    @DisplayName("Si no hay fichero usa la clave api.url del classpath")
    void fallbackClasspath() {
        String url = ConfigLoader.resolver(Optional.empty(), Optional.empty(),
                List.of(inexistente), Optional.of("http://classpath:3333"));

        assertEquals("http://classpath:3333", url);
    }

    @Test
    @DisplayName("Sin ninguna fuente lanza ExcepcionConfiguracion con instrucciones")
    void sinFuentesLanzaExcepcionControlada() {
        ExcepcionConfiguracion error = assertThrows(ExcepcionConfiguracion.class,
                () -> ConfigLoader.resolver(Optional.empty(), Optional.empty(),
                        List.of(inexistente), Optional.empty()));

        assertTrue(error.getMessage().contains("config.properties"),
                "Debe explicar cómo crear el fichero: " + error.getMessage());
        assertTrue(error.getMessage().contains("api.url"),
                "Debe indicar la clave a rellenar: " + error.getMessage());
    }

    @Test
    @DisplayName("cargar() lee la propiedad de sistema gestor.api.url cuando está definida")
    void cargarUsaLaPropiedadDeSistema() {
        System.setProperty(ConfigLoader.PROP_SISTEMA_URL, "http://propiedad:1111");
        try {
            assertEquals("http://propiedad:1111", ConfigLoader.cargar());
        } finally {
            System.clearProperty(ConfigLoader.PROP_SISTEMA_URL);
        }
    }
}
