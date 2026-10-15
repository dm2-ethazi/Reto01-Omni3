package com.omni3.gestorhogwartsjavafx.modelo;

import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
import com.omni3.gestorhogwartsjavafx.util.Idiomas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Catálogo de patronus en memoria, cargado desde {@code GET /patronus}.
 *
 * <p>Permite buscar por nombre en cualquier idioma, devolver el nombre
 * localizado al idioma de la interfaz y filtrar sugerencias para el
 * autocompletado del formulario. La comparación de textos ignora
 * mayúsculas y acentos.</p>
 */
public final class CatalogoPatronus {

    /** Máximo de coincidencias que se ofrecen como sugerencia en el combo. */
    public static final int MAXIMO_SUGERENCIAS = 15;

    /** Catálogo vacío (útil como valor por defecto y en pruebas). */
    private static final CatalogoPatronus VACIO = new CatalogoPatronus(List.of());

    /** Entradas del catálogo, en el orden que devuelve la API. */
    private final List<PatronusDto> entradas;

    private CatalogoPatronus(List<PatronusDto> entradas) {
        this.entradas = List.copyOf(entradas);
    }

    /**
     * Catálogo vacío, sin entradas.
     *
     * @return catálogo sin patronus
     */
    public static CatalogoPatronus vacio() {
        return VACIO;
    }

    /**
     * Catálogo a partir de las entradas de la API.
     *
     * @param entradas entradas del catálogo; {@code null} se trata como vacío
     * @return catálogo con las entradas, sin duplicados de URL y nombre
     */
    public static CatalogoPatronus de(List<PatronusDto> entradas) {
        if (entradas == null || entradas.isEmpty()) {
            return VACIO;
        }
        return new CatalogoPatronus(entradas);
    }

    /**
     * Número de entradas del catálogo.
     *
     * @return total de patronus
     */
    public int total() {
        return entradas.size();
    }

    /**
     * Nombre localizado de una entrada para el idioma dado.
     *
     * @param entrada     entrada del catálogo; no nula
     * @param codigoIdioma código de idioma ({@code es}, {@code eu} o {@code en})
     * @return el nombre en ese idioma, o el español si el código no está admitido
     */
    public static String nombreEn(PatronusDto entrada, String codigoIdioma) {
        Objects.requireNonNull(entrada, "La entrada es obligatoria");
        return switch (codigoIdioma) {
            case Idiomas.CASTELLANO -> entrada.nomEsp();
            case Idiomas.EUSKERA -> entrada.nomEus();
            default -> entrada.nomEng();
        };
    }

    /**
     * Nombres localizados de todo el catálogo para el idioma dado.
     *
     * @param codigoIdioma código de idioma ({@code es}, {@code eu} o {@code en})
     * @return nombres en ese idioma, en orden de catálogo y sin vacíos
     */
    public List<String> nombres(String codigoIdioma) {
        List<String> nombres = new ArrayList<>(entradas.size());
        for (PatronusDto entrada : entradas) {
            String nombre = nombreEn(entrada, codigoIdioma);
            if (nombre != null && !nombre.isBlank()) {
                nombres.add(nombre);
            }
        }
        return List.copyOf(nombres);
    }

    /**
     * Busca una entrada por nombre exacto en el idioma dado, ignorando
     * mayúsculas y acentos.
     *
     * @param texto        nombre a buscar; puede ser {@code null}
     * @param codigoIdioma código de idioma ({@code es}, {@code eu} o {@code en})
     * @return la entrada encontrada, o vacío si no hay coincidencia
     */
    public Optional<PatronusDto> porNombreEn(String texto, String codigoIdioma) {
        String buscado = normalizar(texto);
        if (buscado.isEmpty()) {
            return Optional.empty();
        }
        return entradas.stream()
                .filter(entrada -> buscado.equals(normalizar(nombreEn(entrada, codigoIdioma))))
                .findFirst();
    }

    /**
     * Busca una entrada por nombre exacto en cualquiera de los tres
     * idiomas, ignorando mayúsculas y acentos.
     *
     * @param texto nombre a buscar; puede ser {@code null}
     * @return la entrada encontrada, o vacío si no hay coincidencia
     */
    public Optional<PatronusDto> porNombre(String texto) {
        String buscado = normalizar(texto);
        if (buscado.isEmpty()) {
            return Optional.empty();
        }
        return entradas.stream()
                .filter(entrada -> coincide(entrada, buscado))
                .findFirst();
    }

    /**
     * URL de la imagen de un patronus, buscando por nombre en cualquier
     * idioma.
     *
     * @param nombre nombre del patronus; puede ser {@code null}
     * @return la URL de la imagen, o vacío si el patronus no está en el catálogo
     */
    public Optional<String> urlDe(String nombre) {
        return porNombre(nombre).map(PatronusDto::url);
    }

    /**
     * Sugiere nombres localizados que contienen el texto escrito.
     *
     * <p>El texto vacío no produce sugerencias: el desplegable solo se
     * abre cuando hay entre 1 y {@value #MAXIMO_SUGERENCIAS} coincidencias,
     * para no abrumar al usuario con el catálogo completo.</p>
     *
     * @param texto        texto escrito por el usuario; puede ser {@code null}
     * @param codigoIdioma código de idioma de las sugerencias
     * @return nombres que contienen el texto, en orden de catálogo
     */
    public List<String> sugerencias(String texto, String codigoIdioma) {
        String buscado = normalizar(texto);
        if (buscado.isEmpty()) {
            return List.of();
        }
        List<String> coincidencias = new ArrayList<>();
        for (String nombre : nombres(codigoIdioma)) {
            if (normalizar(nombre).contains(buscado)) {
                coincidencias.add(nombre);
            }
        }
        return List.copyOf(coincidencias);
    }

    /** {@code true} si el nombre localizado de la entrada coincide con el texto. */
    private static boolean coincide(PatronusDto entrada, String buscado) {
        return buscado.equals(normalizar(entrada.nomEsp()))
                || buscado.equals(normalizar(entrada.nomEng()))
                || buscado.equals(normalizar(entrada.nomEus()));
    }

    /** Normaliza un texto: sin acentos, sin mayúsculas y sin espacios sobrantes. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinDiacriticos = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinDiacriticos.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
