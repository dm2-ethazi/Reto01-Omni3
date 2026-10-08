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
}
