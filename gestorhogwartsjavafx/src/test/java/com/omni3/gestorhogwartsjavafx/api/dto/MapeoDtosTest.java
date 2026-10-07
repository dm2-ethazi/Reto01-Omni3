package com.omni3.gestorhogwartsjavafx.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni3.gestorhogwartsjavafx.modelo.Alumno;
import com.omni3.gestorhogwartsjavafx.modelo.EstadoConector;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del mapeo JSON de los DTO (§3): tolerancia a campos nuevos,
 * envelope de error y conversión al modelo de dominio.
 */
@DisplayName("Mapeo JSON de los DTO")
class MapeoDtosTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Un alumno se serializa y deserializa sin pérdidas")
    void roundtripDeAlumnoDto() throws Exception {
        AlumnoDto original = new AlumnoDto("12345678A", "Harry Potter", "Gryffindor", "Ciervo",
                "DERBY", "HISTORICO", 3, "2026-01-15T10:00:00Z", true);

        String json = mapper.writeValueAsString(original);
        AlumnoDto leido = mapper.readValue(json, AlumnoDto.class);

        assertEquals(original, leido);
        assertTrue(json.contains("\"origen\":\"DERBY\""));
        assertTrue(json.contains("\"activo\":true"));
    }

    @Test
    @DisplayName("Se ignoran los campos desconocidos que añada la API")
    void toleraCamposDesconocidos() throws Exception {
        String json = """
                {"dni":"12345678A","nombre":"Harry Potter","casa":"Gryffindor","patronus":"Ciervo",
                 "origen":"DERBY","tipo":"HISTORICO","version":1,
                 "actualizadoEn":"2026-01-15T10:00:00Z","activo":true,
                 "atributoFuturo":{"x":1},"otro":42}
                """;

        AlumnoDto leido = mapper.readValue(json, AlumnoDto.class);

        assertEquals("12345678A", leido.dni());
        assertEquals("DERBY", leido.origen());
        assertEquals(1, leido.version());
    }

    @Test
    @DisplayName("Se admite un JSON parcial sin versión ni estado")
    void toleraCamposOpcionalesAusentes() throws Exception {
        String json = "{\"dni\":\"12345678A\",\"nombre\":\"Harry Potter\"}";

        AlumnoDto leido = mapper.readValue(json, AlumnoDto.class);

        assertEquals("12345678A", leido.dni());
        assertEquals(0, leido.version());
        assertFalse(leido.activo());
    }

    @Test
    @DisplayName("Se lee el envelope de error documentado en el contrato")
    void leeElEnvelopeDeError() {
        String json = "{\"error\":{\"codigo\":\"CONFLICTO_VERSION\",\"mensaje\":\"Versión desactualizada\","
                + "\"detalle\":\"version=2\"}}";

        Optional<ErrorDto> error = ErrorDto.desdeEnvelope(mapper, json);

        assertTrue(error.isPresent());
        assertEquals("CONFLICTO_VERSION", error.get().codigo());
        assertEquals("Versión desactualizada", error.get().mensaje());
        assertEquals("version=2", error.get().detalle());
    }

    @Test
    @DisplayName("Un cuerpo sin envelope de error no produce error")
    void ignoraCuerposSinEnvelope() {
        assertTrue(ErrorDto.desdeEnvelope(mapper, "").isEmpty());
        assertTrue(ErrorDto.desdeEnvelope(mapper, null).isEmpty());
        assertTrue(ErrorDto.desdeEnvelope(mapper, "<html>500</html>").isEmpty());
        assertTrue(ErrorDto.desdeEnvelope(mapper, "{\"detalle\":\"suelto\"}").isEmpty());
    }

    @Test
    @DisplayName("La conversión al dominio tolera origen y tipo desconocidos")
    void convierteAlModeloTolerandoValoresDesconocidos() {
        AlumnoDto dto = new AlumnoDto("12345678A", "Harry Potter", "Gryffindor", "Ciervo",
                "POSTGRES", "INDEFINIDO", 2, "no-es-una-fecha", true);

        Alumno alumno = dto.aModelo();

        assertEquals(Origen.CENTRAL, alumno.origen(), "Un origen desconocido va a la vista unificada");
        assertEquals(TipoAlumno.HISTORICO, alumno.tipo());
        assertEquals("12345678A", alumno.dni());
        assertTrue(alumno.actualizadoEn() instanceof Instant);
    }

    @Test
    @DisplayName("El modelo de dominio y el DTO se convierten mutuamente")
    void convierteEntreModeloYDto() {
        Alumno alumno = new Alumno("12345678A", "Harry Potter", "Gryffindor", "Ciervo",
                Origen.DERBY, TipoAlumno.HISTORICO, 1L, Instant.parse("2026-01-15T10:00:00Z"), true);

        AlumnoDto dto = AlumnoDto.de(alumno);

        assertEquals("DERBY", dto.origen());
        assertEquals("HISTORICO", dto.tipo());
        assertEquals("2026-01-15T10:00:00Z", dto.actualizadoEn());
        assertEquals(alumno, dto.aModelo());
        assertFalse(alumno.toString().contains("12345678A"), "toString no debe filtrar el DNI completo");
    }

    @Test
    @DisplayName("SaludDto y ConectorDto interpretan sus textos de dominio")
    void interpretaSaludYConectores() throws Exception {
        SaludDto salud = mapper.readValue(
                "{\"estado\":\"OK\",\"identificador\":\"R01\",\"mensaje\":\"todo bien\","
                        + "\"momento\":\"2026-01-15T10:00:00Z\",\"futuro\":1}",
                SaludDto.class);
        assertTrue(salud.esR01());
        assertFalse(new SaludDto("OK", "R02", "", "").esR01());

        ConectorDto conector = mapper.readValue(
                "{\"origen\":\"H2\",\"estado\":\"degradado\",\"mensaje\":\"lento\","
                        + "\"ultimaSincronizacion\":\"2026-01-15T10:00:00Z\"}",
                ConectorDto.class);
        assertEquals(EstadoConector.DEGRADADO, conector.estadoDeDominio());
        assertEquals(Origen.H2, conector.origenDeDominio());
        assertEquals(EstadoConector.DESCONOCIDO,
                new ConectorDto("H2", "loquesea", "", "").estadoDeDominio());
        assertEquals(Origen.CENTRAL, new ConectorDto("POSTGRES", "OK", "", "").origenDeDominio());
    }
}
