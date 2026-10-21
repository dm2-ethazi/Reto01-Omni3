package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosFalso;
import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnosHttp;
import com.omni3.gestorhogwartsjavafx.api.ConfigApi;
import com.omni3.gestorhogwartsjavafx.viewmodel.AltaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.BajaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConsultaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EdicionViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.SaludViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la fábrica única de la aplicación (§3): configuración por
 * defecto, inyección del cliente para pruebas y creación de todos los
 * viewmodels.
 *
 * <p>La aplicación no tiene más clientes que {@link ClienteAlumnosHttp},
 * así que la fábrica por defecto se comprueba sobre la configuración y
 * sobre el tipo del cliente, sin abrir ninguna conexión.</p>
 */
@DisplayName("FabricaAplicacion")
class FabricaAplicacionTest {

    /** Doble de pruebas con latencia 0. */
    private final ClienteAlumnosFalso falso = new ClienteAlumnosFalso(0);

    @Test
    @DisplayName("La fábrica resuelve la URL de la propiedad de sistema del entorno")
    void porDefectoCreaElClienteHttp() {
        // La URL real ya no está en el código: ConfigLoader la resuelve
        // del entorno y esta propiedad tiene la máxima prioridad, así la
        // prueba no depende de si existe config.properties en la raíz.
        System.setProperty(ConfigApi.PROP_SISTEMA_URL, ConfigApi.URL_POR_DEFECTO);
        try {
            FabricaAplicacion fabrica = new FabricaAplicacion();
            try {
                assertEquals(ConfigApi.URL_POR_DEFECTO, fabrica.getConfig().getBaseUrl());
                assertEquals(ConfigApi.TIMEOUT_POR_DEFECTO, fabrica.getConfig().getTimeout());
                assertTrue(fabrica.getCliente() instanceof ClienteAlumnosHttp,
                        "La aplicación solo puede crear ClienteAlumnosHttp");
                assertNotNull(fabrica.getServicio());
            } finally {
                fabrica.cerrar();
            }
        } finally {
            System.clearProperty(ConfigApi.PROP_SISTEMA_URL);
        }
    }

    @Test
    @DisplayName("La configuración por defecto usa la URL local y el timeout por defecto")
    void laConfiguracionPorDefectoEsLaDeLaApiLocal() {
        ConfigApi config = ConfigApi.porDefecto();

        assertEquals(ConfigApi.URL_POR_DEFECTO, config.getBaseUrl());
        assertEquals(ConfigApi.TIMEOUT_POR_DEFECTO, config.getTimeout());
    }

    @Test
    @DisplayName("La fábrica admite inyectar configuración, cliente y servicio")
    void admiteInyeccionCompleta() {
        ConfigApi configApi = new ConfigApi(ConfigApi.URL_POR_DEFECTO,
                ConfigApi.TIMEOUT_POR_DEFECTO);
        ServicioAlumnos servicio = new ServicioAlumnos(falso, Runnable::run, Runnable::run);

        FabricaAplicacion fabrica = new FabricaAplicacion(configApi, falso, servicio);

        assertSame(configApi, fabrica.getConfig());
        assertSame(falso, fabrica.getCliente());
        assertSame(servicio, fabrica.getServicio());
    }

    @Test
    @DisplayName("paraPruebas() usa el cliente inyectado y un servicio síncrono")
    void paraPruebasUsaElClienteInyectado() {
        FabricaAplicacion fabrica = FabricaAplicacion.paraPruebas(falso);
        try {
            assertSame(falso, fabrica.getCliente());
            ConsultaViewModel consulta = fabrica.crearConsultaViewModel();
            assertSame(falso, consulta.getServicio().getCliente());
            consulta.buscar();
            assertEquals(7, consulta.getAlumnos().size());
        } finally {
            fabrica.cerrar();
        }
    }

    @Test
    @DisplayName("Todos los viewmodels se crean compartiendo el mismo servicio")
    void creaTodosLosViewmodels() {
        FabricaAplicacion fabrica = FabricaAplicacion.paraPruebas(falso);
        try {
            ConsultaViewModel consulta = fabrica.crearConsultaViewModel();
            AltaViewModel alta = fabrica.crearAltaViewModel();
            EdicionViewModel edicion = fabrica.crearEdicionViewModel();
            BajaViewModel baja = fabrica.crearBajaViewModel();
            SaludViewModel salud = fabrica.crearSaludViewModel();

            assertNotNull(consulta);
            assertNotNull(alta);
            assertNotNull(edicion);
            assertNotNull(baja);
            assertNotNull(salud);

            assertSame(fabrica.getServicio(), consulta.getServicio());
            assertSame(fabrica.getServicio(), alta.getServicio());
            assertSame(fabrica.getServicio(), edicion.getServicio());
            assertSame(fabrica.getServicio(), baja.getServicio());
            assertSame(fabrica.getServicio(), salud.getServicio());
        } finally {
            fabrica.cerrar();
        }
    }
}
