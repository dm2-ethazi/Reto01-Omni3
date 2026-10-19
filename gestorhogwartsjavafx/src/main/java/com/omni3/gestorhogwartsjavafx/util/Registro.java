package com.omni3.gestorhogwartsjavafx.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Registro de eventos de la aplicación (arranques, avisos y errores).
 *
 * <p>Cada evento se escribe en {@code gestor-hogwarts.log}, en el
 * directorio de trabajo, y se refleja a su vez en {@code System.err}
 * para que también sea visible en la consola de lanzamiento. Los
 * ficheros de registro se ignoran en git ({@code *.log}).</p>
 *
 * <p>El registro <strong>nunca</strong> debe recibir URLs de la API ni
 * credenciales: los mensajes solo describen el origen del problema, no
 * los valores de configuración. Si el fichero no puede escribirse
 * (por ejemplo, directorio de solo lectura), el fallo se anota en
 * {@code System.err} y la aplicación continúa sin interrumpirse.</p>
 *
 * @author jairoben
 */
public final class Registro {

    /** Fichero de registro, relativo al directorio de trabajo. */
    private static final Path FICHERO = Path.of("gestor-hogwarts.log");

    /** Constructor privado: solo métodos estáticos. */
    private Registro() {
    }

    /**
     * Registra una información de arranque o de funcionamiento.
     *
     * @param mensaje texto del evento; sin URL ni credenciales
     */
    public static void info(String mensaje) {
        escribir("INFO ", mensaje, null);
    }

    /**
     * Registra un aviso recuperable.
     *
     * @param mensaje texto del aviso; sin URL ni credenciales
     */
    public static void aviso(String mensaje) {
        escribir("AVISO", mensaje, null);
    }

    /**
     * Registra un error con su causa.
     *
     * @param mensaje texto del error; sin URL ni credenciales
     * @param causa   excepción asociada; puede ser {@code null}
     */
    public static void error(String mensaje, Throwable causa) {
        escribir("ERROR", mensaje, causa);
    }

    /**
     * Escribe el evento en {@code System.err} y en el fichero de
     * registro. El método es {@code synchronized} para que las líneas
     * de hilos distintos no se intercalen.
     *
     * @param nivel  etiqueta del nivel (INFO, AVISO, ERROR)
     * @param mensaje texto del evento
     * @param causa  excepción asociada o {@code null}
     */
    private static synchronized void escribir(String nivel, String mensaje, Throwable causa) {
        StringBuilder bloque = new StringBuilder()
                .append(Instant.now())
                .append(' ')
                .append(nivel)
                .append(' ')
                .append(mensaje)
                .append(System.lineSeparator());
        if (causa != null) {
            StringWriter traza = new StringWriter();
            causa.printStackTrace(new PrintWriter(traza));
            bloque.append(traza);
        }
        String texto = bloque.toString();
        System.err.print(texto);
        try {
            Files.writeString(FICHERO, texto, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException excepcion) {
            System.err.println("[aviso] No se pudo escribir el registro "
                    + FICHERO + ": " + excepcion.getMessage());
        }
    }
}
