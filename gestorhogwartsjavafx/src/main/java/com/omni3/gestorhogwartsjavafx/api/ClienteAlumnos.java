package com.omni3.gestorhogwartsjavafx.api;

import com.omni3.gestorhogwartsjavafx.api.dto.AlumnoDto;
import com.omni3.gestorhogwartsjavafx.api.dto.ConectorDto;
import com.omni3.gestorhogwartsjavafx.api.dto.SaludDto;
import com.omni3.gestorhogwartsjavafx.modelo.Origen;

import java.util.List;
import java.util.Map;

/**
 * Interfaz única de acceso a los datos de alumnado (§3 del contrato).
 * La usan en exclusiva los servicios y los viewmodels: la UI nunca
 * habla de red directamente.
 *
 * <p><strong>Todos los métodos son síncronos</strong>: no lanzan hilos
 * ni usan {@code Platform.runLater}. El llamante decide el hilo de
 * ejecución y el hilo en el que consume el resultado (desde JavaFX nunca
 * se llama desde el hilo FX, §8).</p>
 *
 * <p>Cualquier fallo —validación, DNI inexistente, conflicto de
 * concurrencia, error HTTP o de red— se comunica con
 * {@link ExcepcionApi}, que lleva código, mensaje en español,
 * {@code request-id} y estado HTTP.</p>
 */
public interface ClienteAlumnos {

    /**
     * Estado del sistema ({@code GET /health}), con identificador {@code R01}.
     *
     * @return salud actual del sistema
     * @throws ExcepcionApi si la consulta falla
     */
    SaludDto salud();

    /**
     * Consulta unificada de alumnado ({@code GET /students}).
     *
     * @param origen origen a filtrar; {@code null} o {@link Origen#CENTRAL}
     *               para todas las fuentes
     * @param q      texto de búsqueda sobre DNI o nombre; vacío para todo
     * @param limit  máximo de filas a devolver; {@code <= 0} sin límite
     * @param offset número de filas a saltar; {@code <= 0} sin salto
     * @return alumnos activos que cumplen el filtro, mostrando siempre la fuente de origen
     * @throws ExcepcionApi si la consulta falla
     */
    List<AlumnoDto> listarAlumnos(Origen origen, String q, int limit, int offset);

    /**
     * Ficha de un alumno ({@code GET /students/{dni}}).
     *
     * @param dni DNI del alumno (se normaliza antes de buscar)
     * @return la ficha
     * @throws ExcepcionApi con status 404 si no existe
     */
    AlumnoDto obtenerAlumno(String dni);

    /**
     * Alta de alumno nuevo ({@code POST /students}); en la práctica,
     * central MariaDB con patronus obligatorio y sin casa (§2.2).
     *
     * @param alumno datos del alumno; su DNI no puede estar dado de alta
     * @return el alumno dado de alta, con su versión asignada
     * @throws ExcepcionApi con status 400 si no valida o el DNI está duplicado
     */
    AlumnoDto altaAlumno(AlumnoDto alumno);

    /**
     * Edición de un alumno ({@code PUT /students/{dni}}) enviando el estado
     * previo: la versión del DTO debe coincidir con la almacenada, si no
     * se produce un conflicto (§2.3).
     *
     * @param dni    DNI del alumno a editar (se normaliza)
     * @param alumno datos nuevos; {@code version} es el estado previo
     * @return el alumno guardado con la versión incrementada
     * @throws ExcepcionApi con status 404 si no existe, 409 si hay
     *         conflicto de concurrencia o 400 si no valida
     */
    AlumnoDto editarAlumno(String dni, AlumnoDto alumno);

    /**
     * Baja de un alumno ({@code DELETE /students/{dni}}): borrado lógico
     * en los alumnos nuevos y el comportamiento de la base en los
     * históricos (§2.4).
     *
     * @param dni DNI del alumno a dar de baja (se normaliza)
     * @throws ExcepcionApi con status 404 si no existe
     */
    void bajarAlumno(String dni);

    /**
     * Fuentes de datos y su estado ({@code GET /connectors}).
     *
     * @return conectores conocidos
     * @throws ExcepcionApi si la consulta falla
     */
    List<ConectorDto> listarConectores();

    /**
     * Dispara la sincronización ({@code POST /sync}).
     *
     * @return mensaje de la operación para mostrar al usuario
     * @throws ExcepcionApi si la operación falla
     */
    String sincronizar();

    /**
     * Cola de refrescos pendientes ({@code GET /admin/refresh-queue}).
     *
     * <p>Se devuelve como lista de pares clave/valor porque la forma
     * exacta del documento la fijará la API real; así la capa cliente no
     * se rompe con los campos que se añadan.</p>
     *
     * @return entradas de la cola
     * @throws ExcepcionApi si la consulta falla
     */
    List<Map<String, Object>> colaRefrescos();

    /**
     * Fábrica: crea el cliente de acceso a la API.
     *
     * @param config configuración con la URL base y el timeout
     * @return {@link ClienteAlumnosHttp} preparado con la configuración
     * @throws NullPointerException si {@code config} es nulo
     */
    static ClienteAlumnos desde(ConfigApi config) {
        if (config == null) {
            throw new NullPointerException("La configuración es obligatoria");
        }
        return new ClienteAlumnosHttp(config);
    }
}
