package com.omni3.gestorhogwartsjavafx.util;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Ajuste de las ventanas a la pantalla en la que se abren.
 *
 * <p>Concentra en un solo sitio las reglas que hacen que la aplicación
 * funcione en cualquier resolución y en cualquier monitor:</p>
 *
 * <ul>
 *   <li>la ventana principal abre con un tamaño cómodo recortado al
 *       espacio visible de la pantalla primaria y centrada;</li>
 *   <li>ningún mínimo de ventana supera el tamaño de la pantalla;</li>
 *   <li>durante toda la sesión, {@link #vigilar(Stage)} encoge la
 *       ventana si crece más que la pantalla que la contiene (p. ej.
 *       al bajar la resolución con la aplicación abierta) y la
 *       recoloca si su barra de título queda fuera de todas las
 *       pantallas, de modo que nunca se pierde una ventana.</li>
 * </ul>
 *
 * <p>Los métodos {@link #tamano(double, double, double)},
 * {@link #limitar(double, double, double)} y
 * {@link #minimoReal(double, double)} son aritmética pura y se prueban
 * sin arrancar la plataforma JavaFX.</p>
 *
 * @author jairoben
 */
public final class Ventanas {

    /** Margen (px) que se reserva al abrir para no rozar los bordes. */
    private static final double MARGEN = 32.0;

    /** Tolerancia (px) para considerar que un borde está en pantalla. */
    private static final double TOLERANCIA = 8.0;

    /** Altura (px) de la franja de título que debe poder alcanzarse. */
    private static final double ALTO_TITULO = 40.0;

    /** Constructor privado: sólo utilidades estáticas. */
    private Ventanas() {
    }

    /**
     * Recorta un tamaño pedido al intervalo permitido por la pantalla.
     *
     * <p>Si la pantalla es más estrecha que el mínimo exigido, manda la
     * pantalla: el resultado nunca supera {@code limite}, porque una
     * ventana que no cabe es un fallo y un mínimo imposible no sirve
     * para nada.</p>
     *
     * @param deseado tamaño deseado en píxeles
     * @param minimo  tamaño mínimo deseado en píxeles
     * @param limite  espacio disponible en píxeles (ancho o alto de la
     *                pantalla visible, ya descontado el margen)
     * @return tamaño final, dentro de {@code (0, limite]}
     */
    public static double tamano(double deseado, double minimo, double limite) {
        return Math.min(Math.max(deseado, Math.min(minimo, limite)), limite);
    }

    /**
     * Limita un valor al intervalo {@code [minimo, maximo]}.
     *
     * @param valor  valor a limitar
     * @param minimo cota inferior
     * @param maximo cota superior
     * @return el valor dentro del intervalo; si las cotas están
     *         invertidas devuelve {@code minimo}
     */
    public static double limitar(double valor, double minimo, double maximo) {
        if (maximo < minimo) {
            return minimo;
        }
        return Math.max(minimo, Math.min(valor, maximo));
    }

    /**
     * Hace realista un mínimo de ventana: nunca mayor que la pantalla.
     *
     * @param minimo mínimo deseado en píxeles
     * @param limite espacio disponible en píxeles
     * @return el mínimo recortado al espacio disponible
     */
    public static double minimoReal(double minimo, double limite) {
        return Math.min(minimo, limite);
    }

    /**
     * Abre un escenario con el tamaño pedido, centrado en la pantalla
     * primaria y ajustado a su espacio visible.
     *
     * <p>En pantallas pequeñas (p. ej. 1024×768 o portátiles con escala
     * alta) la ventana abre reducida en lugar de salirse del escritorio;
     * en pantallas grandes conserva el tamaño cómodo pedido.</p>
     *
     * @param escenario escenario a ajustar; no nulo
     * @param ancho     ancho deseado en píxeles
     * @param alto      alto deseado en píxeles
     * @param minAncho  ancho mínimo deseado en píxeles
     * @param minAlto   alto mínimo deseado en píxeles
     */
    public static void ajustarEscenario(Stage escenario, double ancho, double alto,
            double minAncho, double minAlto) {
        Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
        double anchoFinal = tamano(ancho, minAncho, pantalla.getWidth() - MARGEN);
        double altoFinal = tamano(alto, minAlto, pantalla.getHeight() - MARGEN);
        escenario.setMinWidth(minimoReal(minAncho, pantalla.getWidth()));
        escenario.setMinHeight(minimoReal(minAlto, pantalla.getHeight()));
        escenario.setWidth(anchoFinal);
        escenario.setHeight(altoFinal);
        escenario.setX(pantalla.getMinX() + (pantalla.getWidth() - anchoFinal) / 2);
        escenario.setY(pantalla.getMinY() + (pantalla.getHeight() - altoFinal) / 2);
    }

    /**
     * Fija los mínimos de un escenario sin superar la pantalla en la
     * que está (o en la que se va a abrir).
     *
     * @param escenario escenario al que aplicar los mínimos; no nulo
     * @param minAncho  ancho mínimo deseado en píxeles
     * @param minAlto   alto mínimo deseado en píxeles
     */
    public static void minimos(Stage escenario, double minAncho, double minAlto) {
        Rectangle2D pantalla = pantallaDe(escenario).getVisualBounds();
        escenario.setMinWidth(minimoReal(minAncho, pantalla.getWidth()));
        escenario.setMinHeight(minimoReal(minAlto, pantalla.getHeight()));
    }

    /**
     * Encoge el escenario si su tamaño excede la pantalla que lo
     * contiene, sin moverlo de donde está.
     *
     * @param escenario escenario a comprobar; no nulo
     */
    public static void ajustarSiExcede(Stage escenario) {
        corregirTamano(escenario, pantallaDe(escenario).getVisualBounds());
    }

    /**
     * Mantiene el escenario accesible mientras la aplicación esté
     * abierta, sea cual sea la resolución o el monitor.
     *
     * <p>Reacciona a cualquier cambio de posición o tamaño y a la
     * recepción del foco (el momento en que el usuario vuelve a la
     * ventana tras cambiar la resolución): si la ventana crece más que
     * la pantalla que la contiene se encoge, y si su barra de título
     * queda fuera de todas las pantallas se recoloca centrada en la
     * que tenga más superficie (o en la más cercana). El arrastre
     * normal entre monitores no se ve afectado: basta con que una
     * franja de título siga asomando en alguna pantalla.</p>
     *
     * @param escenario escenario a vigilar; no nulo
     */
    public static void vigilar(Stage escenario) {
        boolean[] ajustando = {false};
        Runnable correccion = () -> corregir(escenario, ajustando);
        escenario.xProperty().addListener((observable, antes, ahora) -> correccion.run());
        escenario.yProperty().addListener((observable, antes, ahora) -> correccion.run());
        escenario.widthProperty().addListener((observable, antes, ahora) -> correccion.run());
        escenario.heightProperty().addListener((observable, antes, ahora) -> correccion.run());
        escenario.focusedProperty().addListener((observable, antes, enfocado) -> {
            if (enfocado) {
                correccion.run();
            }
        });
    }

    /**
     * Corrige tamaño y posición del escenario si se han quedado fuera
     * de la pantalla. Las correcciones se hacen con la bandera
     * {@code ajustando} puesta para que los propios cambios que
     * disparan no se reentren.
     *
     * @param escenario escenario a corregir
     * @param ajustando bandera de reentrada compartida por los oyentes
     */
    private static void corregir(Stage escenario, boolean[] ajustando) {
        if (ajustando[0] || !escenario.isShowing()) {
            return;
        }
        ajustando[0] = true;
        try {
            Rectangle2D pantalla = pantallaDe(escenario).getVisualBounds();
            corregirTamano(escenario, pantalla);
            if (!tituloVisible(escenario)) {
                double ancho = Math.min(escenario.getWidth(), pantalla.getWidth());
                double alto = Math.min(escenario.getHeight(), pantalla.getHeight());
                escenario.setX(pantalla.getMinX() + (pantalla.getWidth() - ancho) / 2);
                escenario.setY(pantalla.getMinY() + (pantalla.getHeight() - alto) / 2);
            }
        } finally {
            ajustando[0] = false;
        }
    }

    /**
     * Encoge el escenario si excede el ancho o el alto de la pantalla
     * indicada.
     *
     * @param escenario escenario a encoger si procede
     * @param pantalla  límites visibles de su pantalla
     */
    private static void corregirTamano(Stage escenario, Rectangle2D pantalla) {
        if (escenario.getWidth() > pantalla.getWidth()) {
            escenario.setWidth(pantalla.getWidth());
        }
        if (escenario.getHeight() > pantalla.getHeight()) {
            escenario.setHeight(pantalla.getHeight());
        }
    }

    /**
     * Indica si la franja de título del escenario asoma en alguna
     * pantalla del escritorio (con {@link #TOLERANCIA} px de holgura),
     * de modo que la ventana pueda seguir arrastrándose o
     * redimensionándose.
     *
     * @param escenario escenario a comprobar
     * @return {@code true} si la barra de título es alcanzable
     */
    private static boolean tituloVisible(Window escenario) {
        double x = escenario.getX();
        double y = escenario.getY();
        double ancho = escenario.getWidth();
        for (Screen pantalla : Screen.getScreens()) {
            Rectangle2D b = pantalla.getVisualBounds();
            boolean horizontal = x < b.getMaxX() - TOLERANCIA
                    && x + ancho > b.getMinX() + TOLERANCIA;
            boolean vertical = y < b.getMaxY() - TOLERANCIA
                    && y + ALTO_TITULO > b.getMinY() + TOLERANCIA;
            if (horizontal && vertical) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pantalla que mejor contiene al escenario: la de mayor superficie
     * visible compartida; si no se solapa con ninguna (p. ej. tras
     * reducir la resolución), la más cercana a su centro; y como último
     * recurso, la primaria.
     *
     * @param ventana ventana a situar
     * @return pantalla elegida; nunca {@code null}
     */
    private static Screen pantallaDe(Window ventana) {
        double x = ventana.getX();
        double y = ventana.getY();
        double ancho = ventana.getWidth();
        double alto = ventana.getHeight();
        double centroX = x + ancho / 2;
        double centroY = y + alto / 2;

        Screen conSolape = null;
        double mejorSolape = 0;
        Screen cercana = null;
        double mejorDistancia = Double.MAX_VALUE;

        for (Screen pantalla : Screen.getScreens()) {
            Rectangle2D b = pantalla.getVisualBounds();
            double solapeX = Math.max(0,
                    Math.min(x + ancho, b.getMaxX()) - Math.max(x, b.getMinX()));
            double solapeY = Math.max(0,
                    Math.min(y + alto, b.getMaxY()) - Math.max(y, b.getMinY()));
            double solape = solapeX * solapeY;
            if (solape > mejorSolape) {
                mejorSolape = solape;
                conSolape = pantalla;
            }
            double desplazamientoX = centroX - (b.getMinX() + b.getWidth() / 2);
            double desplazamientoY = centroY - (b.getMinY() + b.getHeight() / 2);
            double distancia = desplazamientoX * desplazamientoX
                    + desplazamientoY * desplazamientoY;
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                cercana = pantalla;
            }
        }
        if (conSolape != null) {
            return conSolape;
        }
        if (cercana != null) {
            return cercana;
        }
        return Screen.getPrimary();
    }
}
