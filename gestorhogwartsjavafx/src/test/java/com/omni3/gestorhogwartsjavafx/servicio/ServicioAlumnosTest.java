package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosFalso;
import com.omni3.gestorhogwartsjavafx.api.ExcepcionApi;
import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pruebas del servicio de fondo (§8 del contrato): entrega de resultados
 * por callback, ejecución fuera del hilo llamante, mapeo de errores y
 * cierre del pool. Sin toolkit JavaFX y con latencia 0.
 */
@DisplayName("ServicioAlumnos")
class ServicioAlumnosTest {

    /** Doble de pruebas con latencia 0, como exigen las pruebas. */
    private final ClienteAlumnosFalso falso = new ClienteAlumnosFalso(0);

    /** Servicio síncrono (executors directos) para pruebas deterministas. */
    private final ServicioAlumnos sincrono = new ServicioAlumnos(falso, Runnable::run, Runnable::run);

    /** Servicio con pool de fondo propio y publicador directo. */
    private ServicioAlumnos conPool;

    @AfterEach
    void limpiar() {
        if (conPool != null) {
            conPool.cerrar();
            conPool = null;
        }
    }

    @Test
    @DisplayName("Con executors directos el resultado se entrega de forma síncrona")
    void entregaElResultadoDeFormaSincrona() {
        AtomicReference<List<AlumnoDto>> recibidos = new AtomicReference<>();
        AtomicReference<ExcepcionApi> fallo = new AtomicReference<>();

        sincrono.listarAlumnos(Origen.CENTRAL, "", 0, 0, recibidos::set, fallo::set);

        assertNull(fallo.get(), "No debe haber error: " + fallo.get());
        assertNotNull(recibidos.get());
        assertEquals(7, recibidos.get().size());
        assertTrue(recibidos.get().stream().allMatch(a -> a.origen() != null && !a.origen().isBlank()),
                "Cada fila debe conservar su fuente de origen");
    }

    @Test
    @DisplayName("El error de la API llega íntegro al callback de fallo")
    void entregaElErrorAlCallbackDeFallo() {
        AtomicReference<AlumnoDto> recibido = new AtomicReference<>();
        AtomicReference<ExcepcionApi> fallo = new AtomicReference<>();

        sincrono.obtenerAlumno("99999999Z", recibido::set, fallo::set);

        assertNull(recibido.get(), "No debe entregarse resultado cuando hay error");
        assertNotNull(fallo.get());
        assertEquals(404, fallo.get().getStatus());
        assertEquals("NO_ENCONTRADO", fallo.get().getCodigo());
        assertNotNull(fallo.get().getRequestId());
    }

    @Test
    @DisplayName("La llamada no bloquea: el callback llega después, en el hilo de fondo")
    void laLlamadaNoBloqueaElHiloLlamante() throws InterruptedException {
        ClienteAlumnosFalso lenta = new ClienteAlumnosFalso(1000);
        conPool = new ServicioAlumnos(lenta, Runnable::run);
        CountDownLatch entrega = new CountDownLatch(1);
        AtomicReference<SaludDto> salud = new AtomicReference<>();
        AtomicReference<String> hiloDelCallback = new AtomicReference<>();

        conPool.salud(
                respuesta -> {
                    salud.set(respuesta);
                    hiloDelCallback.set(Thread.currentThread().getName());
                    entrega.countDown();
                },
                error -> entrega.countDown());

        assertFalse(entrega.await(300, TimeUnit.MILLISECONDS),
                "La latencia simulada (1000 ms) no debe esperarse en el hilo llamante");
        assertTrue(entrega.await(5, TimeUnit.SECONDS), "El callback debe llegar tarde pero llegar");
        assertNotNull(salud.get());
        assertEquals("R01", salud.get().identificador());
        assertTrue(hiloDelCallback.get().startsWith("omni3-servicio-alumnos-"),
                "El callback debe ejecutarse en el pool de fondo: " + hiloDelCallback.get());
    }

    @Test
    @DisplayName("Una excepción inesperada se convierte en FALLO_INESPERADO")
    void convierteLasExcepcionesInesperadas() {
        ClienteAlumnosFalso roto = new ClienteAlumnosFalso(0) {
            @Override
            public SaludDto salud() {
                throw new IllegalStateException("sin red");
            }
        };
        ServicioAlumnos servicio = new ServicioAlumnos(roto, Runnable::run, Runnable::run);
        AtomicReference<ExcepcionApi> fallo = new AtomicReference<>();

        servicio.salud(resultado -> fail("No debe haber resultado"), fallo::set);

        assertNotNull(fallo.get());
        assertEquals("FALLO_INESPERADO", fallo.get().getCodigo());
        assertEquals(0, fallo.get().getStatus());
        assertTrue(fallo.get().getMensaje().contains("sin red"));
    }

    @Test
    @DisplayName("La baja lógica avisa por callback sin resultado")
    void laBajaAvisaPorCallback() {
        AtomicBoolean hecha = new AtomicBoolean(false);
        AtomicReference<ExcepcionApi> fallo = new AtomicReference<>();

        sincrono.bajarAlumno("56789012E", () -> hecha.set(true), fallo::set);

        assertTrue(hecha.get());
        assertNull(fallo.get());
        assertFalse(falso.obtenerAlumno("56789012E").activo());
    }

    @Test
    @DisplayName("Después de cerrar, las peticiones fallan con SERVICIO_CERRADO")
    void lasPeticionesTrasElCierreFallan() {
        conPool = new ServicioAlumnos(falso, Runnable::run);
        conPool.cerrar();
        AtomicReference<ExcepcionApi> fallo = new AtomicReference<>();

        conPool.salud(resultado -> fail("No debe haber resultado tras cerrar"), fallo::set);

        assertNotNull(fallo.get());
        assertEquals("SERVICIO_CERRADO", fallo.get().getCodigo());
    }

    @Test
    @DisplayName("El servicio expone el cliente que envuelve")
    void exponeElClienteEnvuelto() {
        assertEquals(falso, sincrono.getCliente());
        assertEquals(falso, new ServicioAlumnos(falso, Runnable::run, Runnable::run).getCliente());
    }
}
