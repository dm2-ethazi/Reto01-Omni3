package com.omni3.gestorhogwartsmovil

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica que una fila reciclada muestra y selecciona al alumno actual tras actualizar la lista.
 *
 * @author koldo
 * @description Verifica que una fila reciclada muestra y selecciona al alumno actual tras actualizar la lista.
 */
class StudentAdapterTest {
    @Test
    fun replacingResultsUpdatesContentAndSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val harry = Student("1", "Harry", null, null, null, null, null)
        val ron = harry.copy(id = "2", nombre = "Ron")
        var selected: Student? = null
        lateinit var adapter: StudentAdapter
        lateinit var holder: StudentAdapter.StudentViewHolder
        val updated = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_GestorHogwartsMovil)
            adapter = StudentAdapter { selected = it }
            holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.submitList(listOf(harry))
            adapter.onBindViewHolder(holder, 0)
            holder.itemView.performClick()
            assertEquals(harry, selected)
            adapter.submitList(listOf(ron)) { updated.countDown() }
        }
        assertTrue("The list update must finish", updated.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            adapter.onBindViewHolder(holder, 0)
            assertEquals("Ron", holder.itemView.findViewById<TextView>(R.id.textoNombre).text.toString())
            holder.itemView.performClick()
            assertEquals(ron, selected)
        }
    }
}
