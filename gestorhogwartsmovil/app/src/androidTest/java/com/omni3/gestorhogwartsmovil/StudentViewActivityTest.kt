package com.omni3.gestorhogwartsmovil

import android.content.Intent
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.omni3.gestorhogwartsmovil.databinding.ActivityMainBinding
import com.omni3.gestorhogwartsmovil.databinding.ActivityStudentViewBinding
import org.junit.Assert.assertEquals
import org.junit.Test

/** Comprueba el contrato de navegación y las variantes visuales sin necesitar la API. */
class StudentViewActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val student = Student("1", "Harry", "Potter", "male", "Stag", "Gryffindor", null)

    @Test
    fun selectedStudentSurvivesActivityRecreation() {
        val intent = StudentViewActivity.crearIntent(context, student)
        ActivityScenario.launch<StudentViewActivity>(intent).use { scenario ->
            scenario.onActivity(::assertStudentDetails)
            scenario.recreate()
            scenario.onActivity(::assertStudentDetails)
        }
    }

    @Test
    fun missingDataUsesLocalizedPlaceholder() {
        val intent = Intent(context, StudentViewActivity::class.java)
        ActivityScenario.launch<StudentViewActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val placeholder = activity.getString(R.string.dato_no_disponible)
                for (id in listOf(R.id.valorDni, R.id.valorNombre, R.id.valorSexo, R.id.valorPatronus, R.id.valorCasa)) {
                    assertEquals(placeholder, activity.findViewById<TextView>(id).text.toString())
                }
            }
        }
    }

    @Test
    fun layoutsBindOnPhoneAndTabletInBothOrientations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (tablet in listOf(false, true)) {
                for (landscape in listOf(false, true)) {
                    val config = Configuration(context.resources.configuration).apply {
                        smallestScreenWidthDp = if (tablet) 800 else 360
                        screenWidthDp = if (landscape) 1280 else smallestScreenWidthDp
                        screenHeightDp = if (landscape) 800 else 1280
                        orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    }
                    val themedContext = ContextThemeWrapper(
                        context.createConfigurationContext(config), R.style.Theme_GestorHogwartsMovil
                    )
                    val inflater = LayoutInflater.from(themedContext)
                    ActivityMainBinding.inflate(inflater)
                    val detail = ActivityStudentViewBinding.inflate(inflater)
                    if (tablet) {
                        // Las etiquetas y los valores deben apilarse, no salirse del ancho de la columna.
                        val column = detail.valorNombre.parent as LinearLayout
                        assertEquals(LinearLayout.VERTICAL, column.orientation)
                    }
                }
            }
        }
    }

    /** Valida los campos que comparten los layouts de teléfono y tablet. */
    private fun assertStudentDetails(activity: StudentViewActivity) {
        val expected = mapOf(
            R.id.valorDni to student.id,
            R.id.valorNombre to student.nombreCompleto,
            R.id.valorSexo to student.sexo,
            R.id.valorPatronus to student.patronus,
            R.id.valorCasa to student.casa
        )
        for ((id, value) in expected) {
            assertEquals(value, activity.findViewById<TextView>(id).text.toString())
        }
    }
}
