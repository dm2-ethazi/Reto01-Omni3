package com.omni3.gestorhogwartsmovil

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.omni3.gestorhogwartsmovil.databinding.ActivityStudentViewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Muestra los datos seleccionados sin consultar de nuevo la API; solo la foto requiere red. */
class StudentViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val student = leerAlumno()
        binding.valorDni.text = datoOAlternativa(student.id)
        binding.valorNombre.text = datoOAlternativa(student.nombreCompleto)
        binding.valorSexo.text = datoOAlternativa(student.sexo)
        binding.valorPatronus.text = datoOAlternativa(student.patronus)
        binding.valorCasa.text = datoOAlternativa(student.casa)
        binding.botonVolver.setOnClickListener { finish() }
        cargarImagen(student.imagen)
    }

    /** Reconstruye el alumno recibido; un extra ausente se presenta como dato no disponible. */
    private fun leerAlumno() = Student(
        id = intent.getStringExtra(EXTRA_ID).orEmpty(),
        nombre = intent.getStringExtra(EXTRA_NOMBRE).orEmpty(),
        apellido = intent.getStringExtra(EXTRA_APELLIDO),
        sexo = intent.getStringExtra(EXTRA_SEXO),
        patronus = intent.getStringExtra(EXTRA_PATRONUS),
        casa = intent.getStringExtra(EXTRA_CASA),
        imagen = intent.getStringExtra(EXTRA_IMAGEN)
    )

    /** Mantiene el icono alternativo si falta la foto; la coroutine se cancela al destruir la ficha. */
    private fun cargarImagen(link: String?) {
        binding.imagenAlumno.setImageResource(R.drawable.ic_alumno)
        val imageUrl = link?.trim()?.takeIf { it.isNotEmpty() } ?: return

        lifecycleScope.launch {
            val bitmap = descargarImagen(imageUrl) ?: return@launch
            binding.imagenAlumno.setImageBitmap(bitmap)
        }
    }

    /**
     * Descarga y decodifica en IO, cerrando stream y conexión en todos los casos.
     * Conserva las cabeceras necesarias para el proveedor de imágenes actual.
     */
    private suspend fun descargarImagen(link: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(link)
            require(url.protocol == "http" || url.protocol == "https") {
                "Unsupported protocol: ${url.protocol}"
            }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                connection.setRequestProperty("Referer", "https://static.wikia.nocookie.net/")

                check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("StudentImage", "No se pudo cargar la imagen del alumno", error)
            null
        }
    }

    /** Sustituye campos ausentes por el texto traducido correspondiente. */
    private fun datoOAlternativa(value: String?): String =
        value?.takeIf { it.isNotBlank() } ?: getString(R.string.dato_no_disponible)

    companion object {
        private const val TIMEOUT_MS = 5_000
        private const val EXTRA_ID = "alumno_id"
        private const val EXTRA_NOMBRE = "alumno_nombre"
        private const val EXTRA_APELLIDO = "alumno_apellido"
        private const val EXTRA_SEXO = "alumno_sexo"
        private const val EXTRA_PATRONUS = "alumno_patronus"
        private const val EXTRA_CASA = "alumno_casa"
        private const val EXTRA_IMAGEN = "alumno_imagen"

        /** Prepara la navegación a la ficha sin exponer las claves de sus extras al llamador. */
        fun crearIntent(context: Context, student: Student): Intent =
            Intent(context, StudentViewActivity::class.java).apply {
                putExtra(EXTRA_ID, student.id)
                putExtra(EXTRA_NOMBRE, student.nombre)
                putExtra(EXTRA_APELLIDO, student.apellido)
                putExtra(EXTRA_SEXO, student.sexo)
                putExtra(EXTRA_PATRONUS, student.patronus)
                putExtra(EXTRA_CASA, student.casa)
                putExtra(EXTRA_IMAGEN, student.imagen)
            }
    }
}
