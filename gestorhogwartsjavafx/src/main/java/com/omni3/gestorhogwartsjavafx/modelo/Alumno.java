package com.omni3.gestorhogwartsjavafx.modelo;

import com.omni3.gestorhogwartsjavafx.util.ResultadoValidacion;
import com.omni3.gestorhogwartsjavafx.util.Validaciones;

import java.time.Instant;
import java.util.Objects;

/**
 * Alumno inmutable del dominio (§4 del contrato).
 *
 * <p>La clase es inmutable: cualquier modificación produce una instancia
 * nueva mediante los métodos {@code conX}. El constructor valida los
 * datos con {@link Validaciones} y normaliza el DNI a mayúsculas y sin
 * espacios.</p>
 *
 * @param dni            DNI exactamente de 9 caracteres ({@code ^[0-9]{8}[A-Z]$})
 * @param nombre         nombre y apellidos, 2–80 caracteres
 * @param casa           casa de Hogwarts; {@code null} en alumnos nuevos
 * @param patronus       patronus; obligatorio en alumnos nuevos
 * @param origen         fuente de la que procede el alumno
 * @param tipo           {@link TipoAlumno#HISTORICO} o {@link TipoAlumno#NUEVO}
 * @param version        versión del registro, estado previo para concurrencia optimista
 * @param actualizadoEn  instante de la última actualización
 * @param activo         borrado lógico: {@code false} cuando se ha dado de baja
 * @author jairoben
 */
public record Alumno(
        String dni,
        String nombre,
        String casa,
        String patronus,
        Origen origen,
        TipoAlumno tipo,
        long version,
        Instant actualizadoEn,
        boolean activo) {

    /**
     * Constructor con validación de dominio.
     *
     * @param dni            DNI del alumno (se normaliza a mayúsculas y sin espacios)
     * @param nombre         nombre y apellidos
     * @param casa           casa; {@code null} en alumnos nuevos
     * @param patronus       patronus; obligatorio en alumnos nuevos
     * @param origen         fuente de origen
     * @param tipo           tipo de alumno
     * @param version        versión del registro (de ser menor que 1 se usa 1)
     * @param actualizadoEn  instante de actualización; si es {@code null} se usa {@link Instant#now()}
     * @param activo         estado lógico del alumno
     * @throws IllegalArgumentException si los datos no superan las validaciones del §4
     * @throws NullPointerException     si {@code origen} o {@code tipo} son {@code null}
     */
    public Alumno {
        Objects.requireNonNull(origen, "El origen es obligatorio");
        Objects.requireNonNull(tipo, "El tipo de alumno es obligatorio");
        dni = Validaciones.normalizarDNI(dni);
        nombre = nombre == null ? null : nombre.trim();
        casa = casa == null ? null : casa.trim();
        patronus = patronus == null ? null : patronus.trim();
        ResultadoValidacion validacion =
                Validaciones.validarAlumno(dni, nombre, casa, patronus, tipo);
        if (!validacion.esValido()) {
            throw new IllegalArgumentException(validacion.mensajeError().orElse("Datos de alumno no válidos"));
        }
        version = Math.max(1L, version);
        if (actualizadoEn == null) {
            actualizadoEn = Instant.now();
        }
    }

    /**
     * Atajo para crear un alumno recién dado de alta (versión 1,
     * actualizado ahora y activo).
     *
     * @param dni       DNI del alumno
     * @param nombre    nombre y apellidos
     * @param casa      casa; {@code null} en alumnos nuevos
     * @param patronus  patronus; obligatorio en alumnos nuevos
     * @param origen    fuente de origen
     * @param tipo      tipo de alumno
     * @return alumno con versión 1, activo y actualizado en este instante
     */
    public static Alumno deAlta(String dni, String nombre, String casa, String patronus,
                                Origen origen, TipoAlumno tipo) {
        return new Alumno(dni, nombre, casa, patronus, origen, tipo, 1L, Instant.now(), true);
    }

    /**
     * Devuelve una copia con otra versión de concurrencia.
     *
     * @param nuevaVersion versión del registro
     * @return copia con la versión indicada
     */
    public Alumno conVersion(long nuevaVersion) {
        return new Alumno(dni, nombre, casa, patronus, origen, tipo, nuevaVersion, actualizadoEn, activo);
    }

    /**
     * Devuelve una copia con otro instante de actualización.
     *
     * @param momento nuevo instante
     * @return copia con el instante indicado
     */
    public Alumno conActualizadoEn(Instant momento) {
        return new Alumno(dni, nombre, casa, patronus, origen, tipo, version, momento, activo);
    }

    /**
     * Devuelve una copia con otro estado de borrado lógico.
     *
     * @param nuevoActivo nuevo valor de {@code activo}
     * @return copia con el estado indicado
     */
    public Alumno conActivo(boolean nuevoActivo) {
        return new Alumno(dni, nombre, casa, patronus, origen, tipo, version, actualizadoEn, nuevoActivo);
    }

    /**
     * Devuelve una copia con otro nombre.
     *
     * @param nuevoNombre nuevo nombre
     * @return copia con el nombre indicado
     */
    public Alumno conNombre(String nuevoNombre) {
        return new Alumno(dni, nuevoNombre, casa, patronus, origen, tipo, version, actualizadoEn, activo);
    }

    /**
     * Devuelve una copia con otra casa.
     *
     * @param nuevaCasa nueva casa (o {@code null} si procede)
     * @return copia con la casa indicada
     */
    public Alumno conCasa(String nuevaCasa) {
        return new Alumno(dni, nombre, nuevaCasa, patronus, origen, tipo, version, actualizadoEn, activo);
    }

    /**
     * Devuelve una copia con otro patronus.
     *
     * @param nuevoPatronus nuevo patronus (o {@code null} si procede)
     * @return copia con el patronus indicado
     */
    public Alumno conPatronus(String nuevoPatronus) {
        return new Alumno(dni, nombre, casa, nuevoPatronus, origen, tipo, version, actualizadoEn, activo);
    }

    /**
     * Indica si el alumno está activo (no dado de baja lógicamente).
     *
     * @return {@code true} si está activo
     */
    public boolean estaActivo() {
        return activo;
    }

    /**
     * Representación textual segura para logs: el DNI aparece
     * enmascarado ({@code 12***45A}), nunca completo.
     *
     * @return descripción del alumno con el DNI enmascarado
     */
    @Override
    public String toString() {
        return "Alumno{dni=" + Validaciones.enmascararDNI(dni)
                + ", nombre='" + nombre + '\''
                + ", casa='" + casa + '\''
                + ", patronus='" + patronus + '\''
                + ", origen=" + origen
                + ", tipo=" + tipo
                + ", version=" + version
                + ", actualizadoEn=" + actualizadoEn
                + ", activo=" + activo
                + '}';
    }
}
