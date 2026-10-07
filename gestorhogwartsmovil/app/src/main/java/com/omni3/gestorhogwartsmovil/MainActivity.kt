package com.omni3.gestorhogwartsmovil

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.LocaleListCompat
import com.omni3.gestorhogwartsmovil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.botonIdioma.setOnClickListener {
            mostrarMenuIdiomas()
        }
    }

    private fun mostrarMenuIdiomas() {
        val idiomas = mapOf(
            R.id.idiomaSistema to "",
            R.id.idiomaIngles to "en",
            R.id.idiomaEspanol to "es",
            R.id.idiomaEuskera to "eu"
        )

        val idiomaActual = AppCompatDelegate
            .getApplicationLocales()
            .get(0)
            ?.language
            .orEmpty()

        PopupMenu(this, binding.botonIdioma).apply {
            inflate(R.menu.menu_idiomas)

            // Meter en un grupo exclusivo
            val idSeleccionado = idiomas.entries
                .firstOrNull { it.value == idiomaActual }
                ?.key ?: R.id.idiomaSistema
            menu.findItem(idSeleccionado).isChecked = true
            Log.d("Idioma seleccionado: ", idiomaActual)
            setOnMenuItemClickListener { item ->
                val idioma = idiomas[item.itemId]
                    ?: return@setOnMenuItemClickListener false

                if (idioma.isEmpty()) {
                    // Volver al idioma configurado en el sistema
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.getEmptyLocaleList()

                    )

                } else {
                    // Establecer idioma específico para la aplicacion
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(idioma)
                    )
                }

                true
            }

            show()

        }
    }
}
