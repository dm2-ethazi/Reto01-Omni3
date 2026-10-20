package com.omni3.gestorhogwartsmovil

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Verifica la selección de fuente y las garantías de caché sin depender de red ni de Android. */
class StudentRepositoryTest {

    private fun student(id: String, name: String = id) = Student(id, name, null, null, null, null, null)

    /** Caché en memoria que permite simular fallos y observar escrituras. */
    private class MemoryCache : StudentCache {
        var snapshot: StudentSnapshot? = null
        var writes = 0
        var readError: Exception? = null
        var writeError: Exception? = null

        override suspend fun read(): StudentSnapshot? {
            readError?.let { throw it }
            return snapshot
        }

        override suspend fun replaceComplete(students: List<Student>, syncedAt: Long) {
            writeError?.let { throw it }
            snapshot = StudentSnapshot(students, syncedAt)
            writes++
        }
    }

    @Test
    fun onlineOfflineAndReconnect() = runBlocking {
        val cache = MemoryCache()
        var online = true
        var rows = listOf(student("1", "Harry"))
        val repository = StudentRepository(
            remote = {
                if (!online) throw IOException("No connection")
                rows
            },
            cache = cache,
            clock = { 123L }
        )

        val onlineData = repository.listarAlumnos() as StudentResult.Data
        assertEquals(StudentSource.API, onlineData.source)
        assertEquals(StudentSnapshot(rows, 123L), cache.snapshot)

        online = false
        val offlineData = repository.listarAlumnos() as StudentResult.Data
        assertEquals(StudentSource.SQLITE, offlineData.source)
        assertEquals(rows, offlineData.students)
        assertEquals(1, cache.writes)

        online = true
        rows = listOf(student("1", "Harry Potter"), student("2"))
        val reconnectedData = repository.listarAlumnos() as StudentResult.Data
        repository.listarAlumnos()
        assertEquals(StudentSource.API, reconnectedData.source)
        assertEquals(rows, cache.snapshot?.students)
    }

    @Test
    fun emptyResponseIsCachedAndDistinctFromMissingCache() = runBlocking {
        val cache = MemoryCache()
        var online = true
        val repository = StudentRepository(
            remote = {
                if (!online) throw IOException("No connection")
                emptyList()
            },
            cache = cache
        )

        repository.listarAlumnos()
        online = false
        val data = repository.listarAlumnos() as StudentResult.Data
        assertTrue(data.students.isEmpty())
        assertEquals(StudentSource.SQLITE, data.source)

        val emptyRepository = StudentRepository({ throw IOException("No connection") }, MemoryCache())
        assertTrue(emptyRepository.listarAlumnos() is StudentResult.Unavailable)
    }

    @Test
    fun filtersDoNotWriteOrDiscardOtherStudents() = runBlocking {
        val cache = MemoryCache()
        val rows = listOf(student("1", "Harry"), student("2", "Ron"))
        val repository = StudentRepository({ rows }, cache)
        val data = repository.listarAlumnos() as StudentResult.Data

        assertEquals(listOf(rows.first()), repository.buscar(data, "har").students)
        assertEquals(rows, repository.buscar(data, " ").students)
        assertEquals(emptyList<Student>(), repository.buscar(data, "Hermione").students)
        assertEquals(1, cache.writes)
        assertEquals(rows, cache.snapshot?.students)
    }

    @Test
    fun httpAndInvalidDataDoNotSilentlyUseCache() = runBlocking {
        val cache = MemoryCache().apply { snapshot = StudentSnapshot(listOf(student("1")), 1) }
        for (status in listOf(400, 401, 403, 404)) {
            val repository = StudentRepository({ throw ApiHttpException(status, "HTTP $status") }, cache)
            assertTrue(repository.listarAlumnos() is StudentResult.Failure)
        }
        val invalidResponses = listOf(
            listOf(student("1"), student("1")),
            listOf(student("")),
            listOf(student("1", " "))
        )
        for (rows in invalidResponses) {
            assertTrue(StudentRepository({ rows }, cache).listarAlumnos() is StudentResult.Failure)
        }

        val unavailableApi = StudentRepository({ throw ApiHttpException(503, "Unavailable") }, cache)
        assertTrue(unavailableApi.listarAlumnos() is StudentResult.Data)
        assertEquals(0, cache.writes)
    }

    @Test
    fun persistenceFailureStillReturnsRemoteData() = runBlocking {
        val cache = MemoryCache().apply { writeError = IOException("Disk full") }
        var logged = false
        val repository = StudentRepository(
            remote = { listOf(student("1")) },
            cache = cache,
            logPersistenceError = { logged = true }
        )

        val result = repository.listarAlumnos() as StudentResult.Data
        assertEquals(StudentSource.API, result.source)
        assertEquals(listOf(student("1")), result.students)
        assertNull(result.syncedAt)
        assertTrue(logged)
    }

    @Test
    fun cacheReadFailureIsReportedWithoutDeletingData() = runBlocking {
        val saved = StudentSnapshot(listOf(student("1")), 1)
        val cache = MemoryCache().apply {
            snapshot = saved
            readError = IOException("Unreadable database")
        }
        var logged = false
        val repository = StudentRepository(
            remote = { throw IOException("No connection") },
            cache = cache,
            logPersistenceError = { logged = true }
        )

        assertTrue(repository.listarAlumnos() is StudentResult.Failure)
        assertEquals(saved, cache.snapshot)
        assertEquals(0, cache.writes)
        assertTrue(logged)
    }

    @Test
    fun cancellationIsNeverConvertedToDataOrAnErrorResult() = runBlocking {
        val cancelled = CancellationException("Screen destroyed")
        val repositories = listOf(
            StudentRepository({ throw cancelled }, MemoryCache()),
            StudentRepository({ throw IOException() }, MemoryCache().apply { readError = cancelled }),
            StudentRepository({ listOf(student("1")) }, MemoryCache().apply { writeError = cancelled })
        )
        for (repository in repositories) {
            try {
                repository.listarAlumnos()
                fail("Cancellation must reach the caller")
            } catch (_: CancellationException) {
                // Es el contrato esperado, incluso durante lectura o escritura de la caché.
            }
        }
    }

    @Test
    fun concurrentRequestsAreSerialized() = runBlocking {
        val active = AtomicInteger()
        val sequence = AtomicInteger()
        val cache = MemoryCache()
        val repository = StudentRepository(
            remote = {
                check(active.incrementAndGet() == 1)
                try {
                    Thread.sleep(10)
                    listOf(student(sequence.incrementAndGet().toString()))
                } finally {
                    active.decrementAndGet()
                }
            },
            cache = cache
        )

        val results = (1..10).map { async { repository.listarAlumnos() } }.awaitAll()
        assertTrue(results.all { it is StudentResult.Data })
        assertEquals("10", cache.snapshot?.students?.single()?.id)
        assertEquals(10, cache.writes)
    }
}
