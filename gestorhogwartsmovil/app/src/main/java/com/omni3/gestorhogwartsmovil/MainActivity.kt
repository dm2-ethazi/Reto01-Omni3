package com.omni3.gestorhogwartsmovil

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.omni3.gestorhogwartsmovil.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Coordina lista, búsqueda y selección de idioma; el repositorio decide el origen de los datos. */
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

        configurarLista()
        configurarBusqueda()
        binding.botonIdioma.setOnClickListener { mostrarMenuIdiomas() }
        cargarAlumnos()
    }

    /** Reutiliza la misma presentación para datos remotos y locales. */
    private fun configurarLista() {
        studentAdapter = StudentAdapter(::mostrarAlumno)
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = studentAdapter
    }

    /** Comparte la acción de búsqueda entre el botón y la tecla de búsqueda del teclado. */
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

    /** Evita cargas simultáneas y liga la petición al ciclo de vida de esta pantalla. */
    private fun cargarAlumnos() {
        if (loading) return
        loading = true
        mostrarEstado(R.string.cargando_alumnos)
        lifecycleScope.launch {
            try {
                when (val result = repository.listarAlumnos()) {
                    is StudentResult.Data -> {
                        currentData = result
                        mostrarResultados()
                    }
                    is StudentResult.Unavailable -> mostrarEstado(R.string.offline_sin_cache)
                    is StudentResult.Failure -> {
                        binding.textoEstado.setTextColor(getColor(R.color.r01_error))
                        binding.textoEstado.text = getString(R.string.error_api, result.cause.message.orEmpty())
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    /** Aplica el texto actual sin perder la indicación de procedencia offline. */
    private fun mostrarResultados() {
        val data = currentData ?: return
        val query = binding.campoNombre.text.toString()
        val results = repository.buscar(data, query)
        studentAdapter.mostrarEstudiantes(results.students)
        binding.textoEstado.setTextColor(getColor(R.color.r01_exito))
        val messageId = if (query.isBlank()) R.string.alumnos_cargados else R.string.resultados_busqueda
        val message = getString(messageId, results.students.size)
        binding.textoEstado.text = if (data.source == StudentSource.SQLITE) {
            getString(R.string.datos_offline, message)
        } else message
    }

    /** Una búsqueda vacía recarga la API; las búsquedas con texto filtran la lista disponible. */
    private fun buscarAlumnos() {
        if (currentData == null || binding.campoNombre.text.isNullOrBlank()) {
            cargarAlumnos()
        } else {
            mostrarResultados()
        }
    }

    /** Abre la ficha con los datos de la fila, sin otra consulta de alumnos. */
    private fun mostrarAlumno(student: Student) {
        startActivity(StudentViewActivity.crearIntent(this, student))
    }

    /** Presenta estados informativos de carga o de ausencia de caché. */
    private fun mostrarEstado(messageId: Int) {
        binding.textoEstado.setTextColor(getColor(R.color.r01_texto_secundario))
        binding.textoEstado.setText(messageId)
    }

    /** Marca el idioma de la aplicación, o «sistema» si no existe una selección explícita. */
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

    /** Un tag vacío elimina la selección explícita y recupera el idioma del dispositivo. */
    private fun aplicarIdioma(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }
}
