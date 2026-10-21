package es.r01.mobile

/** Modelo de estudiante tal y como lo devuelve la API de R01. */
data class Estudiante(
    val id: Int = 0,
    val dni: String = "",
    val nombre: String = "",
    val casa: String = "",
    val patronus: String = ""
)

/** Fila de la central con trazabilidad de origen. */
data class EstudianteCentral(
    val id: Int = 0,
    val origen: String = "",
    val id_origen: Int = 0,
    val dni: String = "",
    val nombre: String = "",
    val casa: String = "",
    val patronus: String = "",
    val backend: String? = null
)

/** Página paginada: /central/estudiantes y /connectors/{id}/estudiantes. */
data class Pagina(
    val items: List<EstudianteCentral> = emptyList(),
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int = 0,
    val backend: String? = null,
    val conector: String? = null
)

/** Respuesta de GET /health. */
data class Salud(
    val status: String = "",
    val reto: String = "R01",
    val caidos: List<String> = emptyList()
)

/** Resumen de POST /sync y POST /sync/{connector_id}. */
data class SyncResumen(
    val conector: String = "",
    val leidos: Int = 0,
    val insertados: Int = 0,
    val actualizados: Int = 0,
    val errores: Int = 0
)

/**
 * Cuerpo de alta (POST /connectors/{connector_id}/estudiantes, HTTP 201).
 * El backend rechaza campos extra, sólo se envían los declarados.
 */
data class EstudiantePayload(
    val id: Int? = null,
    val dni: String = "",
    val nombre: String = "",
    val casa: String = "",
    val patronus: String = ""
)

/**
 * Actualización parcial (PUT /connectors/{connector_id}/estudiantes/{row_id}).
 * Gson omite los campos nulos, así que sólo viajan los informados.
 */
data class EstudianteUpdate(
    val dni: String? = null,
    val nombre: String? = null,
    val casa: String? = null,
    val patronus: String? = null
)

/** Estado de un conector: GET /connectors -> {"conectores": {id: ...}}. */
data class ConectorInfo(
    val id: String = "",
    val nombre: String = "",
    val dialect: String = "",
    val url: String = "",
    val disponible: Boolean = false,
    val detalle: String = ""
)

/** Envoltorio del mapa devuelto por GET /connectors. */
data class ListaConectores(
    val conectores: Map<String, ConectorInfo> = emptyMap()
)

/** Respuesta de error unificada del backend: {"error": {...}}. */
data class ErrorApi(
    val codigo: String = "",
    val mensaje: String = "",
    val detalle: Any? = null
)
