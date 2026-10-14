package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnos;
import com.omni3.gestorhogwartsjavafx.api.ConfigApi;
import com.omni3.gestorhogwartsjavafx.viewmodel.AltaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.BajaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.ConsultaViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.EdicionViewModel;
import com.omni3.gestorhogwartsjavafx.viewmodel.SaludViewModel;

import java.util.Objects;

/**
 * Fabrica unica de la aplicacion: crea la configuracion, el cliente de
 * acceso a la API, el {@link ServicioAlumnos} de fondo y los viewmodels
 * de cada pantalla (§3 del contrato).
 *
 * <p>La aplicacion trabaja siempre contra la API real: no existe ningun
 * conmutador de datos ni modo alternativo, y la vista solo ve los
 * viewmodels.</p>
 *
 * <p>Todos los componentes son sustituibles: para pruebas se usa
 * {@link #paraPruebas(ClienteAlumnos)}, que admite cualquier
 * {@code ClienteAlumnos} (por ejemplo un doble de pruebas con latencia 0)
 * y un servicio sincrono que no necesita toolkit JavaFX.</p>
 */
public class FabricaAplicacion {

    /** Configuracion (URL base y timeout) de la fabrica. */
    private ConfigApi config;

    /** Servicio de fondo compartido por todos los viewmodels. */
    private final ServicioAlumnos servicio;

    /**
     * Crea la fabrica por defecto con la URL del entorno (propiedad
     * {@code gestor.api.url}, variable {@code GESTOR_API_URL} o valor por
     * defecto) y monta el cliente HTTP y el servicio de fondo.
     */
    public FabricaAplicacion() {
        this(ConfigApi.desdeEntorno());
    }

    /**
     * Crea la fabrica con una configuracion concreta.
     *
     * @param config configuracion con URL y timeout; no nula
     * @throws NullPointerException si la configuracion es nula
     */
    public FabricaAplicacion(ConfigApi config) {
        this(config, crearServicio(config));
    }

    /**
     * Crea la fabrica con un servicio ya montado (el cliente se toma del
     * propio servicio). Util para inyectar un servicio en pruebas.
     *
     * @param config  configuracion con URL y timeout; no nula
     * @param servicio servicio de fondo; no nulo
     * @throws NullPointerException si alguno de los argumentos es nulo
     */
    public FabricaAplicacion(ConfigApi config, ServicioAlumnos servicio) {
        this(config, servicio == null ? null : servicio.getCliente(), servicio);
    }

    /**
     * Crea la fabrica con todos sus componentes inyectados.
     *
     * @param config   configuracion con URL y timeout; no nula
     * @param cliente  cliente de acceso a la API; no nulo
     * @param servicio servicio de fondo; no nulo
     * @throws NullPointerException si alguno de los argumentos es nulo
     */
    public FabricaAplicacion(ConfigApi config, ClienteAlumnos cliente, ServicioAlumnos servicio) {
        this.config = Objects.requireNonNull(config, "La configuracion es obligatoria");
        this.servicio = Objects.requireNonNull(servicio, "El servicio es obligatorio");
        Objects.requireNonNull(cliente, "El cliente es obligatorio");
        if (this.servicio.getCliente() != cliente) {
            throw new IllegalArgumentException(
                    "El cliente y el servicio deben coincidir: el servicio envuelve al cliente");
        }
    }

    /**
     * Fabrica de prueba: cliente inyectado y servicio sincrono (sin pool
     * ni publicacion FX), ideal para tests deterministas.
     *
     * @param cliente cliente de acceso; por ejemplo un doble de pruebas con latencia 0
     * @return fabrica lista para usar en pruebas
     * @throws NullPointerException si el cliente es nulo
     */
    public static FabricaAplicacion paraPruebas(ClienteAlumnos cliente) {
        ConfigApi config = new ConfigApi(ConfigApi.URL_POR_DEFECTO,
                ConfigApi.TIMEOUT_POR_DEFECTO);
        ServicioAlumnos servicio = new ServicioAlumnos(cliente, Runnable::run, Runnable::run);
        return new FabricaAplicacion(config, servicio);
    }

    /** Monta el servicio por defecto (pool de fondo + publicacion FX). */
    private static ServicioAlumnos crearServicio(ConfigApi config) {
        Objects.requireNonNull(config, "La configuracion es obligatoria");
        return new ServicioAlumnos(ClienteAlumnos.desde(config));
    }

    /**
     * Configuracion de la aplicacion.
     *
     * @return configuracion con URL y timeout
     */
    public ConfigApi getConfig() {
        return config;
    }

    /**
     * Servicio de fondo compartido.
     *
     * @return servicio; nunca {@code null}
     */
    public ServicioAlumnos getServicio() {
        return servicio;
    }

    /**
     * Cliente de acceso a la API subyacente.
     *
     * @return cliente; nunca {@code null}
     */
    public ClienteAlumnos getCliente() {
        return servicio.getCliente();
    }

    /**
     * Crea el viewmodel de la pantalla de consulta unificada.
     *
     * @return viewmodel de consulta
     */
    public ConsultaViewModel crearConsultaViewModel() {
        return new ConsultaViewModel(servicio);
    }

    /**
     * Crea el viewmodel de la pantalla de alta de alumno.
     *
     * @return viewmodel de alta
     */
    public AltaViewModel crearAltaViewModel() {
        return new AltaViewModel(servicio);
    }

    /**
     * Crea el viewmodel de la pantalla de edicion de alumno.
     *
     * @return viewmodel de edicion
     */
    public EdicionViewModel crearEdicionViewModel() {
        return new EdicionViewModel(servicio);
    }

    /**
     * Crea el viewmodel de la pantalla de baja de alumno.
     *
     * @return viewmodel de baja
     */
    public BajaViewModel crearBajaViewModel() {
        return new BajaViewModel(servicio);
    }

    /**
     * Crea el viewmodel de la pantalla de estado del sistema.
     *
     * @return viewmodel de salud
     */
    public SaludViewModel crearSaludViewModel() {
        return new SaludViewModel(servicio);
    }

    /**
     * Cierra el pool de fondo del servicio. La fabrica queda inservible
     * despues de llamar a este metodo.
     */
    public void cerrar() {
        servicio.cerrar();
    }
}
