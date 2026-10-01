package es.r01.desktop.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import es.r01.desktop.modelo.Estudiante;
import es.r01.desktop.modelo.Pagina;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del cliente de escritorio contra la API real. Si el backend no
 * está levantado en 127.0.0.1:8000 se omiten (assumption), no fallan.
 */
class CentralClientTest {
    private static final String BASE = "http://127.0.0.1:8000";
    private static boolean disponible;

    @BeforeAll
    static void detectarApi() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 8000), 1000);
            disponible = true;
        } catch (IOException ignorada) {
            disponible = false;
        }
    }

    @Test
    void saludInformaDelEstadoDeLaCentralYLosConectores() throws Exception {
        assumeTrue(disponible, "el backend no está en 127.0.0.1:8000");

        JsonNode salud = new CentralClient(BASE).salud();
        assertEquals("R01", salud.path("reto").asText());
        assertTrue(salud.path("status").isTextual());
        assertTrue(salud.path("conectores").size() >= 4);
        assertTrue(salud.path("central").has("sqlite"));
    }

    @Test
    void conectoresDevuelveLosCuatroOrigenes() throws Exception {
        assumeTrue(disponible, "el backend no está en 127.0.0.1:8000");

        JsonNode conectores = new CentralClient(BASE).conectores().path("conectores");
        for (String esperado : new String[] {"derby", "hsqldb", "h2", "oracle"}) {
            assertTrue(conectores.has(esperado), "falta el conector " + esperado);
        }
    }

    @Test
    void listaLaCentralFiltraYBuscaPorDni() throws Exception {
        assumeTrue(disponible, "el backend no está en 127.0.0.1:8000");

        CentralClient cliente = new CentralClient(BASE);
        Pagina todo = cliente.central(null, null, 0, 50);
        assertTrue(todo.total >= 40, "la central debería tener al menos 40 filas");
        assertFalse(todo.items.isEmpty());

        Estudiante primero = todo.items.get(0);
        Estudiante porDni = cliente.porDni(primero.getDni());
        assertNotNull(porDni);
        assertEquals(primero.getDni(), porDni.getDni());
        assertNull(cliente.porDni("00000000A"));

        Pagina filtrada = cliente.central(primero.getNombre().substring(0, 3), null, 0, 50);
        assertFalse(filtrada.items.isEmpty());
    }

    @Test
    void consultaUnOrigenConcreto() throws Exception {
        assumeTrue(disponible, "el backend no está en 127.0.0.1:8000");

        Pagina origen = new CentralClient(BASE).origen("derby", null, null, 0, 5);
        assertEquals(10, origen.total, "derby debe tener la semilla de 10 estudiantes");
        assertEquals("derby", origen.conector);
        assertEquals(5, origen.items.size());
    }
}
