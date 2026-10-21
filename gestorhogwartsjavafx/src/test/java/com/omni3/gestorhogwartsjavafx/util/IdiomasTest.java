package com.omni3.gestorhogwartsjavafx.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas del selector de idioma.
 *
 * <p>Regresión: sin el control sin-reserva, pedir el bundle en inglés
 * se iba al español del ordenador (locale por defecto) en vez de caer
 * a la base inglesa; aquí se comprueba que cada idioma resuelve su
 * bundle y que un idioma no admitido cae al inglés.</p>
 * @author jairoben
 */
@DisplayName("Selector de idioma")
class IdiomasTest {

    @AfterEach
    void restaurarCastellano() {
        Idiomas.cambiar(Idiomas.CASTELLANO);
    }

    @Test
    @DisplayName("el inglés resuelve la base inglesa, no el español del sistema")
    void inglesResuelveElBundleIngles() {
        Idiomas.cambiar(Idiomas.INGLES);
        assertEquals("EN", Idiomas.simbolo());
        assertEquals("Gestor Hogwarts - Desktop Client", Idiomas.texto("app.titulo"));
    }

    @Test
    @DisplayName("el euskera resuelve el bundle vasco")
    void euskeraResuelveElBundleVasco() {
        Idiomas.cambiar(Idiomas.EUSKERA);
        assertEquals("EU", Idiomas.simbolo());
        assertEquals("Gestor Hogwarts - Idazmahaiko bezeroa", Idiomas.texto("app.titulo"));
    }

    @Test
    @DisplayName("el castellano resuelve el bundle español")
    void castellanoResuelveElBundleEspanol() {
        Idiomas.cambiar(Idiomas.CASTELLANO);
        assertEquals("ES", Idiomas.simbolo());
        assertEquals("Gestor Hogwarts - Cliente de escritorio", Idiomas.texto("app.titulo"));
    }

    @Test
    @DisplayName("un idioma no admitido cae al inglés")
    void idiomaNoAdmitidoCaeAlIngles() {
        Idiomas.cambiar("fr");
        assertEquals("EN", Idiomas.simbolo());
        assertEquals("Gestor Hogwarts - Desktop Client", Idiomas.texto("app.titulo"));
    }
}
