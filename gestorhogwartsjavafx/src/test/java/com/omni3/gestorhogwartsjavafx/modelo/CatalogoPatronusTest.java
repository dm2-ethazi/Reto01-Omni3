package com.omni3.gestorhogwartsjavafx.modelo;

import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del catálogo de patronus: búsqueda por nombre y sugerencias
 * del autocompletado (§3). El catálogo es monolingüe (inglés).
 * @author jairoben
 */
@DisplayName("CatalogoPatronus")
class CatalogoPatronusTest {

    private static final List<PatronusDto> ENTRADAS = List.of(
            new PatronusDto("Stag", "http://x/stag.jpg"),
            new PatronusDto("Barn Owl", "http://x/owl.jpg"),
            new PatronusDto("Wolf", "http://x/wolf.jpg"),
            new PatronusDto("Phoenix", "http://x/phoenix.jpg"),
            new PatronusDto("Otter", "http://x/otter.jpg"),
            new PatronusDto("Rabbit", "http://x/rabbit.jpg"),
            new PatronusDto("Grass Snake", "http://x/snake.jpg"),
            new PatronusDto("Balinese", "http://x/cat.jpg"),
            new PatronusDto("Basset Hound", "http://x/basset.jpg"),
            new PatronusDto("Erumpent", "http://x/erumpent.jpg"),
            new PatronusDto("Occamy", "http://x/occamy.jpg"),
            new PatronusDto("Thestral", "http://x/thestral.jpg"),
            new PatronusDto("Dragon", "http://x/dragon.jpg"),
            new PatronusDto("Owl", "http://x/owl2.jpg"),
            new PatronusDto("Cat", "http://x/cat2.jpg"),
            new PatronusDto("Lynx", "http://x/lynx.jpg"));

    private final CatalogoPatronus catalogo = CatalogoPatronus.de(ENTRADAS);

    @Test
    @DisplayName("El catálogo vacío no produce sugerencias ni URLs")
    void catalogoVacio() {
        CatalogoPatronus vacio = CatalogoPatronus.vacio();

        assertEquals(0, vacio.total());
        assertTrue(vacio.sugerencias("Stag").isEmpty());
        assertTrue(vacio.urlDe("Stag").isEmpty());
        assertTrue(vacio.porNombre("Stag").isEmpty());
        assertTrue(vacio.nombres().isEmpty());
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
    @DisplayName("nombres() devuelve todos los nombres en inglés, en orden")
    void nombresEnIngles() {
        List<String> nombres = catalogo.nombres();

        assertTrue(nombres.contains("Stag"));
        assertTrue(nombres.contains("Phoenix"));
        assertEquals(ENTRADAS.size(), nombres.size(),
                "Cada entrada produce un nombre");
        assertEquals("Stag", nombres.get(0), "Se conserva el orden de la API");
    }

    @Test
    @DisplayName("porNombre() encuentra la entrada por nombre exacto")
    void porNombreEncuentra() {
        assertTrue(catalogo.porNombre("Stag").isPresent());
        assertTrue(catalogo.porNombre("Wolf").isPresent());
        assertTrue(catalogo.porNombre("Phoenix").isPresent());
        assertEquals("http://x/stag.jpg", catalogo.urlDe("Stag").orElse(""));
    }

    @Test
    @DisplayName("porNombre() ignora mayúsculas y acentos")
    void porNombreIgnoraMayusculasYAcentos() {
        assertTrue(catalogo.porNombre("STAG").isPresent());
        assertTrue(catalogo.porNombre("  stag  ").isPresent());
        assertTrue(catalogo.porNombre("BARN OWL").isPresent());
        assertTrue(catalogo.porNombre("barn owl").isPresent());
    }

    @Test
    @DisplayName("porNombre() con texto vacío o desconocido devuelve vacío")
    void porNombreSinCoincidencia() {
        assertTrue(catalogo.porNombre(null).isEmpty());
        assertTrue(catalogo.porNombre("").isEmpty());
        assertTrue(catalogo.porNombre("   ").isEmpty());
        assertTrue(catalogo.porNombre("NoExisteEsto").isEmpty());
        assertTrue(catalogo.porNombre("Ciervo").isEmpty(),
                "Los nombres en español ya no forman parte del catálogo");
    }

    @Test
    @DisplayName("porNombre() devuelve la entrada con el nombre canónico")
    void porNombreDevuelveEntrada() {
        PatronusDto stag = catalogo.porNombre("STAG").orElseThrow();

        assertEquals("Stag", stag.nomEng());
    }

    @Test
    @DisplayName("sugerencias() filtra por subcadena")
    void sugerenciasFiltranPorSubcadena() {
        List<String> conC = catalogo.sugerencias("c");

        assertTrue(conC.contains("Occamy"));
        assertTrue(conC.contains("Cat"));
        assertFalse(conC.contains("Wolf"), "Wolf no contiene 'c'");
    }

    @Test
    @DisplayName("sugerencias() con texto vacío no devuelve nada (combo cerrado)")
    void sugerenciasConTextoVacio() {
        assertTrue(catalogo.sugerencias(null).isEmpty());
        assertTrue(catalogo.sugerencias("").isEmpty());
        assertTrue(catalogo.sugerencias("   ").isEmpty());
    }

    @Test
    @DisplayName("sugerencias() ignora mayúsculas del texto escrito")
    void sugerenciasIgnoraMayusculas() {
        assertFalse(catalogo.sugerencias("PHOENIX").isEmpty());
        assertFalse(catalogo.sugerencias("phoenix").isEmpty());
        assertFalse(catalogo.sugerencias("STAG").isEmpty());
    }

    @Test
    @DisplayName("sugerencias() devuelve menos de 15 coincidencias en el catálogo de prueba")
    void sugerenciasMenosDeQuince() {
        List<String> porLetra = catalogo.sugerencias("e");

        assertTrue(porLetra.size() < CatalogoPatronus.MAXIMO_SUGERENCIAS,
                "El test usa un catálogo pequeño; la regla de 15 vive en la vista");
        assertEquals(CatalogoPatronus.MAXIMO_SUGERENCIAS, 15);
    }

    @Test
    @DisplayName("urlDe() devuelve la imagen del patronus")
    void urlDe() {
        assertEquals("http://x/stag.jpg", catalogo.urlDe("Stag").orElse(""));
        assertEquals("http://x/phoenix.jpg", catalogo.urlDe("PHOENIX").orElse(""));
        assertTrue(catalogo.urlDe("NoExiste").isEmpty());
    }
}
