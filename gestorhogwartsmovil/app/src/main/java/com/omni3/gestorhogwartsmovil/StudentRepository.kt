package com.omni3.gestorhogwartsmovil

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Punto de acceso a los alumnos: prioriza la API y conserva una copia local de cada lista válida.
 *
 * Las peticiones y sus escrituras se serializan para que una respuesta antigua no sustituya
 * datos más recientes. Las búsquedas operan sobre la lista cargada y nunca modifican SQLite.
 *
 * @param remote Consulta bloqueante de la colección completa, sin filtros ni paginación.
 * @param cache Almacenamiento de la última colección sincronizada.
 * @param clock Reloj en milisegundos desde Unix epoch; se puede sustituir en las pruebas.
 * @param logPersistenceError Registra fallos locales sin descartar una respuesta remota válida.
 */
class StudentRepository(
    private val remote: () -> List<Student>,
    private val cache: StudentCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logPersistenceError: (Exception) -> Unit = { Log.e("StudentRepository", "Local persistence failed", it) }
) {
    private val mutex = Mutex()

    /**
     * Consulta y sincroniza en IO. Solo los errores de red y HTTP 5xx permiten usar la caché.
     *
     * Una lista vacía es un resultado válido. La cancelación se propaga al llamador,
     * mientras que los demás errores se representan mediante [StudentResult].
     */
    suspend fun listarAlumnos(): StudentResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val students = try {
                remote().also(::validarAlumnos)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return@withLock recuperarTrasError(error)
            }
            StudentResult.Data(students, StudentSource.API, guardarCache(students))
        }
    }

    /** Rechaza una respuesta incompleta o ambigua antes de que pueda reemplazar la caché. */
    private fun validarAlumnos(students: List<Student>) {
        require(students.all { it.id.isNotBlank() && it.nombre.isNotBlank() }) { "Invalid student data" }
        require(students.map { it.id }.toSet().size == students.size) { "Duplicate student IDs" }
    }

    /** Conserva los errores HTTP 4xx y de formato; recupera SQLite solo si la API no está disponible. */
    private suspend fun recuperarTrasError(error: Exception): StudentResult {
        val apiNoDisponible = error is IOException ||
            (error is ApiHttpException && error.statusCode in 500..599)
        if (!apiNoDisponible) return StudentResult.Failure(error)

        return try {
            val snapshot = cache.read() ?: return StudentResult.Unavailable(error)
            StudentResult.Data(snapshot.students, StudentSource.SQLITE, snapshot.syncedAt)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (localError: Exception) {
            logPersistenceError(localError)
            StudentResult.Failure(localError)
        }
    }

    /** Devuelve la fecha persistida, o null si falló el guardado; nunca oculta una cancelación. */
    private suspend fun guardarCache(students: List<Student>): Long? = try {
        val syncedAt = clock()
        cache.replaceComplete(students, syncedAt)
        syncedAt
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logPersistenceError(error)
        null
    }

    /** Filtra por nombre completo sin consultar la API ni escribir en la base de datos. */
    fun buscar(data: StudentResult.Data, query: String): StudentResult.Data =
        data.copy(students = data.students.filter { it.coincideConNombre(query) })

    companion object {
        @Volatile
        private var instance: StudentRepository? = null

        /** Comparte repositorio, base de datos y bloqueo entre las Activities del proceso. */
        fun get(context: Context): StudentRepository = instance ?: synchronized(this) {
            instance ?: StudentRepository(ApiClient::listarAlumnos, StudentDatabase.create(context).students())
                .also { instance = it }
        }
    }
}
