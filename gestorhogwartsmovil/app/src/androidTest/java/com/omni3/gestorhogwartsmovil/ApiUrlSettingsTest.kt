package com.omni3.gestorhogwartsmovil

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.omni3.gestorhogwartsmovil.data.ApiUrlSettings
import com.omni3.gestorhogwartsmovil.data.StudentCache
import com.omni3.gestorhogwartsmovil.data.StudentSnapshot
import com.omni3.gestorhogwartsmovil.data.UrlScopedStudentCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba que un cambio de servidor conserva la dirección y aísla la caché. */
class ApiUrlSettingsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectedUrlPersistsAndOtherServerDoesNotReadPreviousCache() = runBlocking {
        val preferences = context.getSharedPreferences("api_url", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val initialUrl = ApiUrlSettings.get(context)
            val snapshot = StudentSnapshot(listOf(Student("1", "Harry", null, null, null, null, null)), 42L)
            val cache = object : StudentCache {
                var data: StudentSnapshot? = null
                override suspend fun read(): StudentSnapshot? = data
                override suspend fun replaceComplete(students: List<Student>, syncedAt: Long) {
                    data = StudentSnapshot(students, syncedAt)
                }
            }
            val scoped = UrlScopedStudentCache(context, cache)

            scoped.replaceComplete(snapshot.students, snapshot.syncedAt)
            assertEquals(snapshot, scoped.read())

            assertTrue(ApiUrlSettings.save(context, "https://example.org:8443/"))
            assertEquals("https://example.org:8443", ApiUrlSettings.get(context))
            assertNull(scoped.read())
            assertFalse(ApiUrlSettings.save(context, "ftp://example.org"))
            assertEquals("https://example.org:8443", ApiUrlSettings.get(context))

            assertTrue(ApiUrlSettings.save(context, initialUrl))
            assertEquals(snapshot, scoped.read())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
