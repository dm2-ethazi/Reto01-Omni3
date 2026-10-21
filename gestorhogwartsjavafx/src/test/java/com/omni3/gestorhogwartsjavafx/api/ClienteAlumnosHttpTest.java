package com.omni3.gestorhogwartsjavafx.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del fallo de red del cliente HTTP: el mensaje que ve el usuario
 * debe ser entendible y con el request-id de la petición.
 * @author jairoben
 */
class ClienteAlumnosHttpTest {

    /**
     * Conexión rechazada: el mensaje termina en {@code "null"} cuando la
     * excepción no trae detalle, lo cual es ilegible en la pantalla.
     */
    @Test
    void unPuertoCerradoNoProduceUnMensajeConNull() throws Exception {
        int puerto;
        try (ServerSocket liberado = new ServerSocket(0)) {
            puerto = liberado.getLocalPort();
        }
        ConfigApi config = new ConfigApi("http://127.0.0.1:" + puerto,
                Duration.ofSeconds(2));
        ClienteAlumnosHttp cliente = new ClienteAlumnosHttp(config);

        ExcepcionApi excepcion = assertThrows(ExcepcionApi.class,
                () -> cliente.listarAlumnos(Origen.CENTRAL, "", 10, 0));

        assertEquals(0, excepcion.getStatus());
        assertEquals("RED", excepcion.getCodigo());
        assertTrue(excepcion.getMensaje().startsWith(
                        "No se pudo contactar con la API en http://127.0.0.1:" + puerto),
                () -> "mensaje inesperado: " + excepcion.getMensaje());
        assertFalse(excepcion.getMensaje().endsWith("null"),
                () -> "el mensaje no debe acabar en null: " + excepcion.getMensaje());
        assertFalse(excepcion.getRequestId().isEmpty(),
                "el fallo de red también debe llevar request-id");
    }

    /**
     * API que acepta la conexión pero no responde: la petición debe
     * agotar el tiempo de espera (explicándolo en español) en lugar de
     * bloquear la pantalla.
     */
    @Test
    void unaApiQueNoRespondeAgotaElTiempoDeEspera() throws Exception {
        try (ServerSocket mudo = new ServerSocket(0)) {
            ConfigApi config = new ConfigApi("http://127.0.0.1:" + mudo.getLocalPort(),
                    Duration.ofMillis(700));
            ClienteAlumnosHttp cliente = new ClienteAlumnosHttp(config);

            long inicio = System.nanoTime();
            ExcepcionApi excepcion = assertThrows(ExcepcionApi.class,
                    () -> cliente.listarAlumnos(Origen.CENTRAL, "", 10, 0));
            long transcurrido = (System.nanoTime() - inicio) / 1_000_000;

            assertEquals("RED", excepcion.getCodigo());
            assertTrue(excepcion.getMensaje().contains("se agotó el tiempo de espera de 700 ms"),
                    () -> "mensaje inesperado: " + excepcion.getMensaje());
            assertTrue(transcurrido < 5_000,
                    () -> "debe agotar el timeout en lugar de bloquear: "
                            + transcurrido + " ms");
        }
    }
}
