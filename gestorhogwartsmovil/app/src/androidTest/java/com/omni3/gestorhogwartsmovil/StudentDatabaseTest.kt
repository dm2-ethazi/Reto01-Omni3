package com.omni3.gestorhogwartsmovil

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Verifica SQLite real: persistencia tras reabrir, sustitución autoritativa y rollback. */
class StudentDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: StudentDatabase
    private val databaseName = "offline-test.db"
    private val harry = Student("1", "Harry", null, null, null, null, null)
    private val ron = harry.copy(id = "2", nombre = "Ron")

    @Before
    fun openDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, StudentDatabase::class.java, databaseName).build()
    }

    @After
    fun deleteDatabase() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun persistsUpsertsDeletesAndEmptySnapshots() = runBlocking {
        assertNull(database.students().read())
        database.students().replaceComplete(listOf(harry, ron), 1)
        database.close()
        database = Room.databaseBuilder(context, StudentDatabase::class.java, databaseName).build()
        assertEquals(StudentSnapshot(listOf(harry, ron), 1), database.students().read())

        val updatedHarry = harry.copy(nombre = "Harry Potter")
        database.students().replaceComplete(listOf(updatedHarry, ron), 2)
        assertEquals(listOf(updatedHarry, ron), database.students().read()?.students)

        database.students().replaceComplete(listOf(ron), 3)
        assertEquals(listOf(ron), database.students().read()?.students)

        database.students().replaceComplete(emptyList(), 4)
        assertEquals(StudentSnapshot(emptyList(), 4), database.students().read())
    }

    @Test
    fun bulkReplacementDeletesOnlyAbsentIdsAndPreservesOrder() = runBlocking {
        val original = (1..200).map { harry.copy(id = it.toString()) }
        database.students().replaceComplete(original, 1)

        val replacement = original.takeLast(50).reversed() + ron.copy(id = "new")
        database.students().replaceComplete(replacement, 2)
        database.students().replaceComplete(replacement, 3)
        assertEquals(StudentSnapshot(replacement, 3), database.students().read())
    }

    @Test
    fun failedTransactionPreservesPreviousStudentsAndSyncDate() = runBlocking {
        val previous = StudentSnapshot(listOf(harry), 1)
        database.students().replaceComplete(previous.students, previous.syncedAt)
        try {
            database.withTransaction {
                database.students().replaceComplete(listOf(ron), 2)
                error("Simulated failure before commit")
            }
            fail("The transaction must fail")
        } catch (_: IllegalStateException) {
            // La escritura y el borrado anteriores deben haberse revertido juntos.
        }
        assertEquals(previous, database.students().read())
    }
}
