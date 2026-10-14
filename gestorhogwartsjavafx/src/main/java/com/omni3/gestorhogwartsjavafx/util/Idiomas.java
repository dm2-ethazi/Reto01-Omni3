package com.omni3.gestorhogwartsjavafx.util;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Idioma de la interfaz: castellano, euskera o inglés.
 *
 * <p>La aplicación nace en el idioma del sistema si es uno de los tres
 * admitidos ({@code es}, {@code eu}, {@code en}) y en inglés en caso
 * contrario. La propiedad de sistema {@code gestor.idioma} fija el
 * idioma inicial a mano (la usan las pruebas para ser deterministas).</p>
 *
 * <p>Los textos viven en los bundles {@code i18n/mensajes*.properties}:
 * la base (inglés) es la red de seguridad ante claves ausentes. Los FXML
 * los referencian con {@code %clave} a través de
 * {@link #bundle()} y el código Java con {@link #texto(String)}.</p>
 *
 * <p>Al cambiar de idioma se notifica a los oyentes registrados con
 * {@link #observar(Runnable)}; la aplicación responde recargando la raíz
 * de la escena con los recursos del nuevo idioma.</p>
 *
 * @author jairoben
 */
public final class Idiomas {

    /** Castellano. */
    public static final String CASTELLANO = "es";

    /** Euskera. */
    public static final String EUSKERA = "eu";

    /** Inglés; también es el idioma de reserva. */
    public static final String INGLES = "en";

    /** Propiedad de sistema que fija el idioma inicial. */
    public static final String PROP_SISTEMA = "gestor.idioma";

    /** Base de los bundles de mensajes (sin empaquetar). */
    private static final String BASE = "i18n.mensajes";

    /**
     * Control de carga sin reserva al locale del sistema: sin él,
     * pedir el bundle en inglés saltaría al español del ordenador
     * (Windows en español) en vez de caer a la base inglesa.
     */
    private static final ResourceBundle.Control SIN_RESERVA =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /** Oyentes del cambio de idioma; se ejecutan en el hilo llamante. */
    private static final List<Runnable> OYENTES = new CopyOnWriteArrayList<>();

    /** Idioma activo; {@code null} hasta la primera consulta. */
    private static Locale idioma;

    /** Bundle del idioma activo; {@code null} hasta la primera consulta. */
    private static ResourceBundle bundle;

    private Idiomas() {
    }

    /**
     * Código ISO del idioma activo ({@code es}, {@code eu}, {@code en}).
     *
     * @return el código del idioma vigente
     */
    public static synchronized String codigo() {
        inicializar();
        return idioma.getLanguage();
    }

    /**
     * Símbolo corto del idioma activo en mayúsculas ({@code ES},
     * {@code EU}, {@code EN}), para mostrar en botones y desplegables.
     *
     * @return el símbolo del idioma vigente
     */
    public static synchronized String simbolo() {
        return codigo().toUpperCase(Locale.ROOT);
    }

    /**
     * Locale activo.
     *
     * @return el {@link Locale} del idioma vigente
     */
    public static synchronized Locale idioma() {
        inicializar();
        return idioma;
    }

    /**
     * Bundle de mensajes del idioma activo; los FXML lo reciben con
     * {@code FXMLLoader.setResources} para resolver sus claves
     * {@code %texto}.
     *
     * @return el bundle vigente
     */
    public static synchronized ResourceBundle bundle() {
        inicializar();
        return bundle;
    }

    /**
     * Cambia el idioma de la interfaz y avisa a los oyentes.
     *
     * <p>Si el código no está admitido se usa inglés. Si ya era el
     * idioma activo no se hace nada (ni siquiera se avisa).</p>
     *
     * @param nuevoCodigo código de idioma: {@code es}, {@code eu} o {@code en}
     */
    public static void cambiar(String nuevoCodigo) {
        String elegido = admitido(nuevoCodigo) ? nuevoCodigo : INGLES;
        synchronized (Idiomas.class) {
            if (idioma != null && idioma.getLanguage().equals(elegido)) {
                return;
            }
            idioma = Locale.forLanguageTag(elegido);
            bundle = ResourceBundle.getBundle(BASE, idioma, SIN_RESERVA);
        }
        for (Runnable oyente : OYENTES) {
            oyente.run();
        }
    }

    /**
     * Texto traducido de una clave, sin parámetros.
     *
     * @param clave clave del bundle, p. ej. {@code barra.listo}
     * @return el texto del idioma activo
     * @throws MissingResourceException si la clave no existe ni en la
     *                                  base inglesa
     */
    public static String texto(String clave) {
        return bundle().getString(clave);
    }

    /**
     * Texto traducido de una clave con parámetros
     * ({@link MessageFormat}: {@code {0}}, {@code {1}}…).
     *
     * <p>Para contar filas conviene pasar siempre {@code String} en vez
     * de números, así el formato no depende de la locale.</p>
     *
     * @param clave clave del bundle
     * @param args  parámetros del mensaje
     * @return el texto formateado en el idioma activo
     */
    public static String texto(String clave, Object... args) {
        String plantilla = texto(clave);
        if (args == null || args.length == 0) {
            return plantilla;
        }
        return new MessageFormat(plantilla, idioma()).format(args);
    }

    /**
     * Registra un oyente que se ejecuta cada vez que cambia el idioma.
     *
     * @param alCambiar acción a ejecutar tras el cambio; no nula
     */
    public static void observar(Runnable alCambiar) {
        OYENTES.add(alCambiar);
    }

    /** Inicializa el idioma la primera vez que se consulta. */
    private static synchronized void inicializar() {
        if (bundle != null) {
            return;
        }
        String propiedad = System.getProperty(PROP_SISTEMA);
        String inicial;
        if (admitido(propiedad)) {
            inicial = propiedad;
        } else if (admitido(Locale.getDefault().getLanguage())) {
            inicial = Locale.getDefault().getLanguage();
        } else {
            inicial = INGLES;
        }
        idioma = Locale.forLanguageTag(inicial);
        bundle = ResourceBundle.getBundle(BASE, idioma, SIN_RESERVA);
    }

    /** {@code true} si el código es uno de los tres idiomas admitidos. */
    private static boolean admitido(String codigo) {
        return CASTELLANO.equals(codigo) || EUSKERA.equals(codigo) || INGLES.equals(codigo);
    }
}
