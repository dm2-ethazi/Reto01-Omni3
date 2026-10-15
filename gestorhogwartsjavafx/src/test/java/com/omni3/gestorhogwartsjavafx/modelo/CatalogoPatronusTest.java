package com.omni3.gestorhogwartsjavafx.modelo;

import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del catálogo de patronus: búsqueda por nombre en 3 idiomas,
 * localización y sugerencias del autocompletado (§3).
 */
@DisplayName("CatalogoPatronus")
class CatalogoPatronusTest {

    private static final List<PatronusDto> ENTRADAS = List.of(
            new PatronusDto("Ciervo", "Stag", "Orein", "http://x/stag.jpg"),
            new PatronusDto("Lechuza Común", "Barn Owl", "Ontza", "http://x/owl.jpg"),
            new PatronusDto("Lobo", "Wolf", "Otso", "http://x/wolf.jpg"),
            new PatronusDto("Fénix", "Phoenix", "Fenixe", "http://x/phoenix.jpg"),
            new PatronusDto("Nutria", "Otter", "Igaraba", "http://x/otter.jpg"),
            new PatronusDto("Conejo", "Rabbit", "Untxi", "http://x/rabbit.jpg"),
            new PatronusDto("Serpiente de hierba", "Grass Snake", "Belar suge", "http://x/snake.jpg"),
            new PatronusDto("Gato banyano", "Balinese", "Balinese", "http://x/cat.jpg"),
            new PatronusDto("Basset Hound", "Basset Hound", "Basset Hound", "http://x/basset.jpg"),
            new PatronusDto("Erumpent", "Erumpent", "Erumpent", "http://x/erumpent.jpg"),
            new PatronusDto("Occamy", "Occamy", "Occamy", "http://x/occamy.jpg"),
            new PatronusDto("Thestral", "Thestral", "Thestral", "http://x/thestral.jpg"),
            new PatronusDto("Dragón", "Dragon", "Herensuge", "http://x/dragon.jpg"),
            new PatronusDto("Búho", "Owl", "Hontz", "http://x/owl2.jpg"),
            new PatronusDto("Gato", "Cat", "Katu", "http://x/cat2.jpg"),
            new PatronusDto("Lince", "Lynx", "Lintx", "http://x/lynx.jpg"));

    private final CatalogoPatronus catalogo = CatalogoPatronus.de(ENTRADAS);

    @Test
    @DisplayName("El catálogo vacío no produce sugerencias ni URLs")
    void catalogoVacio() {
        CatalogoPatronus vacio = CatalogoPatronus.vacio();

        assertEquals(0, vacio.total());
        assertTrue(vacio.sugerencias("Ciervo", "es").isEmpty());
        assertTrue(vacio.urlDe("Ciervo").isEmpty());
        assertTrue(vacio.porNombre("Ciervo").isEmpty());
        assertTrue(vacio.nombres("es").isEmpty());
    }

    @Test
    @DisplayName("de(null) y de(List.of()) devuelven el catálogo vacío")
    void deNuloOVacioEsVacio() {
        assertEquals(0, CatalogoPatronus.de(null).total());
        assertEquals(0, CatalogoPatronus.de(List.of()).total());
    }

    @Test
    @DisplayName("total() devuelve el número de entradas")
    void total() {
        assertEquals(ENTRADAS.size(), catalogo.total());
    }

    @Test
    @DisplayName("nombres() devuelve el nombre localizado en cada idioma")
    void nombresLocalizados() {
        List<String> enEspanol = catalogo.nombres("es");
        List<String> enIngles = catalogo.nombres("en");
        List<String> enEuskera = catalogo.nombres("eu");

        assertTrue(enEspanol.contains("Ciervo"));
        assertTrue(enEspanol.contains("Fénix"));
        assertTrue(enIngles.contains("Stag"));
        assertTrue(enIngles.contains("Phoenix"));
        assertTrue(enEuskera.contains("Orein"));
        assertTrue(enEuskera.contains("Fenixe"));
        assertEquals(ENTRADAS.size(), enEspanol.size(),
                "Cada entrada produce un nombre por idioma");
    }

    @Test
    @DisplayName("porNombre() encuentra la entrada en cualquiera de los 3 idiomas")
    void porNombreEnCualquierIdioma() {
        assertTrue(catalogo.porNombre("Ciervo").isPresent());
        assertTrue(catalogo.porNombre("Stag").isPresent());
        assertTrue(catalogo.porNombre("Orein").isPresent());
        assertTrue(catalogo.porNombre("Phoenix").isPresent());
        assertEquals("http://x/stag.jpg", catalogo.urlDe("Stag").orElse(""));
    }

    @Test
    @DisplayName("porNombre() ignora mayúsculas y acentos")
    void porNombreIgnoraMayusculasYAcentos() {
        assertTrue(catalogo.porNombre("CIERVO").isPresent());
        assertTrue(catalogo.porNombre("  ciervo  ").isPresent());
        assertTrue(catalogo.porNombre("fenix").isPresent(), "Fénix sin tilde");
        assertTrue(catalogo.porNombre("FÉNIX").isPresent());
        assertTrue(catalogo.porNombre("barn owl").isPresent());
    }

    @Test
    @DisplayName("porNombre() con texto vacío o desconocido devuelve vacío")
    void porNombreSinCoincidencia() {
        assertTrue(catalogo.porNombre(null).isEmpty());
        assertTrue(catalogo.porNombre("").isEmpty());
        assertTrue(catalogo.porNombre("   ").isEmpty());
        assertTrue(catalogo.porNombre("NoExisteEsto").isEmpty());
    }

    @Test
    @DisplayName("porNombreEn() busca solo en el idioma indicado")
    void porNombreEnUnIdioma() {
        assertTrue(catalogo.porNombreEn("Ciervo", "es").isPresent());
        assertTrue(catalogo.porNombreEn("Stag", "en").isPresent());
        assertTrue(catalogo.porNombreEn("Orein", "eu").isPresent());
        assertTrue(catalogo.porNombreEn("Ciervo", "en").isEmpty(),
                "Ciervo es el nombre en español, no en inglés");
        assertTrue(catalogo.porNombreEn("Stag", "es").isEmpty(),
                "Stag es el nombre en inglés, no en español");
    }

    @Test
    @DisplayName("sugerencias() filtra por subcadena localizada")
    void sugerenciasFiltranPorSubcadena() {
        List<String> enEspanol = catalogo.sugerencias("c", "es");

        assertTrue(enEspanol.contains("Ciervo"));
        assertTrue(enEspanol.contains("Conejo"));
        assertTrue(enEspanol.contains("Occamy"));
        assertFalse(enEspanol.contains("Lobo"), "Lobo no contiene 'c'");
    }

    @Test
    @DisplayName("sugerencias() con texto vacío no devuelve nada (combo cerrado)")
    void sugerenciasConTextoVacio() {
        assertTrue(catalogo.sugerencias(null, "es").isEmpty());
        assertTrue(catalogo.sugerencias("", "es").isEmpty());
        assertTrue(catalogo.sugerencias("   ", "es").isEmpty());
    }

    @Test
    @DisplayName("sugerencias() ignora mayúsculas y acentos del texto escrito")
    void sugerenciasIgnoraMayusculasYAcentos() {
        assertFalse(catalogo.sugerencias("FENIX", "es").isEmpty());
        assertFalse(catalogo.sugerencias("fenix", "es").isEmpty());
        assertFalse(catalogo.sugerencias("STAG", "en").isEmpty());
    }

    @Test
    @DisplayName("sugerencias() devuelve menos de 15 coincidencias en el catálogo de prueba")
    void sugerenciasMenosDeQuince() {
        List<String> todas = catalogo.sugerencias("", "es");
        List<String> porLetra = catalogo.sugerencias("e", "es");

        assertTrue(todas.isEmpty(), "El texto vacío nunca sugiere");
        assertTrue(porLetra.size() < CatalogoPatronus.MAXIMO_SUGERENCIAS,
                "El test usa un catálogo pequeño; la regla de 15 vive en la vista");
        assertEquals(CatalogoPatronus.MAXIMO_SUGERENCIAS, 15);
    }

    @Test
    @DisplayName("nombreEn() localiza la entrada en cada idioma")
    void nombreEn() {
        PatronusDto ciervo = catalogo.porNombre("Ciervo").orElseThrow();

        assertEquals("Ciervo", CatalogoPatronus.nombreEn(ciervo, "es"));
        assertEquals("Stag", CatalogoPatronus.nombreEn(ciervo, "en"));
        assertEquals("Orein", CatalogoPatronus.nombreEn(ciervo, "eu"));
        assertEquals("Stag", CatalogoPatronus.nombreEn(ciervo, "xx"),
                "Un idioma no admitido cae al inglés");
    }

    @Test
    @DisplayName("urlDe() devuelve la imagen del patronus en cualquier idioma")
    void urlDe() {
        assertEquals("http://x/stag.jpg", catalogo.urlDe("Ciervo").orElse(""));
        assertEquals("http://x/stag.jpg", catalogo.urlDe("Stag").orElse(""));
        assertEquals("http://x/phoenix.jpg", catalogo.urlDe("Fenixe").orElse(""));
        assertTrue(catalogo.urlDe("NoExiste").isEmpty());
    }
}
