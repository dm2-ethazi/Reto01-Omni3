package com.omni3.gestorhogwartsjavafx.util;

import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las validaciones del dominio (§4 del contrato) y del
 * enmascarado de DNI para logs (§2.6).
 */
@DisplayName("Validaciones del dominio")
class ValidacionesTest {

    // ---------------------------------------------------------------- DNI

    @Test
    @DisplayName("normalizarDNI deja mayúsculas y sin espacios")
    void normalizaDniAMayusculasYSinEspacios() {
        assertEquals("12345678A", Validaciones.normalizarDNI("  123 45678 a "));
        assertEquals("12345678A", Validaciones.normalizarDNI("12345678a"));
        assertEquals("12345678A", Validaciones.normalizarDNI("\t12345678A\n"));
    }

    @Test
    @DisplayName("normalizarDNI devuelve null si la entrada es null")
    void normalizaDniNulo() {
        assertNull(Validaciones.normalizarDNI(null));
    }

    @Test
    @DisplayName("dniValido acepta 8 dígitos y una letra en mayúscula")
    void dniValidoAceptaElFormatoEstandar() {
        assertTrue(Validaciones.dniValido("12345678A"));
        assertTrue(Validaciones.dniValido(" 12345678a "));
        assertTrue(Validaciones.validarDNI("00000000Z").esValido());
    }

    @Test
    @DisplayName("dniValido rechaza longitudes distintas de 9")
    void dniValidoRechazaLongitudIncorrecta() {
        assertFalse(Validaciones.dniValido("1234567"));
        assertFalse(Validaciones.dniValido("123456789A"));
        assertFalse(Validaciones.validarDNI("12345678").esValido());
    }

    @Test
    @DisplayName("dniValido rechaza letras donde deben ir dígitos y viceversa")
    void dniValidoRechazaCombinacionesInvalidas() {
        assertFalse(Validaciones.dniValido("ABCDEFGHA"));
        assertFalse(Validaciones.dniValido("1234567AB"));
        assertFalse(Validaciones.dniValido("1234567aA"));
    }

    @Test
    @DisplayName("dniValido rechaza vacíos y nulos con mensaje en español")
    void dniValidoRechazaVaciosYNulos() {
        assertFalse(Validaciones.dniValido(null));
        assertFalse(Validaciones.dniValido(""));
        assertFalse(Validaciones.dniValido("   "));
        Optional<String> error = Validaciones.validarDNI("").mensajeError();
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("obligatorio"), "El mensaje debe indicar que el DNI es obligatorio");
    }

    @Test
    @DisplayName("validarDNI explica la forma correcta cuando el DNI es inválido")
    void validarDniExplicaElFormatoEsperado() {
        Optional<String> error = Validaciones.validarDNI("1234567").mensajeError();
        assertTrue(error.isPresent());
        assertTrue(error.get().contains("9 caracteres"), "El mensaje debe citar la longitud exigida");
        assertTrue(error.get().contains("12345678A"), "El mensaje debe mostrar un ejemplo");
    }

    // -------------------------------------------------------------- nombre

    @Test
    @DisplayName("validarNombre acepta entre 2 y 80 caracteres")
    void validarNombreAceptaLongitudesPermitidas() {
        assertTrue(Validaciones.validarNombre("Ana").esValido());
        assertTrue(Validaciones.validarNombre("  Hermione Granger  ").esValido());
        assertTrue(Validaciones.validarNombre("A".repeat(80)).esValido());
    }

    @Test
    @DisplayName("validarNombre rechaza vacío, corto y largo")
    void validarNombreRechazaValoresInvalidos() {
        assertFalse(Validaciones.validarNombre(null).esValido());
        assertFalse(Validaciones.validarNombre("").esValido());
        assertFalse(Validaciones.validarNombre("   ").esValido());
        assertFalse(Validaciones.validarNombre("A").esValido());
        assertFalse(Validaciones.validarNombre("A".repeat(81)).esValido());
        assertTrue(Validaciones.validarNombre("").mensajeError().orElse("").contains("obligatorio"));
    }

    // ------------------------------------------------------------ patronus

    @Test
    @DisplayName("El patronus es obligatorio en los alumnos nuevos")
    void patronusObligatorioEnAlumnosNuevos() {
        assertFalse(Validaciones.validarPatronus(null, TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarPatronus("", TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarPatronus("   ", TipoAlumno.NUEVO).esValido());
        assertTrue(Validaciones.validarPatronus("Gato", TipoAlumno.NUEVO).esValido());
        assertTrue(Validaciones.validarPatronus(null, TipoAlumno.NUEVO)
                .mensajeError().orElse("").contains("obligatorio"));
    }

    @Test
    @DisplayName("El patronus es opcional en los alumnos históricos")
    void patronusOpcionalEnAlumnosHistoricos() {
        assertTrue(Validaciones.validarPatronus(null, TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarPatronus("", TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarPatronus("Ciervo", TipoAlumno.HISTORICO).esValido());
    }

    @Test
    @DisplayName("El patronus informado debe medir entre 2 y 60 caracteres")
    void patronusRespetaLaLongitudPermitida() {
        assertTrue(Validaciones.validarPatronus("ab", TipoAlumno.NUEVO).esValido());
        assertTrue(Validaciones.validarPatronus("a".repeat(60), TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarPatronus("a", TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarPatronus("a".repeat(61), TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarPatronus("a", TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarPatronus("a".repeat(61), TipoAlumno.HISTORICO)
                .mensajeError().orElse("").contains("60"));
    }

    // --------------------------------------------------------------- casa

    @Test
    @DisplayName("La casa es obligatoria en los alumnos históricos")
    void casaObligatoriaEnAlumnosHistoricos() {
        assertFalse(Validaciones.validarCasa(null, TipoAlumno.HISTORICO).esValido());
        assertFalse(Validaciones.validarCasa("   ", TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarCasa("Gryffindor", TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarCasa(null, TipoAlumno.HISTORICO)
                .mensajeError().orElse("").contains("obligatoria"));
    }

    @Test
    @DisplayName("La casa está prohibida en los alumnos nuevos")
    void casaProhibidaEnAlumnosNuevos() {
        assertTrue(Validaciones.validarCasa(null, TipoAlumno.NUEVO).esValido());
        assertTrue(Validaciones.validarCasa("", TipoAlumno.NUEVO).esValido());
        assertFalse(Validaciones.validarCasa("Gryffindor", TipoAlumno.NUEVO).esValido());
        assertTrue(Validaciones.validarCasa("Gryffindor", TipoAlumno.NUEVO)
                .mensajeError().orElse("").contains("no deben tener casa"));
    }

    // ------------------------------------------------------- alumno completo

    @Test
    @DisplayName("validarAlumno devuelve el primer error en orden DNI, nombre, casa, patronus")
    void validarAlumnoDevuelveElPrimerError() {
        assertEquals("El DNI es obligatorio.",
                Validaciones.validarAlumno(null, "Ana", "Gryffindor", "Ciervo", TipoAlumno.HISTORICO)
                        .mensajeError().orElse(null));
        assertTrue(Validaciones.validarAlumno("12345678A", "A", "Gryffindor", "Ciervo", TipoAlumno.HISTORICO)
                .mensajeError().orElse("").contains("nombre"));
        assertTrue(Validaciones.validarAlumno("12345678A", "Ana", null, "Ciervo", TipoAlumno.HISTORICO)
                .mensajeError().orElse("").contains("casa"));
        assertTrue(Validaciones.validarAlumno("12345678A", "Ana", null, null, TipoAlumno.NUEVO)
                .mensajeError().orElse("").contains("patronus"));
    }

    @Test
    @DisplayName("validarAlumno acepta un histórico completo y un nuevo sin casa")
    void validarAlumnoAceptaFichasCorrectas() {
        assertTrue(Validaciones.validarAlumno("12345678A", "Harry Potter", "Gryffindor",
                "Ciervo", TipoAlumno.HISTORICO).esValido());
        assertTrue(Validaciones.validarAlumno("67890123F", "Maria Rodriguez", null,
                "Gato", TipoAlumno.NUEVO).esValido());
    }

    // ---------------------------------------------------------- enmascarado

    @Test
    @DisplayName("enmascararDNI conserva 2 primeros y 3 últimos: 12345645A -> 12***45A")
    void enmascaraDniSegunLaReglaDelContrato() {
        assertEquals("12***45A", Validaciones.enmascararDNI("12345645A"));
        assertEquals("12***78A", Validaciones.enmascararDNI("12345678A"));
        assertEquals("12***78A", Validaciones.enmascararDNI(" 123 45678 a "));
    }

    @Test
    @DisplayName("El DNI enmascarado nunca muestra el DNI completo")
    void nuncaMuestraElDniCompleto() {
        String enmascarado = Validaciones.enmascararDNI("12345678A");
        assertFalse(enmascarado.contains("345678"));
        assertFalse(enmascarado.equals("12345678A"));
        assertEquals(8, enmascarado.length());
    }

    @Test
    @DisplayName("Los DNI no identificables se sustituyen por ***")
    void enmascaraValoresNoIdentificables() {
        assertEquals("***", Validaciones.enmascararDNI(null));
        assertEquals("***", Validaciones.enmascararDNI(""));
        assertEquals("***", Validaciones.enmascararDNI("12A"));
    }

    // ---------------------------------------------------- ResultadoValidacion

    @Test
    @DisplayName("ResultadoValidacion distingue entre correcto e inválido")
    void resultadoValidacionEsInmutable() {
        ResultadoValidacion correcto = ResultadoValidacion.correcto();
        assertTrue(correcto.esValido());
        assertTrue(correcto.mensajeError().isEmpty());
        assertEquals("válido", correcto.toString());

        ResultadoValidacion fallo = ResultadoValidacion.error("Mensaje de prueba");
        assertFalse(fallo.esValido());
        assertEquals(Optional.of("Mensaje de prueba"), fallo.mensajeError());
        assertEquals("Mensaje de prueba", fallo.mensajeErrorObligatorio());
        assertEquals("inválido: Mensaje de prueba", fallo.toString());

        assertThrows(IllegalStateException.class, correcto::mensajeErrorObligatorio);
        assertThrows(IllegalArgumentException.class, () -> ResultadoValidacion.error("  "));
    }
}
