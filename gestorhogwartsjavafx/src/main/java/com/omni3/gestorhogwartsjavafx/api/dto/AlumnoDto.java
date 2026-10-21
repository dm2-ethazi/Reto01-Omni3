package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.omni3.gestorhogwartsjavafx.modelo.Alumno;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Representación JSON de un alumno en la API (§3 y §4).
 *
 * <p>Es un {@code record} inmutable y tolerante a campos desconocidos: la
 * API real aún no existe, así que se ignoran los atributos nuevos que
 * pueda añadir. {@code origen} y {@code tipo} viajan como texto para no
 * romperse si el backend envía un valor no previsto.</p>
 *
 * @param dni           DNI normalizado (9 caracteres)
 * @param nombre        nombre y apellidos
 * @param casa          casa; nula en alumnos nuevos
 * @param patronus      patronus; obligatorio en alumnos nuevos
 * @param origen        nombre del {@link Origen}, p. ej. {@code DERBY}
 * @param tipo          nombre del {@link TipoAlumno}, p. ej. {@code HISTORICO}
 * @param version       versión del registro (estado previo para concurrencia)
 * @param actualizadoEn instante ISO-8601 de la última actualización
 * @param activo        {@code false} cuando el alumno está de baja lógica
 * @param patronusUrl   URL de la imagen del patronus (catálogo PATRONUS_IMG)
 * @param imagen        URL o ruta de la imagen del propio alumno
 * @author jairoben
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlumnoDto(
        String dni,
        String nombre,
        String casa,
        String patronus,
        String origen,
        String tipo,
        long version,
        String actualizadoEn,
        boolean activo,
        @JsonProperty("patronus_url") String patronusUrl,
        String imagen) {

    /**
     * Constructor de conveniencia sin las URLs de imagen, para los
     * casos en los que no aportan nada (altas, pruebas, maquetas).
     *
     * @param dni           DNI normalizado
     * @param nombre        nombre y apellidos
     * @param casa          casa; nula en alumnos nuevos
     * @param patronus      patronus; obligatorio en alumnos nuevos
     * @param origen        nombre del {@link Origen}
     * @param tipo          nombre del {@link TipoAlumno}
     * @param version       versión del registro
     * @param actualizadoEn instante ISO-8601 de la última actualización
     * @param activo        {@code false} cuando el alumno está de baja lógica
     */
    public AlumnoDto(String dni, String nombre, String casa, String patronus, String origen,
                     String tipo, long version, String actualizadoEn, boolean activo) {
        this(dni, nombre, casa, patronus, origen, tipo, version, actualizadoEn, activo, null, null);
    }

    /**
     * Convierte un alumno del dominio en su DTO.
     *
     * @param alumno alumno a convertir
     * @return DTO equivalente
     * @throws NullPointerException si {@code alumno} es nulo
     */
    public static AlumnoDto de(Alumno alumno) {
        if (alumno == null) {
            throw new NullPointerException("El alumno es obligatorio");
        }
        return new AlumnoDto(
                alumno.dni(),
                alumno.nombre(),
                alumno.casa(),
                alumno.patronus(),
                alumno.origen().name(),
                alumno.tipo().name(),
                alumno.version(),
                alumno.actualizadoEn().toString(),
                alumno.activo(),
                null,
                null);
    }

    /**
     * Convierte el DTO en un alumno del dominio, aplicando las reglas de
     * tolerancia de la capa API:
     *
     * <ul>
     *   <li>origen desconocido o ausente → {@link Origen#CENTRAL}
     *       (vista unificada, única opción que no atribuye la ficha a una base errónea);</li>
     *   <li>tipo desconocido o ausente → {@link TipoAlumno#HISTORICO};</li>
     *   <li>instante ausente o ilegible → ahora.</li>
     * </ul>
     *
     * @return alumno del dominio listo para la UI
     * @throws IllegalArgumentException si el DNI, el nombre o las
     *         reglas de casa/patronus no son válidos (§4)
     */
    public Alumno aModelo() {
        Origen origenModelo = Origen.desdeTexto(origen).orElse(Origen.CENTRAL);
        TipoAlumno tipoModelo = TipoAlumno.desdeTexto(tipo).orElse(TipoAlumno.HISTORICO);
        Instant momento = parsearInstante(actualizadoEn);
        return new Alumno(dni, nombre, casa, patronus, origenModelo, tipoModelo, version, momento, activo);
    }

    /**
     * Interpreta un instante ISO-8601 de forma tolerante.
     *
     * @param texto texto a interpretar; puede ser {@code null}
     * @return el instante interpretado o el momento actual
     */
    private static Instant parsearInstante(String texto) {
        if (texto == null || texto.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException excepcion) {
            return Instant.now();
        }
    }
}
