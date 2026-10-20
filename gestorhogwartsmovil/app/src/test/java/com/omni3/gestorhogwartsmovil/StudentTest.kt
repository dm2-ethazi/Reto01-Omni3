package com.omni3.gestorhogwartsmovil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba nombres, búsquedas y mapeo sin red ni base de datos. */
class StudentTest {
    private val student = Student("1", "Harry", "Potter", "male", "Stag", "Gryffindor", "https://example.com/harry.jpg")

    @Test
    fun fullNameHandlesMissingSurname() {
        assertEquals("Harry Potter", student.nombreCompleto)
        assertEquals("Harry", student.copy(apellido = null).nombreCompleto)
    }

    @Test
    fun searchTrimsTextAndIgnoresCase() {
        assertTrue(student.coincideConNombre("  pOtT  "))
        assertTrue(student.coincideConNombre("harry p"))
        assertTrue(student.coincideConNombre(" "))
        assertFalse(student.coincideConNombre("Ron"))
    }

    @Test
    fun entityMappingPreservesEveryApiField() {
        val entity = student.toEntity(position = 7)
        assertEquals(7, entity.position)
        assertEquals(student, entity.toStudent())
    }

    @Test
    fun entityMappingPreservesMissingOptionalFields() {
        val minimal = Student("2", "Ron", null, null, null, null, null)
        assertEquals(minimal, minimal.toEntity(position = 0).toStudent())
    }
}
