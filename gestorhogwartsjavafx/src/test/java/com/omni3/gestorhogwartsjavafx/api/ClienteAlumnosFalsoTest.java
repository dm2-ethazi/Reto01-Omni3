package com.omni3.gestorhogwartsjavafx.api;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del doble de pruebas de ClienteAlumnos (§3 del contrato): consultas con
 * fuente de origen, alta, duplicidad, conflicto de concurrencia 409,
 * baja lógica y salud con identificador R01. Sin llamadas de red.
 */
@DisplayName("ClienteAlumnosFalso")
class ClienteAlumnosFalsoTest {

    private ClienteAlumnosFalso falso;

    @BeforeEach
    void preparar() {
        falso = new ClienteAlumnosFalso(0);
    }

    // ------------------------------------------------------------ consultas

    @Test
    @DisplayName("El listado unificado muestra siempre la fuente de origen")
    void listadoUnificadoIncluyeLaFuenteDeOrigen() {
        List<AlumnoDto> alumnos = falso.listarAlumnos(null, "", 0, 0);

        assertEquals(7, alumnos.size());
        Set<String> origenes = alumnos.stream()
                .map(AlumnoDto::origen)
                .collect(Collectors.toSet());
        assertTrue(origenes.containsAll(List.of("DERBY", "HSQLDB", "H2", "ORACLE", "SQLITE", "MARIADB")),
                "Deben aparecer todas las fuentes: " + origenes);
        assertTrue(alumnos.stream().allMatch(a -> a.origen() != null && !a.origen().isBlank()),
                "Ninguna fila puede quedar sin origen");
        assertTrue(alumnos.stream().allMatch(AlumnoDto::activo),
                "El listado solo debe devolver alumnos activos");
    }

    @Test
    @DisplayName("El filtro por origen devuelve solo esa fuente")
    void listadoFiltraPorOrigen() {
        List<AlumnoDto> derby = falso.listarAlumnos(Origen.DERBY, "", 0, 0);
        List<AlumnoDto> mariadb = falso.listarAlumnos(Origen.MARIADB, "", 0, 0);

        assertEquals(1, derby.size());
        assertEquals("DERBY", derby.get(0).origen());
        assertEquals(2, mariadb.size());
        assertTrue(mariadb.stream().allMatch(a -> "MARIADB".equals(a.origen())));
    }

    @Test
    @DisplayName("La vista CENTRAL devuelve todas las fuentes")
    void vistaCentralDevuelveTodo() {
        assertEquals(7, falso.listarAlumnos(Origen.CENTRAL, "", 0, 0).size());
    }

    @Test
    @DisplayName("La búsqueda q filtra por nombre y por DNI sin distinguir mayúsculas")
    void listadoFiltraPorTexto() {
        assertEquals(1, falso.listarAlumnos(null, "maria", 0, 0).size());
        assertEquals(1, falso.listarAlumnos(null, "12345678", 0, 0).size());
        assertEquals(1, falso.listarAlumnos(null, "harry potter", 0, 0).size());
        assertEquals(0, falso.listarAlumnos(null, "nadie-existe", 0, 0).size());
    }

    @Test
    @DisplayName("La paginación respeta limit y offset")
    void listadoRespetaLaPaginacion() {
        assertEquals(2, falso.listarAlumnos(null, "", 2, 0).size());
        assertEquals(2, falso.listarAlumnos(null, "", 2, 5).size());
        assertEquals(0, falso.listarAlumnos(null, "", 2, 99).size());
        assertEquals(7, falso.listarAlumnos(null, "", 0, 0).size());
    }

    @Test
    @DisplayName("La ficha de un alumno existe y un DNI desconocido da 404")
    void obtenerAlumno() {
        AlumnoDto ficha = falso.obtenerAlumno("12345678A");
        assertEquals("Harry Potter", ficha.nombre());
        assertEquals("DERBY", ficha.origen());

        ExcepcionApi error = assertThrows(ExcepcionApi.class, () -> falso.obtenerAlumno("99999999Z"));
        assertEquals(404, error.getStatus());
        assertEquals("NO_ENCONTRADO", error.getCodigo());
        assertNotNull(error.getRequestId());
        assertFalse(error.getMensaje().contains("99999999Z"), "El mensaje no debe filtrar el DNI completo");
    }

    // ----------------------------------------------------------------- alta

    @Test
    @DisplayName("El alta de un alumno nuevo funciona y normaliza el DNI")
    void altaDeAlumnoNuevo() {
        AlumnoDto alta = falso.altaAlumno(
                new AlumnoDto(" 87654321 b ", "Ana Torres", null, "Lechuza",
                        "MARIADB", "NUEVO", 0, null, true));

        assertEquals("87654321B", alta.dni());
        assertEquals(1, alta.version());
        assertTrue(alta.activo());
        assertTrue(alta.casa() == null || alta.casa().isBlank(), "Un alumno nuevo no debe tener casa");
        assertNotNull(alta.patronus(), "Un alumno nuevo debe llevar patronus");
        assertEquals("NUEVO", alta.tipo());
        assertEquals(alta, falso.obtenerAlumno("87654321B"));
        assertEquals(8, falso.listarAlumnos(null, "", 0, 0).size());
    }

    @Test
    @DisplayName("El alta con DNI duplicado devuelve error 409 como la API real")
    void altaConDniDuplicado() {
        AlumnoDto duplicado = new AlumnoDto("12345678A", "Impostor", null, "Ciervo",
                "MARIADB", "NUEVO", 0, null, true);

        ExcepcionApi error = assertThrows(ExcepcionApi.class, () -> falso.altaAlumno(duplicado));

        assertEquals(409, error.getStatus());
        assertEquals("DNI_DUPLICADO", error.getCodigo());
        assertTrue(error.getMensaje().contains("12***78A"), "El error debe enmascarar el DNI");
        assertTrue(error.getMensaje().contains("ya existe"), "El error debe explicar el motivo");
        assertEquals(7, falso.listarAlumnos(null, "", 0, 0).size());
    }

    @Test
    @DisplayName("El alta con datos inválidos devuelve error de validación 400")
    void altaConDatosInvalidos() {
        ExcepcionApi sinPatronus = assertThrows(ExcepcionApi.class,
                () -> falso.altaAlumno(new AlumnoDto("99999999Z", "Nuevo Sin Patronus", null, null,
                        "MARIADB", "NUEVO", 0, null, true)));
        assertEquals(400, sinPatronus.getStatus());
        assertEquals("VALIDACION", sinPatronus.getCodigo());
        assertTrue(sinPatronus.getMensaje().contains("patronus"));

        ExcepcionApi conCasa = assertThrows(ExcepcionApi.class,
                () -> falso.altaAlumno(new AlumnoDto("99999999Z", "Nuevo Con Casa", "Gryffindor", "Lobo",
                        "MARIADB", "NUEVO", 0, null, true)));
        assertEquals("VALIDACION", conCasa.getCodigo());
        assertTrue(conCasa.getMensaje().contains("casa"));
    }

    // -------------------------------------------------------------- edición

    @Test
    @DisplayName("La edición con la versión previa correcta incrementa la versión")
    void edicionConVersionPreviaCorrecta() {
        AlumnoDto original = falso.obtenerAlumno("12345678A");

        AlumnoDto editado = falso.editarAlumno("12345678A",
                new AlumnoDto(original.dni(), "Harry James Potter", original.casa(), original.patronus(),
                        original.origen(), original.tipo(), original.version(), null, original.activo()));

        assertEquals(original.version() + 1, editado.version());
        assertEquals("Harry James Potter", editado.nombre());
        assertEquals(original.origen(), editado.origen(), "La edición no cambia la fuente de origen");
        assertEquals(editado, falso.obtenerAlumno("12345678A"));
    }

    @Test
    @DisplayName("La edición con un estado previo distinto devuelve 409 y no guarda nada")
    void edicionConVersionDesactualizadaDevuelve409() {
        AlumnoDto original = falso.obtenerAlumno("12345678A");

        ExcepcionApi conflicto = assertThrows(ExcepcionApi.class,
                () -> falso.editarAlumno("12345678A",
                        new AlumnoDto(original.dni(), "Nombre Manipulado", original.casa(), original.patronus(),
                                original.origen(), original.tipo(), original.version() + 5, null, true)));

        assertEquals(409, conflicto.getStatus());
        assertEquals("CONFLICTO_VERSION", conflicto.getCodigo());
        assertTrue(conflicto.esConflicto());
        assertNotNull(conflicto.getRequestId());
        assertTrue(conflicto.getMensaje().contains("Recargue"), "El 409 debe invitar a recargar");

        assertEquals("Harry Potter", falso.obtenerAlumno("12345678A").nombre(),
                "El conflicto nunca debe sobrescribir en silencio");
        assertEquals(1, falso.obtenerAlumno("12345678A").version());
    }

    @Test
    @DisplayName("La edición de un alumno inexistente devuelve 404")
    void edicionDeAlumnoInexistente() {
        ExcepcionApi error = assertThrows(ExcepcionApi.class,
                () -> falso.editarAlumno("99999999Z",
                        new AlumnoDto("99999999Z", "Nadie", null, "Lobo", "MARIADB", "NUEVO", 1, null, true)));
        assertEquals(404, error.getStatus());
        assertEquals("NO_ENCONTRADO", error.getCodigo());
    }

    // ---------------------------------------------------------------- baja

    @Test
    @DisplayName("La baja es lógica: el alumno queda inactivo y sale del listado")
    void bajaLogica() {
        assertEquals(7, falso.listarAlumnos(null, "", 0, 0).size());

        falso.bajarAlumno("56789012E");

        assertEquals(7, falso.totalAlumnos(), "La baja lógica no elimina filas del almacén");
        assertEquals(6, falso.listarAlumnos(null, "", 0, 0).size(),
                "La consulta unificada solo devuelve alumnos activos");
        assertTrue(falso.listarAlumnos(null, "", 0, 0).stream()
                .noneMatch(a -> "56789012E".equals(a.dni())), "El alumno de baja no aparece en la consulta");

        AlumnoDto deBaja = falso.obtenerAlumno("56789012E");
        assertFalse(deBaja.activo());
        assertEquals(2, deBaja.version());
    }

    @Test
    @DisplayName("La baja de un alumno inexistente devuelve 404")
    void bajaDeAlumnoInexistente() {
        ExcepcionApi error = assertThrows(ExcepcionApi.class, () -> falso.bajarAlumno("99999999Z"));
        assertEquals(404, error.getStatus());
        assertEquals("NO_ENCONTRADO", error.getCodigo());
    }

    // ------------------------------------------------------ estado y red

    @Test
    @DisplayName("La salud informa del identificador R01")
    void saludIdentificadorR01() {
        SaludDto salud = falso.salud();

        assertEquals("R01", salud.identificador());
        assertTrue(salud.esR01());
        assertEquals("OK", salud.estado());
        assertNotNull(salud.momento());
    }

    @Test
    @DisplayName("La latencia simulada es configurable y 0 en pruebas")
    void latenciaConfigurable() {
        assertEquals(0, falso.getLatenciaMs());
        falso.setLatenciaMs(50);
        assertEquals(50, falso.getLatenciaMs());
        falso.setLatenciaMs(-10);
        assertEquals(0, falso.getLatenciaMs(), "Una latencia negativa se trata como 0");
        assertEquals(ClienteAlumnosFalso.LATENCIA_POR_DEFECTO,
                new ClienteAlumnosFalso().getLatenciaMs());
    }

    @Test
    @DisplayName("reiniciar devuelve los datos originales")
    void reiniciarRestauraLosDatos() {
        falso.bajarAlumno("12345678A");
        falso.reiniciar();

        assertEquals(7, falso.listarAlumnos(null, "", 0, 0).size());
        assertTrue(falso.obtenerAlumno("12345678A").activo());
    }

    // ----------------------------------------------------------- patronus

    @Test
    @DisplayName("listarPatronus devuelve el catálogo en 3 idiomas con URLs")
    void listarPatronusDevuelveElCatalogo() {
        List<PatronusDto> catalogo = falso.listarPatronus();

        assertFalse(catalogo.isEmpty(), "El catálogo de pruebas no puede estar vacío");
        assertTrue(catalogo.stream().allMatch(p -> p.nomEsp() != null && !p.nomEsp().isBlank()),
                "Cada entrada debe tener nombre en español");
        assertTrue(catalogo.stream().allMatch(p -> p.nomEng() != null && !p.nomEng().isBlank()),
                "Cada entrada debe tener nombre en inglés");
        assertTrue(catalogo.stream().allMatch(p -> p.nomEus() != null && !p.nomEus().isBlank()),
                "Cada entrada debe tener nombre en euskera");
        assertTrue(catalogo.stream().allMatch(p -> p.url() != null && !p.url().isBlank()),
                "Cada entrada debe tener URL de imagen");
        assertTrue(catalogo.stream().anyMatch(p -> "Ciervo".equals(p.nomEsp())));
        assertTrue(catalogo.stream().anyMatch(p -> "Stag".equals(p.nomEng())));
        assertTrue(catalogo.stream().anyMatch(p -> "Orein".equals(p.nomEus())));
    }

    @Test
    @DisplayName("listarPatronus es estable entre llamadas (misma lista)")
    void listarPatronusEsEstable() {
        assertEquals(falso.listarPatronus(), falso.listarPatronus());
    }
}
