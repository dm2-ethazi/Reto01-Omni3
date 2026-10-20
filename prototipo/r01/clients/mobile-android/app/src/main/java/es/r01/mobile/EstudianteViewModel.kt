package es.r01.mobile

import java.io.IOException

/**
 * ViewModel MVVM de la pantalla de estudiantes.
 *
 * Mantiene el estado observable de la lista y de la barra de estado, y
 * delega toda la red en [ApiClient] desde un hilo de fondo. La Activity
 * sólo se suscribe a los callbacks: sin lógica de negocio en la Vista.
 */
class EstudianteViewModel(
    private val api: ApiClient = ApiClient(),
    private val executor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "r01-api").apply { isDaemon = true }
        }
) {

    /** Observador de la UI: recibe el estado ya formateado. */
    interface Observador {
        fun onFilas(pagina: Pagina)
        fun onEstado(texto: String)
        fun onError(mensaje: String)
    }

    var observador: Observador? = null

    var origen: String = "central"
    var filtroNombre: String = ""
    var filtroDni: String = ""

    fun conectar(baseUrl: String) {
        api.urlBase = baseUrl
        buscar()
    }

    fun buscar() {
        publicar { it.onEstado("Consultando $origen ...") }
        ejecutar {
            val pagina = if (origen == "central") {
                api.central(filtroNombre, filtroDni)
            } else {
                api.origen(origen, filtroNombre, filtroDni)
            }
            publicar { it.onFilas(pagina) }
            publicar {
                it.onEstado(
                    "$origen: ${pagina.items.size} de ${pagina.total} filas " +
                        "(backend=${pagina.backend ?: pagina.conector ?: "-"})"
                )
            }
        }
    }

    fun consultarSalud() {
        ejecutar {
            val salud = api.salud()
            publicar { it.onEstado("status=${salud.status} | caidos=${salud.caidos}") }
        }
    }

    fun sincronizar() {
        observador?.onEstado("Sincronizando ...")
        ejecutar {
            val resumen = api.sincronizar()
            publicar {
                it.onEstado(
                    "sync: insertados=${resumen.insertados} " +
                        "actualizados=${resumen.actualizados} errores=${resumen.errores}"
                )
            }
            buscar()
        }
    }

    fun porDni(dni: String) {
        ejecutar {
            val estudiante = api.porDni(dni)
            publicar {
                if (estudiante == null) {
                    it.onEstado("DNI $dni no encontrado")
                } else {
                    it.onEstado("${estudiante.dni} - ${estudiante.nombre} (${estudiante.casa})")
                }
            }
        }
    }

    // --- Acciones CRUD sobre un origen ------------------------------------

    /** POST /connectors/{origen}/estudiantes - sólo aplica a orígenes. */
    fun crear(datos: EstudiantePayload) {
        val conector = origenSeleccionado()
        if (conector == null) {
            observador?.onEstado("La alta sólo existe en los orígenes, no en la central")
            return
        }
        ejecutar {
            val creado = api.crearEstudiante(conector, datos)
            publicar { it.onEstado("Creado id=${creado.id} en $conector") }
            buscar()
        }
    }

    /** PUT /connectors/{origen}/estudiantes/{rowId} - parcial. */
    fun actualizar(rowId: Int, cambios: EstudianteUpdate) {
        val conector = origenSeleccionado()
        if (conector == null) {
            observador?.onEstado("La edición sólo existe en los orígenes, no en la central")
            return
        }
        ejecutar {
            val modificado = api.actualizarEstudiante(conector, rowId, cambios)
            publicar { it.onEstado("Actualizado id=${modificado.id} en $conector") }
            buscar()
        }
    }

    /** DELETE /connectors/{origen}/estudiantes/{rowId} - responde 204. */
    fun borrar(rowId: Int) {
        val conector = origenSeleccionado()
        if (conector == null) {
            observador?.onEstado("El borrado sólo existe en los orígenes, no en la central")
            return
        }
        ejecutar {
            api.borrarEstudiante(conector, rowId)
            publicar { it.onEstado("Borrado id=$rowId de $conector") }
            buscar()
        }
    }

    /** Devuelve el conector activo, o null si la vista apunta a la central. */
    private fun origenSeleccionado(): String? =
        if (origen == "central" || origen.isBlank()) null else origen

    fun apagar() {
        executor.shutdownNow()
    }

    private fun ejecutar(tarea: () -> Unit) {
        executor.execute {
            try {
                tarea()
            } catch (error: IOException) {
                publicar { it.onError(error.message ?: "error de red") }
            } catch (error: Exception) {
                publicar { it.onError(error.message ?: error.toString()) }
            }
        }
    }

    private fun publicar(accion: (Observador) -> Unit) {
        val actual = observador ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { accion(actual) }
    }
}
