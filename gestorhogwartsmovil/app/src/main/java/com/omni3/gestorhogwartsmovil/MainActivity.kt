package com.omni3.gestorhogwartsmovil

import com.omni3.gestorhogwartsmovil.data.ApiHttpException
import com.omni3.gestorhogwartsmovil.data.ApiUrlSettings
import com.omni3.gestorhogwartsmovil.data.StudentRepository

import android.os.Bundle
import android.view.View
import android.text.format.DateFormat
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.omni3.gestorhogwartsmovil.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordina lista, búsqueda y selección de idioma; el repositorio decide el origen de los datos.
 *
 * @author koldo
 * @description Coordina lista, búsqueda y selección de idioma; el repositorio decide el origen de los datos.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var studentAdapter: StudentAdapter
    private val repository by lazy { StudentRepository.get(applicationContext) }
    private var currentData: StudentResult.Data? = null
    private var loading = false

    private val languageTags = mapOf(
        R.id.idiomaSistema to "",
        R.id.idiomaIngles to "en",
        R.id.idiomaEspanol to "es",
        R.id.idiomaEuskera to "eu"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        configurarLista()
        configurarUrl()
        configurarBusqueda()
        binding.botonIdioma.setOnClickListener { mostrarMenuIdiomas() }
        cargarAlumnos()
    }

    /**
     * Reutiliza la misma presentación para datos remotos y locales.
     *
     * @author koldo
     * @description Reutiliza la misma presentación para datos remotos y locales.
     */
    private fun configurarLista() {
        studentAdapter = StudentAdapter(::mostrarAlumno)
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = studentAdapter
    }

    /** Muestra la URL activa y permite aplicarla durante el uso de la app. */
    private fun configurarUrl() {
        binding.campoApiUrl.setText(ApiUrlSettings.get(this))
        binding.botonAplicarUrl.setOnClickListener { aplicarUrl() }
        binding.campoApiUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            aplicarUrl()
            true
        }
    }

    private fun aplicarUrl() {
        if (loading) {
            binding.campoApiUrl.error = getString(R.string.api_url_loading)
            return
        }

        val previous = ApiUrlSettings.get(this)
        if (!ApiUrlSettings.save(this, binding.campoApiUrl.text.toString())) {
            binding.campoApiUrl.error = getString(R.string.api_url_invalid)
            return
        }

        val current = ApiUrlSettings.get(this)
        binding.campoApiUrl.setText(current)
        if (current != previous) {
            currentData = null
            studentAdapter.mostrarEstudiantes(emptyList())
            cargarAlumnos()
        }
    }

    /**
     * Comparte la acción de búsqueda entre el botón y la tecla de búsqueda del teclado.
     *
     * @author koldo
     * @description Comparte la acción de búsqueda entre el botón y la tecla de búsqueda del teclado.
     */
    private fun configurarBusqueda() {
        binding.botonBuscar.setOnClickListener { buscarAlumnos() }
        binding.campoNombre.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                return@setOnEditorActionListener false
            }

            buscarAlumnos()
            true
        }
    }

    /**
     * Evita cargas simultáneas y liga la petición al ciclo de vida de esta pantalla.
     *
     * @author koldo
     * @description Evita cargas simultáneas y liga la petición al ciclo de vida de esta pantalla.
     */
    private fun cargarAlumnos() {
        if (loading) return
        loading = true
        binding.botonAplicarUrl.isEnabled = false
        binding.progreso.visibility = View.VISIBLE
        mostrarEstado(R.string.cargando_alumnos)
        lifecycleScope.launch {
            try {
                when (val result = repository.listarAlumnos { cached ->
                    withContext(Dispatchers.Main) {
                        currentData = cached
                        mostrarResultados()
                    }
                }) {
                    is StudentResult.Data -> {
                        currentData = result
                        mostrarResultados()
                    }
                    is StudentResult.Unavailable -> mostrarEstado(R.string.offline_sin_cache)
                    is StudentResult.Failure -> {
                        binding.textoEstado.setTextColor(getColor(R.color.r01_error))
                        binding.textoEstado.setText(when {
                            result.cause is ApiHttpException && result.cause.statusCode in listOf(401, 403) -> R.string.error_acceso
                            result.cause is ApiHttpException && result.cause.statusCode == 404 -> R.string.error_servicio
                            else -> R.string.error_amable
                        })
                    }
                }
            } finally {
                loading = false
                binding.botonAplicarUrl.isEnabled = true
                binding.progreso.visibility = View.GONE
            }
        }
    }

    /**
     * Aplica el texto actual sin perder la indicación de procedencia offline.
     *
     * @author koldo
     * @description Aplica el texto actual sin perder la indicación de procedencia offline.
     */
    private fun mostrarResultados() {
        val data = currentData ?: return
        val query = binding.campoNombre.text.toString()
        val results = repository.buscar(data, query)
        studentAdapter.mostrarEstudiantes(results.students)
        binding.textoEstado.setTextColor(getColor(
            if (data.verification in listOf(Verification.SERVER_ERROR, Verification.PERSISTENCE_ERROR))
                R.color.r01_error else R.color.r01_exito
        ))
        val messageId = if (query.isBlank()) R.string.alumnos_cargados else R.string.resultados_busqueda
        val message = if (query.isNotBlank() && results.students.isEmpty())
            getString(R.string.sin_resultados) else getString(messageId, results.students.size)
        val origin = getString(if (data.source == StudentSource.SQLITE) R.string.origen_sqlite else R.string.origen_api)
        val verification = getString(when (data.verification) {
            Verification.CHECKING -> R.string.verificando
            Verification.VERIFIED -> R.string.verificado
            Verification.UPDATED -> R.string.actualizado
            Verification.OFFLINE -> R.string.sin_red_cache
            Verification.SERVER_ERROR -> R.string.servidor_cache
            Verification.PERSISTENCE_ERROR -> R.string.error_persistencia
        })
        val date = data.syncedAt?.let {
            getString(R.string.ultima_sincronizacion, DateFormat.format("dd/MM/yyyy HH:mm", it))
        }.orEmpty()
        binding.textoEstado.text = getString(R.string.estado_datos, message, origin, verification, date)
    }

    /**
     * Una búsqueda vacía recarga la API; las búsquedas con texto filtran la lista disponible.
     *
     * @author koldo
     * @description Una búsqueda vacía recarga la API; las búsquedas con texto filtran la lista disponible.
     */
    private fun buscarAlumnos() {
        if (currentData == null || binding.campoNombre.text.isNullOrBlank()) {
            cargarAlumnos()
        } else {
            mostrarResultados()
        }
    }

    /**
     * Abre la ficha con los datos de la fila, sin otra consulta de alumnos.
     *
     * @author koldo
     * @description Abre la ficha con los datos de la fila, sin otra consulta de alumnos.
     */
    private fun mostrarAlumno(student: Student) {
        startActivity(StudentViewActivity.crearIntent(this, student))
    }

    /**
     * Presenta estados informativos de carga o de ausencia de caché.
     *
     * @author koldo
     * @description Presenta estados informativos de carga o de ausencia de caché.
     */
    private fun mostrarEstado(messageId: Int) {
        binding.textoEstado.setTextColor(getColor(R.color.r01_texto_secundario))
        binding.textoEstado.setText(messageId)
    }

    /**
     * Marca el idioma de la aplicación, o «sistema» si no existe una selección explícita.
     *
     * @author koldo
     * @description Marca el idioma de la aplicación, o «sistema» si no existe una selección explícita.
     */
    private fun mostrarMenuIdiomas() {
        val currentLanguage = AppCompatDelegate
            .getApplicationLocales()
            .get(0)
            ?.language
            .orEmpty()

        val selectedItemId = languageTags.entries
            .firstOrNull { it.value == currentLanguage }
            ?.key ?: R.id.idiomaSistema

        val popupMenu = PopupMenu(this, binding.botonIdioma)
        popupMenu.inflate(R.menu.menu_idiomas)
        popupMenu.menu.findItem(selectedItemId).isChecked = true
        popupMenu.setOnMenuItemClickListener { item ->
            val language = languageTags[item.itemId]
                ?: return@setOnMenuItemClickListener false

            aplicarIdioma(language)
            true
        }
        popupMenu.show()
    }

    /**
     * Un tag vacío elimina la selección explícita y recupera el idioma del dispositivo.
     *
     * @author koldo
     * @description Un tag vacío elimina la selección explícita y recupera el idioma del dispositivo.
     */
    private fun aplicarIdioma(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }
}
