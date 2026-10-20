package com.omni3.gestorhogwartsmovil

/** Origen de los datos mostrados, independiente de que la lista esté vacía. */
enum class StudentSource { API, SQLITE }

/** Resultado de una carga: datos disponibles, ausencia de caché o fallo no recuperable. */
sealed interface StudentResult {
    /**
     * Colección completa o filtrada, que puede estar vacía sin representar un error.
     *
     * @property syncedAt Fecha de persistencia en milisegundos desde Unix epoch;
     * null si la API respondió correctamente pero no se pudo guardar su respuesta.
     */
    data class Data(
        val students: List<Student>,
        val source: StudentSource,
        val syncedAt: Long?
    ) : StudentResult

    /** La API no está disponible y todavía no existe una instantánea local. */
    data class Unavailable(val cause: Exception) : StudentResult

    /** Error HTTP 4xx, datos inválidos o fallo de lectura local que impide obtener resultados. */
    data class Failure(val cause: Exception) : StudentResult
}
