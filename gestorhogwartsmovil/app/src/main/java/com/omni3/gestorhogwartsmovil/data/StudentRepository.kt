package com.omni3.gestorhogwartsmovil.data

import com.omni3.gestorhogwartsmovil.Student
import com.omni3.gestorhogwartsmovil.StudentResult
import com.omni3.gestorhogwartsmovil.StudentSource
import com.omni3.gestorhogwartsmovil.Verification

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Muestra primero SQLite y después verifica todos sus campos contra la colección del servidor.
 *
 * Las peticiones y sus escrituras se serializan para que una respuesta antigua no sustituya
 * datos más recientes. Las búsquedas operan sobre la lista cargada y nunca modifican SQLite.
 *
 * @author koldo
 * @description Muestra primero SQLite y después verifica todos sus campos contra la colección del servidor.
 * @param remote Consulta bloqueante de la colección completa, sin filtros ni paginación.
 * @param cache Almacenamiento de la última colección sincronizada.
 * @param clock Reloj en milisegundos desde Unix epoch; se puede sustituir en las pruebas.
 * @param logPersistenceError Registra fallos locales sin descartar una respuesta remota válida.
 */
class StudentRepository(
    private val remote: () -> List<Student>,
    private val cache: StudentCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logPersistenceError: (Exception) -> Unit = { Log.e("R01-PERSISTENCE", "SQLite verification failed", it) }
) {
    private val mutex = Mutex()

    /**
     * Notifica la caché antes de acceder a red. El callback suspendido se ejecuta en IO;
     * la presentación debe cambiar a Main. Siempre consulta después la API para verificar.
     *
     * Una lista vacía es un resultado válido. La cancelación se propaga al llamador,
     * mientras que los demás errores se representan mediante [StudentResult].
     *
     * @author koldo
     * @description Notifica la caché antes de acceder a red. El callback suspendido se ejecuta en IO; la presentación debe cambiar a Main. Siempre consulta después la API para verificar.
     */
    suspend fun listarAlumnos(onCached: suspend (StudentResult.Data) -> Unit = {}): StudentResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            var localError: Exception? = null
            val snapshot = try {
                cache.read()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logPersistenceError(error)
                localError = error
                null
            }
            if (snapshot != null) {
                onCached(StudentResult.Data(snapshot.students, StudentSource.SQLITE, snapshot.syncedAt))
            }

            val students = try {
                remote().also(::validarAlumnos)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val apiNoDisponible = error is IOException ||
                    (error is ApiHttpException && error.statusCode in 500..599)
                return@withLock when {
                    snapshot != null -> StudentResult.Data(
                        snapshot.students, StudentSource.SQLITE, snapshot.syncedAt,
                        if (apiNoDisponible) Verification.OFFLINE else Verification.SERVER_ERROR
                    )
                    !apiNoDisponible -> StudentResult.Failure(error)
                    localError != null -> StudentResult.Failure(localError)
                    else -> StudentResult.Unavailable(error)
                }
            }
            val syncedAt = guardarCache(students)
            val unchanged = snapshot?.students == students
            StudentResult.Data(
                students, if (unchanged) StudentSource.SQLITE else StudentSource.API, syncedAt,
                when {
                    syncedAt == null -> Verification.PERSISTENCE_ERROR
                    unchanged -> Verification.VERIFIED
                    else -> Verification.UPDATED
                }
            )
        }
    }

    /**
     * Rechaza una respuesta incompleta o ambigua antes de que pueda reemplazar la caché.
     *
     * @author koldo
     * @description Rechaza una respuesta incompleta o ambigua antes de que pueda reemplazar la caché.
     */
    private fun validarAlumnos(students: List<Student>) {
        require(students.all { it.id.isNotBlank() && it.nombre.isNotBlank() }) { "Invalid student data" }
        require(students.map { it.id }.toSet().size == students.size) { "Duplicate student IDs" }
    }

    /**
     * Devuelve la fecha persistida, o null si falló el guardado; nunca oculta una cancelación.
     *
     * @author koldo
     * @description Devuelve la fecha persistida, o null si falló el guardado; nunca oculta una cancelación.
     */
    private suspend fun guardarCache(students: List<Student>): Long? = try {
        val syncedAt = clock()
        cache.replaceComplete(students, syncedAt)
        // R01-PERSISTENCE: igualdad de TODOS los campos, orden, cantidad y fecha tras releer SQLite.
        check(cache.read() == StudentSnapshot(students, syncedAt)) {
            "R01-PERSISTENCE: persisted snapshot differs from server response"
        }
        syncedAt
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        logPersistenceError(error)
        null
    }

    /**
     * Filtra por nombre completo sin consultar la API ni escribir en la base de datos.
     *
     * @author koldo
     * @description Filtra por nombre completo sin consultar la API ni escribir en la base de datos.
     */
    fun buscar(data: StudentResult.Data, query: String): StudentResult.Data =
        data.copy(students = data.students.filter { it.coincideConNombre(query) })

    companion object {
        @Volatile
        private var instance: StudentRepository? = null

        /**
         * Comparte repositorio, base de datos y bloqueo entre las Activities del proceso.
         *
         * @author koldo
         * @description Comparte repositorio, base de datos y bloqueo entre las Activities del proceso.
         */
        fun get(context: Context): StudentRepository = instance ?: synchronized(this) {
            instance ?: StudentRepository(ApiClient::listarAlumnos, StudentDatabase.create(context).students())
                .also { instance = it }
        }
    }
}
