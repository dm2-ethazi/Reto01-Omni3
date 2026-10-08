package com.omni3.gestorhogwartsjavafx.servicio;

import com.omni3.gestorhogwartsjavafx.api.ClienteAlumnos;
import com.omni3.gestorhogwartsjavafx.api.ExcepcionApi;
import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import javafx.application.Platform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de fondo que envuelve a {@link ClienteAlumnos} y ejecuta cada
 * peticion en un hilo aparte, entregando el resultado mediante callbacks
 * funcionales (§8 del contrato).
 *
 * <p><strong>Nunca bloquea el hilo FX:</strong> la llamada al cliente se
 * despacha en un {@link ExecutorService} y el
 * llamante recibe la respuesta por {@link CallbackExito} o
 * {@link CallbackFallo}. La logica de red jamas toca JavaFX: la publicacion
 * de resultados se hace exclusivamente desde este servicio, con el
 * publicador inyectado ({@code Platform.runLater} en produccion).</p>
 *
 * <p>La clase es inyectable para las pruebas: se le puede sustituir el
 * executor de fondo y el publicador, de modo que un test puede trabajar de
 * forma sincrona sin arrancar el toolkit JavaFX.</p>
 */
public class ServicioAlumnos {

    /** Cliente de acceso a la API envuelto por este servicio. */
    private final ClienteAlumnos cliente;

    /** Executor de fondo donde corren las peticiones. */
    private final Executor fondo;

    /** Executor que publica los callbacks (en produccion, {@code Platform.runLater}). */
    private final Executor publicador;

    /** {@code true} si el servicio creo su propio pool y debe cerrarlo. */
    private final boolean propietarioDelFondo;

    /**
     * Crea un servicio con pool de fondo propio y publicacion en el hilo
     * FX ({@code Platform.runLater}).
     *
     * @param cliente cliente de acceso a la API; no nulo
     * @throws NullPointerException si {@code cliente} es nulo
     */
    public ServicioAlumnos(ClienteAlumnos cliente) {
        this(cliente, poolPropio(), Platform::runLater, true);
    }

    /**
     * Crea un servicio con pool de fondo propio y un publicador propio
     * (util en pruebas, donde no hay toolkit JavaFX).
     *
     * @param cliente    cliente de acceso a la API; no nulo
     * @param publicador executor que recibe los callbacks; no nulo
     * @throws NullPointerException si alguno de los argumentos es nulo
     */
    public ServicioAlumnos(ClienteAlumnos cliente, Executor publicador) {
        this(cliente, poolPropio(), publicador, true);
    }

    /**
     * Crea un servicio con todo inyectado: sin pool propio ni publicacion
     * FX. Pensado para pruebas deterministicas (executors directos).
     *
     * @param cliente    cliente de acceso a la API; no nulo
     * @param fondo      executor de las peticiones; no nulo
     * @param publicador executor que recibe los callbacks; no nulo
     * @throws NullPointerException si alguno de los argumentos es nulo
     */
    public ServicioAlumnos(ClienteAlumnos cliente, Executor fondo, Executor publicador) {
        this(cliente, fondo, publicador, false);
    }

    /** Constructor completo interno. */
    private ServicioAlumnos(ClienteAlumnos cliente, Executor fondo, Executor publicador,
                            boolean propietarioDelFondo) {
        this.cliente = Objects.requireNonNull(cliente, "El cliente es obligatorio");
        this.fondo = Objects.requireNonNull(fondo, "El executor de fondo es obligatorio");
        this.publicador = Objects.requireNonNull(publicador, "El publicador es obligatorio");
        this.propietarioDelFondo = propietarioDelFondo;
    }

    /** Crea el pool de fondo con hilos daemon y nombre. */
    private static ExecutorService poolPropio() {
        AtomicInteger contador = new AtomicInteger();
        return Executors.newFixedThreadPool(2, tarea -> {
            Thread hilo = new Thread(tarea, "omni3-servicio-alumnos-" + contador.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        });
    }

    /**
     * Cliente de acceso a la API subyacente.
     *
     * @return el cliente envuelto; nunca {@code null}
     */
    public ClienteAlumnos getCliente() {
        return cliente;
    }

    /**
     * Consulta de salud ({@code GET /health}).
     *
     * @param exito callback de exito
     * @param fallo callback de fallo
     */
    public void salud(CallbackExito<SaludDto> exito, CallbackFallo fallo) {
        ejecutarEnFondo(cliente::salud, exito, fallo);
    }

    /**
     * Consulta unificada de alumnado ({@code GET /students}).
     *
     * @param origen origen a filtrar; {@code null} o {@link Origen#CENTRAL} para todas las fuentes
     * @param q      texto de busqueda; vacio para todo
     * @param limit  maximo de filas; {@code <= 0} sin limite
     * @param offset filas a saltar; {@code <= 0} sin salto
     * @param exito  callback de exito
     * @param fallo  callback de fallo
     */
    public void listarAlumnos(Origen origen, String q, int limit, int offset,
                              CallbackExito<List<AlumnoDto>> exito, CallbackFallo fallo) {
        ejecutarEnFondo(() -> cliente.listarAlumnos(origen, q, limit, offset), exito, fallo);
    }

    /**
     * Ficha de un alumno ({@code GET /students/{dni}}).
     *
     * @param dni   DNI del alumno
     * @param exito callback de exito
     * @param fallo callback de fallo
     */
    public void obtenerAlumno(String dni, CallbackExito<AlumnoDto> exito, CallbackFallo fallo) {
        ejecutarEnFondo(() -> cliente.obtenerAlumno(dni), exito, fallo);
    }

    /**
     * Alta de alumno nuevo ({@code POST /students}).
     *
     * @param alumno datos del alumno
     * @param exito  callback de exito
     * @param fallo  callback de fallo
     */
    public void altaAlumno(AlumnoDto alumno, CallbackExito<AlumnoDto> exito, CallbackFallo fallo) {
        ejecutarEnFondo(() -> cliente.altaAlumno(alumno), exito, fallo);
    }

    /**
     * Edicion de alumno enviando el estado previo ({@code PUT /students/{dni}}).
     *
     * @param dni    DNI del alumno
     * @param alumno datos nuevos; su {@code version} es el estado previo
     * @param exito  callback de exito
     * @param fallo  callback de fallo
     */
    public void editarAlumno(String dni, AlumnoDto alumno, CallbackExito<AlumnoDto> exito,
                             CallbackFallo fallo) {
        ejecutarEnFondo(() -> cliente.editarAlumno(dni, alumno), exito, fallo);
    }

    /**
     * Baja logica de un alumno ({@code DELETE /students/{dni}}).
     *
     * @param dni   DNI del alumno
     * @param exito callback de exito, sin resultado
     * @param fallo callback de fallo
     */
    public void bajarAlumno(String dni, Runnable exito, CallbackFallo fallo) {
        Objects.requireNonNull(exito, "El callback de exito es obligatorio");
        ejecutarEnFondo(() -> {
            cliente.bajarAlumno(dni);
            return null;
        }, resultado -> exito.run(), fallo);
    }

    /**
     * Fuentes de datos y su estado ({@code GET /connectors}).
     *
     * @param exito callback de exito
     * @param fallo callback de fallo
     */
    public void listarConectores(CallbackExito<List<ConectorDto>> exito, CallbackFallo fallo) {
        ejecutarEnFondo(cliente::listarConectores, exito, fallo);
    }

    /**
     * Dispara la sincronizacion ({@code POST /sync}).
     *
     * @param exito callback de exito con el mensaje de la operacion
     * @param fallo callback de fallo
     */
    public void sincronizar(CallbackExito<String> exito, CallbackFallo fallo) {
        ejecutarEnFondo(cliente::sincronizar, exito, fallo);
    }

    /**
     * Cola de refrescos pendientes ({@code GET /admin/refresh-queue}).
     *
     * @param exito callback de exito
     * @param fallo callback de fallo
     */
    public void colaRefrescos(CallbackExito<List<Map<String, Object>>> exito, CallbackFallo fallo) {
        ejecutarEnFondo(cliente::colaRefrescos, exito, fallo);
    }

    /**
     * Cierra el pool de fondo si lo creo este servicio. Las peticiones
     * posteriores fallan con codigo {@code SERVICIO_CERRADO}.
     */
    public void cerrar() {
        if (!propietarioDelFondo || !(fondo instanceof ExecutorService servicio)) {
            return;
        }
        servicio.shutdown();
        try {
            servicio.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Despacha la tarea en el hilo de fondo y publica el resultado o el
     * error en el publicador. Es el unico punto que publica callbacks, y lo
     * hace siempre fuera de la logica de red (§8 del contrato).
     *
     * @param <T>   tipo de resultado
     * @param tarea operacion sincrona contra el cliente
     * @param exito callback de exito
     * @param fallo callback de fallo
     */
    private <T> void ejecutarEnFondo(Callable<T> tarea, CallbackExito<T> exito, CallbackFallo fallo) {
        Objects.requireNonNull(exito, "El callback de exito es obligatorio");
        Objects.requireNonNull(fallo, "El callback de fallo es obligatorio");
        try {
            fondo.execute(() -> {
                T resultado;
                try {
                    resultado = tarea.call();
                } catch (ExcepcionApi error) {
                    publicar(() -> fallo.alFallo(error));
                    return;
                } catch (Exception inesperado) {
                    publicar(() -> fallo.alFallo(new ExcepcionApi(0, "FALLO_INESPERADO",
                            "Error inesperado al consultar la API: " + mensajeDe(inesperado), null)));
                    return;
                }
                publicar(() -> exito.alExito(resultado));
            });
        } catch (RejectedExecutionException rechazada) {
            publicar(() -> fallo.alFallo(new ExcepcionApi(0, "SERVICIO_CERRADO",
                    "El servicio de acceso a la API esta cerrado.", null)));
        }
    }

    /** Publica una tarea en el hilo que corresponda (FX en produccion). */
    private void publicar(Runnable tarea) {
        publicador.execute(tarea);
    }

    /** Mensaje seguro de una excepcion no prevista. */
    private static String mensajeDe(Exception excepcion) {
        String mensaje = excepcion.getMessage();
        return mensaje == null || mensaje.isBlank()
                ? excepcion.getClass().getSimpleName()
                : mensaje;
    }
}
