package com.omni3.gestorhogwartsjavafx.api;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Alumno;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;
import com.omni3.gestorhogwartsjavafx.modelo.TipoAlumno;
import com.omni3.gestorhogwartsjavafx.util.ResultadoValidacion;
import com.omni3.gestorhogwartsjavafx.util.Validaciones;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Implementación de demostración de {@link ClienteAlumnos}: datos en
 * memoria, sin ninguna llamada de red (§3 del contrato).
 *
 * <p>Simula el comportamiento de la API real para poder ejercitar la UI
 * sin backend: latencia configurable, DNI duplicado (400), conflicto de
 * edición por versión (409), borrado lógico y {@code /health} con
 * identificador {@code R01}.</p>
 *
 * <p>Todos los métodos son {@code synchronized} porque la UI puede
 * consultar y modificar desde varios hilos de fondo.</p>
 */
public class ClienteAlumnosDemo implements ClienteAlumnos {

    /** Latencia simulada por defecto, en milisegundos. */
    public static final long LATENCIA_POR_DEFECTO = 150L;

    /** Instante fijo de las fichas sembradas (para tests deterministas). */
    private static final Instant MOMENTO_SEMILLA = Instant.parse("2026-01-15T10:00:00Z");

    /** Alumnos en memoria, indexados por DNI normalizado. */
    private final Map<String, AlumnoDto> alumnos = new LinkedHashMap<>();

    /** Conectores (fuentes) y su estado. */
    private final List<ConectorDto> conectores = new ArrayList<>();

    /** Latencia simulada por petición. */
    private long latenciaMs;

    /**
     * Crea el cliente con la latencia simulada por defecto.
     */
    public ClienteAlumnosDemo() {
        this(LATENCIA_POR_DEFECTO);
    }

    /**
     * Crea el cliente con una latencia simulada concreta.
     *
     * @param latenciaMs milisegundos de espera simulada por petición; 0 en tests
     */
    public ClienteAlumnosDemo(long latenciaMs) {
        this.latenciaMs = Math.max(0L, latenciaMs);
        sembrar();
    }

    /**
     * Fija la latencia simulada.
     *
     * @param latenciaMs milisegundos; los valores negativos se tratan como 0
     */
    public void setLatenciaMs(long latenciaMs) {
        this.latenciaMs = Math.max(0L, latenciaMs);
    }

    /**
     * Latencia simulada actual.
     *
     * @return milisegundos
     */
    public long getLatenciaMs() {
        return latenciaMs;
    }

    @Override
    public synchronized SaludDto salud() {
        dormir();
        return new SaludDto("OK", "R01",
                "API de demostración operativa", Instant.now().toString());
    }

    @Override
    public synchronized List<AlumnoDto> listarAlumnos(Origen origen, String q, int limit, int offset) {
        dormir();
        String texto = q == null ? "" : q.trim();
        String textoMayusculas = texto.toUpperCase(Locale.ROOT);
        List<AlumnoDto> resultado = new ArrayList<>();
        for (AlumnoDto alumno : alumnos.values()) {
            if (!alumno.activo()) {
                continue;
            }
            if (origen != null && !origen.esCentral() && !origen.name().equalsIgnoreCase(alumno.origen())) {
                continue;
            }
            if (!texto.isEmpty()
                    && !alumno.dni().toUpperCase(Locale.ROOT).contains(textoMayusculas)
                    && !alumno.nombre().toUpperCase(Locale.ROOT).contains(textoMayusculas)) {
                continue;
            }
            resultado.add(alumno);
        }
        int desde = Math.max(0, offset);
        if (desde >= resultado.size()) {
            return List.of();
        }
        int hasta = limit > 0 ? Math.min(resultado.size(), desde + limit) : resultado.size();
        return List.copyOf(resultado.subList(desde, hasta));
    }

    @Override
    public synchronized AlumnoDto obtenerAlumno(String dni) {
        dormir();
        AlumnoDto alumno = alumnos.get(clave(dni));
        if (alumno == null) {
            throw noEncontrado(dni);
        }
        return alumno;
    }

    @Override
    public synchronized AlumnoDto altaAlumno(AlumnoDto alumno) {
        dormir();
        if (alumno == null) {
            throw new ExcepcionApi(400, "VALIDACION", "El alumno es obligatorio.", peticion());
        }
        String dni = Validaciones.normalizarDNI(alumno.dni());
        TipoAlumno tipo = TipoAlumno.desdeTexto(alumno.tipo()).orElse(TipoAlumno.NUEVO);
        Origen origen = Origen.desdeTexto(alumno.origen()).orElse(Origen.MARIADB);
        ResultadoValidacion validacion =
                Validaciones.validarAlumno(dni, alumno.nombre(), alumno.casa(), alumno.patronus(), tipo);
        if (!validacion.esValido()) {
            throw new ExcepcionApi(400, "VALIDACION",
                    validacion.mensajeError().orElse("Datos de alumno no válidos"), peticion());
        }
        if (alumnos.containsKey(dni)) {
            throw new ExcepcionApi(400, "DNI_DUPLICADO",
                    "Ya existe un alumno con el DNI " + Validaciones.enmascararDNI(dni) + ".",
                    peticion());
        }
        Alumno nuevo = new Alumno(dni, alumno.nombre(), alumno.casa(), alumno.patronus(),
                origen, tipo, 1L, Instant.now(), true);
        return guardar(nuevo);
    }

    @Override
    public synchronized AlumnoDto editarAlumno(String dni, AlumnoDto alumno) {
        dormir();
        if (alumno == null) {
            throw new ExcepcionApi(400, "VALIDACION", "El alumno es obligatorio.", peticion());
        }
        String clave = clave(dni);
        AlumnoDto almacenado = alumnos.get(clave);
        if (almacenado == null) {
            throw noEncontrado(dni);
        }
        Alumno actual = almacenado.aModelo();
        long versionPrevia = alumno.version();
        if (versionPrevia != actual.version()) {
            throw new ExcepcionApi(409, "CONFLICTO_VERSION",
                    "Conflicto de concurrencia: la versión previa " + versionPrevia
                            + " no coincide con la versión actual " + actual.version()
                            + ". Recargue la ficha antes de volver a editar.",
                    peticion());
        }
        ResultadoValidacion validacion = Validaciones.validarAlumno(
                clave, alumno.nombre(), alumno.casa(), alumno.patronus(), actual.tipo());
        if (!validacion.esValido()) {
            throw new ExcepcionApi(400, "VALIDACION",
                    validacion.mensajeError().orElse("Datos de alumno no válidos"), peticion());
        }
        Alumno editado = new Alumno(clave, alumno.nombre(), alumno.casa(), alumno.patronus(),
                actual.origen(), actual.tipo(), actual.version() + 1L, Instant.now(), actual.activo());
        return guardar(editado);
    }

    @Override
    public synchronized void bajarAlumno(String dni) {
        dormir();
        String clave = clave(dni);
        AlumnoDto almacenado = alumnos.get(clave);
        if (almacenado == null) {
            throw noEncontrado(dni);
        }
        Alumno actual = almacenado.aModelo();
        Alumno deBaja = actual.conActivo(false)
                .conVersion(actual.version() + 1L)
                .conActualizadoEn(Instant.now());
        guardar(deBaja);
    }

    @Override
    public synchronized List<ConectorDto> listarConectores() {
        dormir();
        return List.copyOf(conectores);
    }

    @Override
    public synchronized String sincronizar() {
        dormir();
        return "Sincronización completada (modo demo): 0 elementos pendientes.";
    }

    @Override
    public synchronized List<Map<String, Object>> colaRefrescos() {
        dormir();
        List<Map<String, Object>> cola = new ArrayList<>();
        cola.add(Map.<String, Object>of("ruta", "/students?origen=DERBY", "pendientes", 0));
        cola.add(Map.<String, Object>of("ruta", "/students?origen=MARIADB", "pendientes", 0));
        return List.copyOf(cola);
    }

    /**
     * Número de alumnos en memoria, contando también los de baja lógica.
     *
     * @return total de fichas almacenadas
     */
    public synchronized int totalAlumnos() {
        return alumnos.size();
    }

    /**
     * Vuelve a los datos de demostración originales.
     */
    public synchronized void reiniciar() {
        alumnos.clear();
        conectores.clear();
        sembrar();
    }

    /** Rellena el alumnado histórico de varias bases y los nuevos de MariaDB. */
    private void sembrar() {
        guardar(new Alumno("12345678A", "Harry Potter", "Gryffindor", "Ciervo",
                Origen.DERBY, TipoAlumno.HISTORICO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("23456789B", "Hermione Granger", "Gryffindor", "Otaria",
                Origen.HSQLDB, TipoAlumno.HISTORICO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("34567890C", "Draco Malfoy", "Slytherin", "Serpiente",
                Origen.H2, TipoAlumno.HISTORICO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("45678901D", "Luna Lovegood", "Ravenclaw", "Conejo",
                Origen.ORACLE, TipoAlumno.HISTORICO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("56789012E", "Cedric Diggory", "Hufflepuff", "Fenix",
                Origen.SQLITE, TipoAlumno.HISTORICO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("67890123F", "Maria Rodriguez", null, "Gato",
                Origen.MARIADB, TipoAlumno.NUEVO, 1L, MOMENTO_SEMILLA, true));
        guardar(new Alumno("78901234G", "Carlos Ruiz", null, "Lobo",
                Origen.MARIADB, TipoAlumno.NUEVO, 1L, MOMENTO_SEMILLA, true));

        conectores.add(new ConectorDto("DERBY", "OK", "Conector Derby operativo", MOMENTO_SEMILLA.toString()));
        conectores.add(new ConectorDto("HSQLDB", "OK", "Conector HSQLDB operativo", MOMENTO_SEMILLA.toString()));
        conectores.add(new ConectorDto("H2", "DEGRADADO", "Respuestas lentas del conector H2", MOMENTO_SEMILLA.toString()));
        conectores.add(new ConectorDto("ORACLE", "OK", "Conector Oracle operativo", MOMENTO_SEMILLA.toString()));
        conectores.add(new ConectorDto("MARIADB", "OK", "Conector MariaDB operativo", MOMENTO_SEMILLA.toString()));
        conectores.add(new ConectorDto("SQLITE", "CAIDO", "No responde en el puerto 9092", MOMENTO_SEMILLA.toString()));
    }

    /** Almacena el alumno y devuelve su DTO. */
    private AlumnoDto guardar(Alumno alumno) {
        AlumnoDto dto = AlumnoDto.de(alumno);
        alumnos.put(dto.dni(), dto);
        return dto;
    }

    /** Clave de búsqueda: DNI normalizado. */
    private String clave(String dni) {
        String normalizado = Validaciones.normalizarDNI(dni);
        if (normalizado == null || normalizado.isEmpty()) {
            throw new ExcepcionApi(400, "VALIDACION", "El DNI es obligatorio.", peticion());
        }
        return normalizado;
    }

    /** Error 404 con el DNI enmascarado. */
    private ExcepcionApi noEncontrado(String dni) {
        return new ExcepcionApi(404, "NO_ENCONTRADO",
                "No existe ningún alumno con el DNI " + Validaciones.enmascararDNI(dni) + ".",
                peticion());
    }

    /** Simula la latencia de red de la API real. */
    private void dormir() {
        if (latenciaMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(latenciaMs);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
        }
    }

    /** Identificador de petición simulado, igual que el de la API real. */
    private String peticion() {
        return UUID.randomUUID().toString();
    }
}
