package com.omni3.gestorhogwartsjavafx.viewmodel;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosFalso;
import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.servicio.FabricaAplicacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pruebas del viewmodel de edición (§2.3): la carga de la ficha y el
 * control de concurrencia optimista.
 *
 * <p>Contrasta con {@link AltaViewModelTest}: aquí el 409 sí debe
 * publicarse como {@link EstadoCarga#CONFLICTO} para que la vista ofrezca
 * recargar sin sobrescribir.</p>
 */
@DisplayName("EdicionViewModel")
class EdicionViewModelTest {

    /** DNI sembrado en el doble de pruebas. */
    private static final String DNI = "12345678A";

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
    @DisplayName("Al cargar se rellenan los campos y la versión vigente")
    void cargarRellenaLaFicha() {
        EdicionViewModel edicion = fabrica.crearEdicionViewModel();

        edicion.cargar(DNI);

        assertEquals(EstadoCarga.OK, edicion.getEstado());
        assertEquals(DNI, edicion.getDni());
        assertEquals("Harry Potter", edicion.getNombre());
        assertEquals(1L, edicion.getVersionPrevia());
        assertEquals(Long.valueOf(1L), edicion.versionPreviaProperty().get());
    }

    @Test
    @DisplayName("El 409 por versión desactualizada se publica como conflicto")
    void conflictoDeVersionSePublicaComoConflicto() {
        EdicionViewModel edicion = fabrica.crearEdicionViewModel();
        edicion.cargar(DNI);
        edicion.nombreProperty().set("Nombre editado en local");

        // Otra sesión guarda la ficha: la versión del servidor pasa a 2.
        falso.editarAlumno(DNI, new AlumnoDto(DNI, "Harry Potter", "Gryffindor",
                "Ciervo", "DERBY", "HISTORICO", 1L, null, true));

        edicion.guardar();

        assertEquals(EstadoCarga.CONFLICTO, edicion.getEstado(),
                "El 409 de la edición sí es un conflicto de concurrencia");
        assertNull(edicion.getGuardado(), "No debe publicarse ninguna ficha guardada");
        assertEquals("Nombre editado en local", edicion.getNombre(),
                "Los cambios locales se conservan para poder recargarlos");
    }
}
