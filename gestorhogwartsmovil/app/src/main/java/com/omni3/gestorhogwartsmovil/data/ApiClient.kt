package com.omni3.gestorhogwartsmovil.data

import com.omni3.gestorhogwartsmovil.Student
import com.omni3.gestorhogwartsmovil.BuildConfig

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Error HTTP que conserva el código para decidir si procede recuperar la caché.
 *
 * @author koldo
 * @description Error HTTP que conserva el código para decidir si procede recuperar la caché.
 */
class ApiHttpException(val statusCode: Int, message: String) : Exception(message)

/**
 * Cliente HTTP del contrato REST de alumnos. Sus operaciones son bloqueantes.
 *
 * @author koldo
 * @description Cliente HTTP del contrato REST de alumnos. Sus operaciones son bloqueantes.
 */
object ApiClient {

    private val BASE_URL = BuildConfig.API_BASE_URL
    private const val TIMEOUT_MS = 5_000

    /**
     * Obtiene la colección completa de GET /alumnos, sin filtros ni paginación.
     *
     * Debe invocarse fuera del hilo principal; [StudentRepository] se encarga de ello.
     *
     * @author koldo
     * @description Obtiene la colección completa de GET /alumnos, sin filtros ni paginación.
     * @throws ApiHttpException si el servidor responde con un código distinto de 2xx.
     * @throws java.io.IOException si falla la conexión o se agota el tiempo de espera.
     * @throws org.json.JSONException si la respuesta no tiene el formato esperado.
     */
    fun listarAlumnos(): List<Student> = parsearAlumnos(solicitar("$BASE_URL/alumnos"))

    /**
     * Convierte el array JSON sin confundir una respuesta inválida con una lista vacía.
     *
     * @author koldo
     * @description Convierte el array JSON sin confundir una respuesta inválida con una lista vacía.
     */
    private fun parsearAlumnos(json: String): List<Student> {
        val alumnos = JSONArray(json)
        return List(alumnos.length()) { index ->
            alumnos.getJSONObject(index).toStudent()
        }
    }

    /**
     * Ejecuta GET y libera siempre la conexión, incluso ante errores HTTP o de lectura.
     *
     * @author koldo
     * @description Ejecuta GET y libera siempre la conexión, incluso ante errores HTTP o de lectura.
     */
    private fun solicitar(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        // El túnel de desarrollo debe devolver JSON, sin la página de aviso de ngrok.
        connection.setRequestProperty("ngrok-skip-browser-warning", "true")
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS

        return try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                // Un fallo al leer el detalle no debe ocultar un 401/403 como error de conexión.
                val body = runCatching { leerRespuesta(connection, statusCode) }.getOrDefault("")
                throw crearErrorApi(body, statusCode)
            }
            leerRespuesta(connection, statusCode)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Cierra el stream de éxito o de error después de leerlo.
     *
     * @author koldo
     * @description Cierra el stream de éxito o de error después de leerlo.
     */
    private fun leerRespuesta(connection: HttpURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    /**
     * Usa el detalle enviado por el servidor si existe; el código HTTP siempre se conserva.
     *
     * @author koldo
     * @description Usa el detalle enviado por el servidor si existe; el código HTTP siempre se conserva.
     */
    private fun crearErrorApi(body: String, statusCode: Int): ApiHttpException {
        val detail = runCatching { JSONObject(body).optString("detail") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return ApiHttpException(statusCode, detail ?: "HTTP $statusCode")
    }

    /**
     * Mapea únicamente los campos del contrato existente; los opcionales pueden faltar.
     *
     * @author koldo
     * @description Mapea únicamente los campos del contrato existente; los opcionales pueden faltar.
     */
    private fun JSONObject.toStudent() = Student(
        id = getString("id"),
        nombre = getString("nombre"),
        apellido = optNullableString("apellido"),
        sexo = optNullableString("sexo"),
        patronus = optNullableString("patronus"),
        casa = optNullableString("casa"),
        imagen = optNullableString("imagen")
    )

    /**
     * Unifica valores ausentes, vacíos y null del JSON en un null de Kotlin.
     *
     * @author koldo
     * @description Unifica valores ausentes, vacíos y null del JSON en un null de Kotlin.
     */
    private fun JSONObject.optNullableString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

}
