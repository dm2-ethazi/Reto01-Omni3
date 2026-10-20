package es.r01.mobile

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView

/**
 * Vista (MVVM) de R01: sólo pinta el estado que expone [EstudianteViewModel]
 * y le reenvía las acciones del usuario. Sin reglas de negocio.
 */
class MainActivity : Activity(), EstudianteViewModel.Observador {

    private lateinit var viewModel: EstudianteViewModel
    private lateinit var campoUrl: EditText
    private lateinit var campoNombre: EditText
    private lateinit var campoDni: EditText
    private lateinit var textoEstado: TextView
    private lateinit var lista: ListView
    private val filas = mutableListOf<String>()
    private lateinit var adaptador: FilaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        campoUrl = findViewById(R.id.campoUrl)
        campoNombre = findViewById(R.id.campoNombre)
        campoDni = findViewById(R.id.campoDni)
        textoEstado = findViewById(R.id.textoEstado)
        lista = findViewById(R.id.lista)

        // El campo arranca con la URL real (el hint no es texto: sin esto,
        // conectar("") deja urlBase vacía y toda petición falla).
        campoUrl.setText(ApiClient.DEFAULT_URL)

        filas.add("Pulsa Conectar para consultar la API")
        adaptador = FilaAdapter(this, filas)
        lista.adapter = adaptador

        viewModel = EstudianteViewModel()
        viewModel.observador = this
        viewModel.conectar(campoUrl.text.toString().ifBlank { ApiClient.DEFAULT_URL })

        findViewById<Button>(R.id.botonConectar).setOnClickListener {
            viewModel.conectar(campoUrl.text.toString().ifBlank { ApiClient.DEFAULT_URL })
        }
        findViewById<Button>(R.id.botonBuscar).setOnClickListener {
            viewModel.filtroNombre = campoNombre.text.toString()
            viewModel.filtroDni = campoDni.text.toString()
            viewModel.buscar()
        }
        findViewById<Button>(R.id.botonSalud).setOnClickListener { viewModel.consultarSalud() }
        findViewById<Button>(R.id.botonSincronizar).setOnClickListener { viewModel.sincronizar() }
        findViewById<Button>(R.id.botonDni).setOnClickListener {
            viewModel.porDni(campoDni.text.toString())
        }
    }

    override fun onFilas(pagina: Pagina) {
        filas.clear()
        filas.addAll(pagina.items.map { "${it.dni} | ${it.nombre} | ${it.casa} | ${it.patronus}" })
        if (filas.isEmpty()) filas.add("(sin resultados)")
        adaptador.notifyDataSetChanged()
    }

    override fun onEstado(texto: String) {
        textoEstado.text = texto
    }

    override fun onError(mensaje: String) {
        textoEstado.text = "ERROR: $mensaje"
    }

    override fun onDestroy() {
        viewModel.apagar()
        super.onDestroy()
    }

    /**
     * Adaptador de filas de dos líneas (DNI · Nombre / casa · patronus).
     * Sólo presentación: consume la misma lista de textos de siempre.
     */
    private class FilaAdapter(
        contexto: Context,
        private val filas: List<String>,
    ) : BaseAdapter() {

        private val inflador = LayoutInflater.from(contexto)

        override fun getCount(): Int = filas.size
        override fun getItem(posicion: Int): Any = filas[posicion]
        override fun getItemId(posicion: Int): Long = posicion.toLong()

        override fun getView(posicion: Int, vista: View?, grupo: ViewGroup): View {
            val fila = inflador.inflate(R.layout.item_fila, grupo, false)
            val titulo = fila.findViewById<TextView>(R.id.fila_titulo)
            val detalle = fila.findViewById<TextView>(R.id.fila_detalle)
            val partes = filas[posicion].split(" | ")
            if (partes.size >= 4) {
                titulo.text = partes[1]
                detalle.text = "${partes[0]} · ${partes[2]} · ${partes[3]}"
            } else {
                titulo.text = filas[posicion]
                detalle.visibility = View.GONE
            }
            return fila
        }
    }
}
