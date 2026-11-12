package com.omni3.gestorhogwartsjavafx.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del fallo de red del cliente HTTP: el mensaje que ve el usuario
 * debe ser entendible y con el request-id de la petición.
 */
class ClienteAlumnosHttpTest {

    /** Ngrok free debe entregar JSON en lugar de su página interstitial. */
    @Test
    void enviaCabeceraParaSaltarInterstitialDeNgrok() throws Exception {
        AtomicReference<String> cabeceraNgrok = new AtomicReference<>();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/health", intercambio -> {
            cabeceraNgrok.set(intercambio.getRequestHeaders()
                    .getFirst("ngrok-skip-browser-warning"));
            byte[] respuesta = "{\"status\":\"ok\",\"reto\":\"R01\",\"timestamp\":\"2026-11-12T00:00:00Z\"}"
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
        try {
            ClienteAlumnosHttp cliente = new ClienteAlumnosHttp(new ConfigApi(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), Duration.ofSeconds(2)));

            var salud = cliente.salud();

            assertEquals("1", cabeceraNgrok.get());
            assertTrue(salud.esR01());
            assertEquals("OK", salud.estado());
        } finally {
            servidor.stop(0);
        }
    }

    /** La vista SQLite consulta la copia y no descarta las filas por su origen. */
    @Test
    void sqliteConsultaCopiaYConservaOrigenYBusqueda() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/alumnos", intercambio -> {
            query.set(intercambio.getRequestURI().getQuery());
            String respuesta = "[{\"id\":\"20000000A\",\"nombre\":\"Hermione\",\"origen\":\"mariadb\"},"
                    + "{\"id\":\"10000002B\",\"nombre\":\"Cedric\",\"origen\":\"h2\"}]";
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        try {
            ClienteAlumnosHttp cliente = new ClienteAlumnosHttp(new ConfigApi(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), Duration.ofSeconds(2)));
            var copia = cliente.listarAlumnos(Origen.SQLITE, "", 10, 0);
            assertEquals("lectura=sqlite", query.get());
            assertEquals(2, copia.size());
            assertEquals("MARIADB", copia.get(0).origen());
            assertEquals("H2", copia.get(1).origen());
            assertEquals(1, cliente.listarAlumnos(Origen.SQLITE, "Herm", 10, 0).size());
            assertEquals("Hermione", cliente.listarAlumnos(Origen.MARIADB, "", 10, 0).get(0).nombre());
            assertEquals(null, query.get());
        } finally {
            servidor.stop(0);
        }
    }

    /** Conserva la versión del formulario aunque la consulta auxiliar sea más reciente. */
    @Test
    void enviaVersionOriginalYLeeEstadoReal() throws Exception {
        AtomicReference<String> cuerpoPut = new AtomicReference<>();
        AtomicReference<String> versionBaja = new AtomicReference<>();
        AtomicReference<String> versionServidor = new AtomicReference<>("3");
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            String respuesta;
            if ("/health".equals(intercambio.getRequestURI().getPath())) {
                respuesta = "{\"status\":\"degraded\",\"reto\":\"R01\",\"timestamp\":\"2026-11-05T10:00:00Z\"}";
            } else if ("DELETE".equals(intercambio.getRequestMethod())) {
                versionBaja.set(intercambio.getRequestHeaders().getFirst("If-Match"));
                intercambio.sendResponseHeaders(204, -1);
                intercambio.close();
                return;
            } else {
                if ("PUT".equals(intercambio.getRequestMethod())) {
                    cuerpoPut.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                }
                respuesta = "{\"id\":\"20000000A\",\"nombre\":\"Hermione\",\"patronus\":\"Otter\","
                        + "\"origen\":\"mariadb\",\"tipo\":\"NUEVO\",\"version\":" + versionServidor.get()
                        + ",\"activo\":true,\"actualizado_en\":\"2026-11-05T10:00:00Z\"}";
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        try {
            ClienteAlumnosHttp cliente = new ClienteAlumnosHttp(new ConfigApi(
                    "http://127.0.0.1:" + servidor.getAddress().getPort(), Duration.ofSeconds(2)));
            var original = cliente.obtenerAlumno("20000000A");
            assertEquals(3, original.version());
            assertEquals("NUEVO", original.tipo());
            assertEquals("MARIADB", original.origen());
            versionServidor.set("4");
            cliente.editarAlumno(original.dni(), original);
            assertTrue(cuerpoPut.get().contains("\"version\":3"));
            cliente.bajarAlumno(original.dni());
            assertEquals("\"4\"", versionBaja.get());
            assertEquals("DEGRADADO", cliente.salud().estado());
            assertTrue(cliente.salud().esR01());
        } finally {
            servidor.stop(0);
        }
    }

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
