package com.omni3.gestorhogwartsmovil

/**
 * Origen de los datos mostrados, independiente de que la lista esté vacía.
 *
 * @author koldo
 * @description Origen de los datos mostrados, independiente de que la lista esté vacía.
 */
enum class StudentSource { API, SQLITE }

/**
 * Estado de la comparación completa con el servidor y de la relectura persistida.
 *
 * @author koldo
 * @description Estado de la comparación completa con el servidor y de la relectura persistida.
 */
enum class Verification { CHECKING, VERIFIED, UPDATED, OFFLINE, SERVER_ERROR, PERSISTENCE_ERROR }

/**
 * Resultado de una carga: datos disponibles, ausencia de caché o fallo no recuperable.
 *
 * @author koldo
 * @description Resultado de una carga: datos disponibles, ausencia de caché o fallo no recuperable.
 */
sealed interface StudentResult {
    /**
     * Colección completa o filtrada, que puede estar vacía sin representar un error.
     *
     * @author koldo
     * @description Colección completa o filtrada, que puede estar vacía sin representar un error.
     * @property syncedAt Fecha de persistencia en milisegundos desde Unix epoch;
     * null si la API respondió correctamente pero no se pudo guardar su respuesta.
     */
    data class Data(
        val students: List<Student>,
        val source: StudentSource,
        val syncedAt: Long?,
        val verification: Verification = Verification.CHECKING
    ) : StudentResult

    /**
     * La API no está disponible y todavía no existe una instantánea local.
     *
     * @author koldo
     * @description La API no está disponible y todavía no existe una instantánea local.
     */
    data class Unavailable(val cause: Exception) : StudentResult

    /**
     * Error HTTP 4xx, datos inválidos o fallo de lectura local que impide obtener resultados.
     *
     * @author koldo
     * @description Error HTTP 4xx, datos inválidos o fallo de lectura local que impide obtener resultados.
     */
    data class Failure(val cause: Exception) : StudentResult
}
