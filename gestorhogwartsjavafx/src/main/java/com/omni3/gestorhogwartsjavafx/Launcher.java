package com.omni3.gestorhogwartsjavafx;

/**
 * Clase lanzadora para el fat JAR.
 *
 * <p>Esta clase no extiende {@link javafx.application.Application}; su único
 * propósito es delegar en {@link App#main(String[])} para que JavaFX se
 * inicie correctamente cuando el JAR se ejecuta con {@code java -jar}.
 * El uso de una clase lanzadora separada evita el error
 * «JavaFX runtime components are missing» que ocurre cuando la clase
 * principal extiende {@code Application} y se ejecuta desde el classpath
 * (en lugar del module path).</p>
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * Punto de entrada para {@code java -jar gestor-hogwarts.jar}.
     *
     * @param args argumentos de línea de comandos (se pasan a {@link App#main})
     */
    public static void main(String[] args) {
        App.main(args);
    }
}