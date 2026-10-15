package com.omni3.gestorhogwartsjavafx.api;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.PatronusDto;
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
 * Doble de prueba de {@link ClienteAlumnos}: datos en memoria, sin
 * ninguna llamada de red. Existe <strong>solo en el classpath de
 * pruebas</strong>; la aplicación usa exclusivamente
 * {@link ClienteAlumnosHttp}.
 *
 * <p>Simula el comportamiento de la API real para poder ejercitar los
 * servicios y viewmodels sin backend: latencia configurable, DNI
 * duplicado (409, igual que la API real), conflicto de edición por
 * versión (409), borrado lógico y salud con identificador
 * {@code R01}.</p>
 *
 * <p>Todos los métodos son {@code synchronized} porque las pruebas
 * pueden consultar y modificar desde varios hilos de fondo.</p>
 */
public class ClienteAlumnosFalso implements ClienteAlumnos {

    /** Latencia simulada por defecto, en milisegundos. */
    public static final long LATENCIA_POR_DEFECTO = 150L;

    /** Instante fijo de las fichas sembradas (para tests deterministas). */
    private static final Instant MOMENTO_SEMILLA = Instant.parse("2026-01-15T10:00:00Z");

    /** Alumnos en memoria, indexados por DNI normalizado. */
    private final Map<String, AlumnoDto> alumnos = new LinkedHashMap<>();

    /** Catálogo de patronus simulado (subconjunto del real, en 3 idiomas). */
    private static final List<PatronusDto> CATALOGO = List.of(
            new PatronusDto("Ciervo", "Stag", "Orein",
                    "https://upload.wikimedia.org/wikipedia/commons/4/4b/Cervus_elaphus_Luc_Viatour_6.jpg"),
            new PatronusDto("Lobo", "Wolf", "Otso",
                    "https://upload.wikimedia.org/wikipedia/commons/6/68/Eurasian_wolf_2.jpg"),
            new PatronusDto("Conejo", "Rabbit", "Untxi",
                    "https://upload.wikimedia.org/wikipedia/commons/1/1f/Oryctolagus_cuniculus_Rcdo.jpg"),
            new PatronusDto("Nutria", "Otter", "Igaraba",
                    "https://upload.wikimedia.org/wikipedia/commons/d/d3/Fischotter%2C_Lutra_Lutra.JPG"),
            new PatronusDto("Lechuza Común", "Barn Owl", "Ontza",
                    "https://upload.wikimedia.org/wikipedia/commons/9/9e/American_Barn_Owl_%28Tyto_furcata_guatemalae%29%2C_Orange_Walk.jpg"),
            new PatronusDto("Fénix", "Phoenix", "Fenixe",
                    "https://upload.wikimedia.org/wikipedia/commons/c/c4/Phoenix_%281583%29.svg"),
            new PatronusDto("Serpiente de hierba", "Grass Snake", "Belar suge",
                    "https://upload.wikimedia.org/wikipedia/commons/0/0c/Grass_snake_%28Natrix_natrix%29_Pieniny.jpg"),
            new PatronusDto("Gato banyano", "Balinese", "Balinese",
                    "https://upload.wikimedia.org/wikipedia/commons/0/05/Old-Style_Balinese_Cat.png"),
            new PatronusDto("Basset Hound", "Basset Hound", "Basset Hound",
                    "https://upload.wikimedia.org/wikipedia/commons/c/cf/BassetHound_profil.jpg"),
            new PatronusDto("Erumpent", "Erumpent", "Erumpent",
                    "https://static.wikia.nocookie.net/harrypotter/images/6/61/Erumpent_Concept_Art_FB1.png/revision/latest"),
            new PatronusDto("Occamy", "Occamy", "Occamy",
                    "https://static.wikia.nocookie.net/harrypotter/images/2/29/Occamy_FBCases.png/revision/latest"),
            new PatronusDto("Thestral", "Thestral", "Thestral",
                    "https://static.wikia.nocookie.net/harrypotter/images/b/bd/Thestral.png/revision/latest"));

    /** Latencia simulada por petición. */
    private long latenciaMs;

    /**
     * Crea el cliente con la latencia simulada por defecto.
     */
    public ClienteAlumnosFalso() {
        this(LATENCIA_POR_DEFECTO);
    }

    /**
     * Crea el cliente con una latencia simulada concreta.
     *
     * @param latenciaMs milisegundos de espera simulada por petición; 0 en tests
     */
    public ClienteAlumnosFalso(long latenciaMs) {
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
                "API de pruebas operativa", Instant.now().toString());
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
            throw new ExcepcionApi(409, "DNI_DUPLICADO",
                    "El DNI '" + Validaciones.enmascararDNI(dni) + "' ya existe en la fuente del alumno.",
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
    public synchronized List<PatronusDto> listarPatronus() {
        dormir();
        return CATALOGO;
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
     * Vuelve a los datos de prueba originales.
     */
    public synchronized void reiniciar() {
        alumnos.clear();
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
