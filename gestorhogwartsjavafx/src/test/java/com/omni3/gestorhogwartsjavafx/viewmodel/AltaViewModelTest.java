package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosFalso;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del viewmodel de alta (§2.2): validación en cliente y, sobre
 * todo, el estado publicado cuando la API rechaza el alta.
 *
 * <p>El punto crítico es el 409 por DNI duplicado: como en un alta no hay
 * versión previa, debe publicarse {@link EstadoCarga#ERROR} y nunca
 * {@link EstadoCarga#CONFLICTO}, porque la vista abre el diálogo de
 * «otra sesión ha modificado la ficha» al ver un conflicto.</p>
 * @author jairoben
 */
@DisplayName("AltaViewModel")
class AltaViewModelTest {

    /** Doble de pruebas con latencia 0 y ejecución síncrona. */
    private ClienteAlumnosFalso falso;
    private FabricaAplicacion fabrica;

    @BeforeEach
    void preparar() {
        falso = new ClienteAlumnosFalso(0);
        fabrica = FabricaAplicacion.paraPruebas(falso);
    }

    @AfterEach
    void limpiar() {
        fabrica.cerrar();
    }

    @Test
    @DisplayName("El DNI duplicado (409) se publica como error y no como conflicto")
    void dniDuplicadoSePublicaComoError() {
        AltaViewModel alta = fabrica.crearAltaViewModel();
        alta.dniProperty().set("12345678A");
        alta.nombreProperty().set("Impostor");
        alta.patronusProperty().set("Ciervo");

        alta.guardar();

        assertEquals(EstadoCarga.ERROR, alta.getEstado(),
                "Un 409 de alta no es un conflicto de concurrencia: no hay versión previa");
        assertTrue(alta.getMensaje().contains("ya existe"),
                "El mensaje debe explicar que el DNI ya existe: " + alta.getMensaje());
        assertNull(alta.getAltaRealizada(), "No debe publicarse ningún alumno creado");
        assertFalse(alta.getRequestId().isEmpty(), "Debe conservarse el request-id para soporte");
    }

    @Test
    @DisplayName("El alta válida publica la ficha creada y pasa a OK")
    void altaValidaPublicaLaFichaCreada() {
        AltaViewModel alta = fabrica.crearAltaViewModel();
        alta.dniProperty().set("87654321B");
        alta.nombreProperty().set("Alumno Nuevo");
        alta.patronusProperty().set("Lechuza");

        alta.guardar();

        assertEquals(EstadoCarga.OK, alta.getEstado());
        assertNotNull(alta.getAltaRealizada());
        assertEquals("87654321B", alta.resultadoProperty().get().dni());
        assertEquals(8, falso.totalAlumnos());
    }

    @Test
    @DisplayName("Sin validar no se envía nada y el estado es de error")
    void sinValidarNoSeEnviaNada() {
        AltaViewModel alta = fabrica.crearAltaViewModel();

        alta.guardar();

        assertEquals(EstadoCarga.ERROR, alta.getEstado());
        assertTrue(alta.getMensaje().contains("Revise los campos"));
        assertEquals(7, falso.totalAlumnos());
    }
}
