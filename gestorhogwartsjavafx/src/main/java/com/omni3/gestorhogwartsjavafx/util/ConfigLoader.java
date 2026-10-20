package com.omni3.gestorhogwartsjavafx.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Resuelve la URL base de la API sin que aparezca en el código ni en
 * ningún fichero versionado.
 *
 * <p>Orden de búsqueda (primero que encuentre, gana):</p>
 * <ol>
 *   <li>propiedad de sistema {@code gestor.api.url};</li>
 *   <li>variable de entorno {@code GESTOR_API_URL};</li>
 *   <li>clave {@code api.url} de un fichero {@code config.properties}
 *       en el directorio de trabajo o junto al JAR;</li>
 *   <li>clave {@code api.url} de un {@code config.properties} del
 *       classpath (uso puntual desde el IDE o pruebas).</li>
 * </ol>
 *
 * <p>El fichero {@code config.properties} se crea copiando
 * {@code config.properties.example}; está en el {@code .gitignore} y se
 * excluye del empaquetado, de modo que la URL real nunca viaja en el
 * JAR ni en el repositorio. Si ninguna fuente aporta la URL se lanza
 * {@link ExcepcionConfiguracion} con un mensaje del bundle que explica
 * cómo crear el fichero.</p>
 *
 * <p>Los mensajes del registro solo indican el <strong>origen</strong>
 * resuelto, nunca el valor: la URL no debe acabar en logs.</p>
 *
 * @author jairoben
 */
public final class ConfigLoader {

    /** Propiedad de sistema que fija la URL base de la API. */
    public static final String PROP_SISTEMA_URL = "gestor.api.url";

    /** Variable de entorno que fija la URL base de la API. */
    public static final String VAR_ENTORNO_URL = "GESTOR_API_URL";

    /** Clave de la URL dentro del fichero de configuración. */
    public static final String CLAVE_URL = "api.url";

    /** Nombre del fichero de configuración local. */
    public static final String FICHERO = "config.properties";

    /** Constructor privado: solo métodos estáticos. */
    private ConfigLoader() {
    }

    /**
     * Resuelve la URL de la API siguiendo el orden de la clase.
     *
     * @return URL tal y como está escrita en la fuente encontrada (sin
     *         espacios de relleno); se normaliza después en
     *         {@code ConfigApi}
     * @throws ExcepcionConfiguracion si ninguna fuente aporta la URL
     */
    public static String cargar() {
        Optional<String> sistema = texto(System.getProperty(PROP_SISTEMA_URL));
        Optional<String> entorno = texto(System.getenv(VAR_ENTORNO_URL));
        List<Path> ficheros = new ArrayList<>();
        ficheros.add(Path.of(FICHERO));
        Path directorio = directorioDelJar();
        if (directorio != null) {
            ficheros.add(directorio.resolve(FICHERO));
        }
        return resolver(sistema, entorno, ficheros, deClasspath());
    }

    /**
     * Núcleo de la resolución, con las fuentes ya extraídas para poder
     * probarlo sin depender del equipo que ejecute las pruebas.
     *
     * @param sistema   valor de la propiedad de sistema, si procede
     * @param entorno   valor de la variable de entorno, si procede
     * @param ficheros  ficheros candidatos, en orden de prioridad
     * @param classpath valor del classpath, si procede
     * @return URL resuelta
     * @throws ExcepcionConfiguracion si todas las fuentes están vacías
     */
    static String resolver(Optional<String> sistema, Optional<String> entorno,
                           List<Path> ficheros, Optional<String> classpath) {
        if (sistema.isPresent()) {
            return registrar(sistema.get(), "propiedad de sistema " + PROP_SISTEMA_URL);
        }
        if (entorno.isPresent()) {
            return registrar(entorno.get(), "variable de entorno " + VAR_ENTORNO_URL);
        }
        for (Path fichero : ficheros) {
            Optional<String> valor = leer(fichero);
            if (valor.isPresent()) {
                return registrar(valor.get(), "fichero " + fichero);
            }
        }
        if (classpath.isPresent()) {
            return registrar(classpath.get(), "classpath " + FICHERO);
        }
        throw new ExcepcionConfiguracion(Idiomas.texto("config.no.encontrado"));
    }

    /**
     * Escribe el origen resuelto en el registro y devuelve la URL.
     * El registro recibe el origen, jamás el valor.
     *
     * @param url    URL resuelta
     * @param origen descripción de la fuente (sin el valor)
     * @return la misma URL, para encadenar
     */
    private static String registrar(String url, String origen) {
        Registro.info("URL de la API resuelta desde " + origen);
        return url;
    }

    /**
     * Lee la clave {@code api.url} de un fichero; si no existe o no
     * trae valor, devuelve vacío para seguir buscando.
     *
     * @param fichero fichero candidato; puede no existir
     * @return valor recortado o vacío
     */
    private static Optional<String> leer(Path fichero) {
        if (fichero == null || !Files.isRegularFile(fichero)) {
            return Optional.empty();
        }
        try (InputStream entrada = Files.newInputStream(fichero)) {
            Properties propiedades = new Properties();
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return texto(propiedades.getProperty(CLAVE_URL));
        } catch (IOException excepcion) {
            Registro.aviso("No se pudo leer " + fichero.getFileName()
                    + ": " + excepcion.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lee la clave {@code api.url} de un {@code config.properties} del
     * classpath.
     *
     * @return valor recortado o vacío si no está en el classpath
     */
    private static Optional<String> deClasspath() {
        try (InputStream entrada = ConfigLoader.class.getResourceAsStream("/" + FICHERO)) {
            if (entrada == null) {
                return Optional.empty();
            }
            Properties propiedades = new Properties();
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return texto(propiedades.getProperty(CLAVE_URL));
        } catch (IOException excepcion) {
            Registro.aviso("No se pudo leer " + FICHERO + " del classpath: "
                    + excepcion.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Directorio que contiene el código que se está ejecutando: la
     * carpeta del JAR en ejecución o {@code target/classes} en el IDE.
     *
     * @return directorio o {@code null} si no es localizable
     */
    private static Path directorioDelJar() {
        try {
            URL ubicacion = ConfigLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (ubicacion == null) {
                return null;
            }
            Path ruta = Path.of(ubicacion.toURI());
            return Files.isRegularFile(ruta) ? ruta.getParent() : ruta;
        } catch (URISyntaxException | RuntimeException excepcion) {
            return null;
        }
    }

    /**
     * Recorta un valor y lo devuelve vacío si no aporta nada.
     *
     * @param valor texto crudo
     * @return valor recortado o vacío
     */
    private static Optional<String> texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(valor.trim());
    }
}
