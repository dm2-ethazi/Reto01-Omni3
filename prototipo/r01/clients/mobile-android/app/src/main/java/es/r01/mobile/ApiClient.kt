package es.r01.mobile

import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Cliente Kotlin de la API de R01 (OkHttp + Gson).
 *
 * Contrato cubierto: /health, /connectors, /connectors/{id}/estudiantes,
 * /central/estudiantes, /central/estudiantes/{dni} y /sync.
 */
class ApiClient(baseUrl: String = DEFAULT_URL) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    var urlBase: String = normalizar(baseUrl)
        set(value) {
            field = normalizar(value)
        }

    /** GET /health */
    @Throws(IOException::class)
    fun salud(): Salud = gson.fromJson(get("/health"), Salud::class.java)

    /** GET /connectors - ids y disponibilidad de cada conector. */
    @Throws(IOException::class)
    fun conectores(): ListaConectores = gson.fromJson(get("/connectors"), ListaConectores::class.java)

    /** GET /central/estudiantes con filtros opcionales y paginación. */
    @Throws(IOException::class)
    fun central(
        nombre: String? = null,
        dni: String? = null,
        offset: Int = 0,
        limit: Int = 100
    ): Pagina {
        val ruta = url("/central/estudiantes")
            .newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
        if (!nombre.isNullOrBlank()) ruta.addQueryParameter("nombre", nombre.trim())
        if (!dni.isNullOrBlank()) ruta.addQueryParameter("dni", dni.trim())
        return gson.fromJson(get(ruta.build()), Pagina::class.java)
    }

    /** GET /connectors/{connectorId}/estudiantes */
    @Throws(IOException::class)
    fun origen(
        connectorId: String,
        nombre: String? = null,
        dni: String? = null,
        offset: Int = 0,
        limit: Int = 100
    ): Pagina {
        val ruta = url("/connectors/$connectorId/estudiantes")
            .newBuilder()
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
        if (!nombre.isNullOrBlank()) ruta.addQueryParameter("nombre", nombre.trim())
        if (!dni.isNullOrBlank()) ruta.addQueryParameter("dni", dni.trim())
        return gson.fromJson(get(ruta.build()), Pagina::class.java)
    }

    // --- CRUD de un origen: /connectors/{id}/estudiantes -----------------

    /** POST /connectors/{connectorId}/estudiantes - alta, responde 201. */
    @Throws(IOException::class)
    fun crearEstudiante(connectorId: String, datos: EstudiantePayload): Estudiante =
        gson.fromJson(
            enviar("POST", "/connectors/$connectorId/estudiantes", gson.toJson(datos)),
            Estudiante::class.java
        )

    /** GET /connectors/{connectorId}/estudiantes/{rowId}. */
    @Throws(IOException::class)
    fun obtenerEstudiante(connectorId: String, rowId: Int): Estudiante =
        gson.fromJson(get("/connectors/$connectorId/estudiantes/$rowId"), Estudiante::class.java)

    /** PUT /connectors/{connectorId}/estudiantes/{rowId} - actualización parcial. */
    @Throws(IOException::class)
    fun actualizarEstudiante(
        connectorId: String,
        rowId: Int,
        cambios: EstudianteUpdate
    ): Estudiante = gson.fromJson(
        enviar("PUT", "/connectors/$connectorId/estudiantes/$rowId", gson.toJson(cambios)),
        Estudiante::class.java
    )

    /** DELETE /connectors/{connectorId}/estudiantes/{rowId} - responde 204 sin cuerpo. */
    @Throws(IOException::class)
    fun borrarEstudiante(connectorId: String, rowId: Int) {
        enviar("DELETE", "/connectors/$connectorId/estudiantes/$rowId", null)
    }

    // --- Central ----------------------------------------------------------

    /** GET /central/estudiantes/{dni} - devuelve null si responde 404. */
    @Throws(IOException::class)
    fun porDni(dni: String): EstudianteCentral? {
        val peticion = Request.Builder()
            .url(urlBase + "/central/estudiantes/" + dni.trim())
            .get()
            .build()
        http.newCall(peticion).execute().use { respuesta ->
            if (respuesta.code == 404) return null
            comprobar(respuesta)
            return gson.fromJson(respuesta.body!!.string(), EstudianteCentral::class.java)
        }
    }

    /** POST /sync - sincroniza todos los conectores. */
    @Throws(IOException::class)
    fun sincronizar(): SyncResumen = gson.fromJson(enviar("POST", "/sync", null), SyncResumen::class.java)

    /** POST /sync/{connectorId} - sincroniza un solo conector. */
    @Throws(IOException::class)
    fun sincronizarConector(connectorId: String): SyncResumen =
        gson.fromJson(enviar("POST", "/sync/$connectorId", null), SyncResumen::class.java)

    /** GET /central/consistencia - conteos y huellas MariaDB vs SQLite. */
    @Throws(IOException::class)
    fun consistencia(): String = get("/central/consistencia")

    private fun get(ruta: String): String = get(url(ruta))

    private fun get(ruta: HttpUrl): String {
        val peticion = Request.Builder().url(ruta).get().build()
        http.newCall(peticion).execute().use { respuesta ->
            comprobar(respuesta)
            return respuesta.body!!.string()
        }
    }

    /**
     * Petición con cuerpo JSON opcional. [cuerpo] en null significa sin
     * cuerpo (POST /sync y DELETE); el 204 devuelve cadena vacía.
     */
    private fun enviar(metodo: String, ruta: String, cuerpo: String?): String {
        val peticion = Request.Builder()
            .url(url(ruta))
            .method(metodo, cuerpo?.toRequestBody(JSON) ?: ByteArray(0).toRequestBody(null))
            .build()
        http.newCall(peticion).execute().use { respuesta ->
            comprobar(respuesta)
            return respuesta.body?.string().orEmpty()
        }
    }

    private fun url(ruta: String): HttpUrl =
        (urlBase + ruta).toHttpUrlOrNull()
            ?: throw IllegalStateException("URL no válida: $urlBase$ruta")

    private fun comprobar(respuesta: Response) {
        if (!respuesta.isSuccessful) {
            val cuerpo = respuesta.body?.string().orEmpty()
            throw IOException("HTTP ${respuesta.code}: $cuerpo")
        }
    }

    companion object {
        /** El emulador de Android alcanza al host por 10.0.2.2. */
        const val DEFAULT_URL = "http://10.0.2.2:8000"

        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun normalizar(valor: String): String {
            // Campo vacío o en blanco: se cae a la URL por defecto del emulador.
            val limpio = valor.trim().ifBlank { DEFAULT_URL }
            return if (limpio.endsWith("/")) limpio.dropLast(1) else limpio
        }
    }
}
