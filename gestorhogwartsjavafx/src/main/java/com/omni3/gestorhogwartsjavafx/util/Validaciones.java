package com.omni3.gestorhogwartsjavafx.util;

import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validaciones del dominio (§4 del contrato). Métodos estáticos y sin
 * estado; los mensajes de error se traducen con {@link Idiomas} según
 * el idioma activo de la interfaz.
 *
 * <p>Regla de enmascarado de DNI para logs (§2.6): nunca se muestra un
 * DNI completo. Ver {@link #enmascararDNI(String)}.</p>
 * @author jairoben
 */
public final class Validaciones {

    /** Forma canónica de un DNI: 8 dígitos y una letra mayúscula. */
    private static final Pattern PATRON_DNI = Pattern.compile("^[0-9]{8}[A-Z]$");

    /** Longitud mínima y máxima admitida para el nombre (§4). */
    private static final int NOMBRE_MIN = 2;
    private static final int NOMBRE_MAX = 80;

    /** Longitud mínima y máxima admitida para el patronus (§4). */
    private static final int PATRONUS_MIN = 2;
    private static final int PATRONUS_MAX = 60;

    private Validaciones() {
    }

    /**
     * Normaliza un DNI: sin espacios y en mayúsculas.
     *
     * @param dni DNI de entrada; puede ser {@code null}
     * @return el DNI normalizado, o {@code null} si la entrada era {@code null}
     */
    public static String normalizarDNI(String dni) {
        if (dni == null) {
            return null;
        }
        StringBuilder sinEspacios = new StringBuilder(dni.length());
        for (int i = 0; i < dni.length(); i++) {
            char c = dni.charAt(i);
            if (!Character.isWhitespace(c)) {
                sinEspacios.append(c);
            }
        }
        return sinEspacios.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Comprueba si un DNI (normalizado internamente) es válido.
     *
     * @param dni DNI a comprobar; puede ser {@code null}
     * @return {@code true} si cumple {@code ^[0-9]{8}[A-Z]$}
     */
    public static boolean dniValido(String dni) {
        String normalizado = normalizarDNI(dni);
        return normalizado != null && PATRON_DNI.matcher(normalizado).matches();
    }

    /**
     * Valida un DNI con mensaje de error traducido.
     *
     * @param dni DNI a validar; puede ser {@code null}
     * @return resultado con el motivo del rechazo, si lo hay
     */
    public static ResultadoValidacion validarDNI(String dni) {
        String normalizado = normalizarDNI(dni);
        if (normalizado == null || normalizado.isEmpty()) {
            return ResultadoValidacion.error(Idiomas.texto("valid.dni.obligatorio"));
        }
        if (!PATRON_DNI.matcher(normalizado).matches()) {
            return ResultadoValidacion.error(Idiomas.texto("valid.dni.formato"));
        }
        return ResultadoValidacion.correcto();
    }

    /**
     * Valida el nombre de un alumno: obligatorio y de 2 a 80 caracteres
     * sin contar los espacios de los extremos.
     *
     * @param nombre nombre a validar; puede ser {@code null}
     * @return resultado con el motivo del rechazo, si lo hay
     */
    public static ResultadoValidacion validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return ResultadoValidacion.error(Idiomas.texto("valid.nombre.obligatorio"));
        }
        String limpio = nombre.trim();
        if (limpio.length() < NOMBRE_MIN || limpio.length() > NOMBRE_MAX) {
            return ResultadoValidacion.error(Idiomas.texto("valid.nombre.longitud",
                    NOMBRE_MIN, NOMBRE_MAX));
        }
        return ResultadoValidacion.correcto();
    }

    /**
     * Valida el patronus según el tipo de alumno (§4): obligatorio de
     * 2 a 60 caracteres en los alumnos nuevos y opcional en los
     * históricos (si se informa, también debe medir entre 2 y 60).
     *
     * @param patronus patronus a validar; puede ser {@code null}
     * @param tipo     tipo de alumno; si es {@code null} se exige como en NUEVO
     * @return resultado con el motivo del rechazo, si lo hay
     */
    public static ResultadoValidacion validarPatronus(String patronus, TipoAlumno tipo) {
        String limpio = patronus == null ? "" : patronus.trim();
        boolean vacio = limpio.isEmpty();
        boolean nuevo = tipo != TipoAlumno.HISTORICO;
        if (vacio) {
            if (nuevo) {
                return ResultadoValidacion.error(Idiomas.texto("valid.patronus.obligatorio"));
            }
            return ResultadoValidacion.correcto();
        }
        if (limpio.length() < PATRONUS_MIN || limpio.length() > PATRONUS_MAX) {
            return ResultadoValidacion.error(Idiomas.texto("valid.patronus.longitud",
                    PATRONUS_MIN, PATRONUS_MAX));
        }
        return ResultadoValidacion.correcto();
    }

    /**
     * Valida la casa según el tipo de alumno (§4): obligatoria en los
     * históricos y prohibida en los nuevos (que viven en la central MariaDB).
     *
     * @param casa casa a validar; puede ser {@code null}
     * @param tipo tipo de alumno; si es {@code null} se exige como en NUEVO
     * @return resultado con el motivo del rechazo, si lo hay
     */
    public static ResultadoValidacion validarCasa(String casa, TipoAlumno tipo) {
        String limpio = casa == null ? "" : casa.trim();
        boolean vacio = limpio.isEmpty();
        boolean historico = tipo == TipoAlumno.HISTORICO;
        if (historico && vacio) {
            return ResultadoValidacion.error(Idiomas.texto("valid.casa.obligatoria"));
        }
        if (!historico && !vacio) {
            return ResultadoValidacion.error(Idiomas.texto("valid.casa.prohibida"));
        }
        return ResultadoValidacion.correcto();
    }

    /**
     * Valida todos los campos de un alumno en el orden documentado
     * (DNI, nombre, casa, patronus) y devuelve el primer error.
     *
     * @param dni      DNI del alumno
     * @param nombre   nombre del alumno
     * @param casa     casa; {@code null} en alumnos nuevos
     * @param patronus patronus; obligatorio en alumnos nuevos
     * @param tipo     tipo de alumno
     * @return primer error encontrado, o resultado correcto si todo es válido
     */
    public static ResultadoValidacion validarAlumno(String dni, String nombre, String casa,
                                                    String patronus, TipoAlumno tipo) {
        ResultadoValidacion resultado = validarDNI(dni);
        if (!resultado.esValido()) {
            return resultado;
        }
        resultado = validarNombre(nombre);
        if (!resultado.esValido()) {
            return resultado;
        }
        resultado = validarCasa(casa, tipo);
        if (!resultado.esValido()) {
            return resultado;
        }
        return validarPatronus(patronus, tipo);
    }

    /**
     * Enmascara un DNI para mostrarlo en logs o en la interfaz.
     *
     * <p><strong>Regla exacta:</strong> se parte del DNI normalizado
     * (mayúsculas y sin espacios); se conservan los <strong>2 primeros</strong>
     * y los <strong>3 últimos</strong> caracteres y el bloque central se
     * sustituye por {@code ***}. El resultado mide siempre
     * {@code longitud - 1} caracteres. Ejemplo del contrato: DNI
     * {@code 12345645A} → {@code 12***45A}. Con el DNI de referencia
     * {@code 12345678A} el resultado es {@code 12***78A}. Si el DNI
     * normalizado mide menos de 5 caracteres (o es nulo/vacío) se devuelve
     * {@code ***}, para no arriesgar ninguna cifra identificable.</p>
     *
     * @param dni DNI a enmascarar; puede ser {@code null}
     * @return la versión enmascarada del DNI
     */
    public static String enmascararDNI(String dni) {
        String normalizado = normalizarDNI(dni);
        if (normalizado == null || normalizado.isEmpty()) {
            return "***";
        }
        if (normalizado.length() < 5) {
            return "***";
        }
        return normalizado.substring(0, 2) + "***" + normalizado.substring(normalizado.length() - 3);
    }
}
