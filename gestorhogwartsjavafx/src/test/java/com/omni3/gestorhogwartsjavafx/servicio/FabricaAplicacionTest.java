package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosDemo;
import com.omni3.gestorhogwartsjavafx.api.ConfigApi;
import com.omni3.gestorhogwartsjavafx.api.Modo;
import com.omni3.gestorhogwartsjavafx.viewmodel.AltaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.BajaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConectoresViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConsultaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EdicionViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.SaludViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.SincronizacionViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la fábrica única de la aplicación (§3): modo DEMO por
 * defecto, conmutación del modo con {@code -Domni3.modo}, inyección del
 * cliente para pruebas y creación de todos los viewmodels.
 *
 * <p>Para no instanciar el cliente HTTP (que abriría un {@code HttpClient}
 * en el JVM de pruebas) el modo API se valida sobre la configuración y
 * sobre el camino de inyección, no sobre {@code FabricaAplicacion()}.</p>
 */
@DisplayName("FabricaAplicacion")
class FabricaAplicacionTest {

    /** Cliente de demostración con latencia 0. */
    private final ClienteAlumnosDemo demo = new ClienteAlumnosDemo(0);

    /** Valor previo de la system property de modo, para restaurarlo. */
    private String modoPrevio;

    @BeforeEach
    void guardarModo() {
        modoPrevio = System.getProperty(ConfigApi.CLAVE_MODO);
        System.clearProperty(ConfigApi.CLAVE_MODO);
    }

    @AfterEach
    void restaurarModo() {
        if (modoPrevio == null) {
            System.clearProperty(ConfigApi.CLAVE_MODO);
        } else {
            System.setProperty(ConfigApi.CLAVE_MODO, modoPrevio);
        }
    }

    @Test
    @DisplayName("La fábrica por defecto arranca en modo DEMO con el cliente de demostración")
    void porDefectoArrancaEnModoDemo() {
        FabricaAplicacion fabrica = new FabricaAplicacion();
        try {
            assertEquals(Modo.DEMO, fabrica.getConfig().getModo());
            assertTrue(fabrica.getConfig().esDemo());
            assertTrue(fabrica.getCliente() instanceof ClienteAlumnosDemo,
                    "En modo DEMO debe crearse ClienteAlumnosDemo");
            assertNotNull(fabrica.getServicio());
        } finally {
            fabrica.cerrar();
        }
    }

    @Test
    @DisplayName("-Domni3.modo=api conmuta la configuración a la API real")
    void elModoApiSeDetectaEnLaConfiguracion() {
        System.setProperty(ConfigApi.CLAVE_MODO, "api");

        ConfigApi config = ConfigApi.porDefecto();

        assertEquals(Modo.API, config.getModo());
        assertFalse(config.esDemo());
        assertEquals(ConfigApi.URL_POR_DEFECTO, config.getBaseUrl());
    }

    @Test
    @DisplayName("La fábrica admite inyectar configuración, cliente y servicio")
    void admiteInyeccionCompleta() {
        ConfigApi configApi = new ConfigApi(ConfigApi.URL_POR_DEFECTO, Modo.API,
                ConfigApi.TIMEOUT_POR_DEFECTO);
        ServicioAlumnos servicio = new ServicioAlumnos(demo, Runnable::run, Runnable::run);

        FabricaAplicacion fabrica = new FabricaAplicacion(configApi, demo, servicio);

        assertSame(configApi, fabrica.getConfig());
        assertSame(demo, fabrica.getCliente());
        assertSame(servicio, fabrica.getServicio());
        assertFalse(fabrica.getConfig().esDemo());
    }

    @Test
    @DisplayName("paraPruebas() usa el cliente inyectado y un servicio síncrono")
    void paraPruebasUsaElClienteInyectado() {
        FabricaAplicacion fabrica = FabricaAplicacion.paraPruebas(demo);
        try {
            assertSame(demo, fabrica.getCliente());
            assertTrue(fabrica.getConfig().esDemo());
            ConsultaViewModel consulta = fabrica.crearConsultaViewModel();
            assertSame(demo, consulta.getServicio().getCliente());
            consulta.buscar();
            assertEquals(7, consulta.getAlumnos().size());
        } finally {
            fabrica.cerrar();
        }
    }

    @Test
    @DisplayName("Todos los viewmodels se crean compartiendo el mismo servicio")
    void creaTodosLosViewmodels() {
        FabricaAplicacion fabrica = FabricaAplicacion.paraPruebas(demo);
        try {
            ConsultaViewModel consulta = fabrica.crearConsultaViewModel();
            AltaViewModel alta = fabrica.crearAltaViewModel();
            EdicionViewModel edicion = fabrica.crearEdicionViewModel();
            BajaViewModel baja = fabrica.crearBajaViewModel();
            ConectoresViewModel conectores = fabrica.crearConectoresViewModel();
            SaludViewModel salud = fabrica.crearSaludViewModel();
            SincronizacionViewModel sincronizacion = fabrica.crearSincronizacionViewModel();

            assertNotNull(consulta);
            assertNotNull(alta);
            assertNotNull(edicion);
            assertNotNull(baja);
            assertNotNull(conectores);
            assertNotNull(salud);
            assertNotNull(sincronizacion);

            assertSame(fabrica.getServicio(), consulta.getServicio());
            assertSame(fabrica.getServicio(), alta.getServicio());
            assertSame(fabrica.getServicio(), edicion.getServicio());
            assertSame(fabrica.getServicio(), baja.getServicio());
            assertSame(fabrica.getServicio(), conectores.getServicio());
            assertSame(fabrica.getServicio(), salud.getServicio());
            assertSame(fabrica.getServicio(), sincronizacion.getServicio());
        } finally {
            fabrica.cerrar();
        }
    }
}
