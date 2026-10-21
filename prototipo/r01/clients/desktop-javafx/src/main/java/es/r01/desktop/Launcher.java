package es.r01.desktop;

import javafx.application.Application;

/**
 * Punto de entrada del fat jar. No extiende Application a propósito: el
 * launcher de la JDK exige el módulo javafx.graphics en el module-path
 * cuando la clase principal extiende Application (JDK-8334909), cosa que
 * no ocurre en un fat jar, que se ejecuta en class-path.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
