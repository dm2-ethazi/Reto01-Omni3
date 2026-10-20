package com.omni3.gestorhogwartsmovil.data

import android.content.Context
import com.omni3.gestorhogwartsmovil.BuildConfig
import com.omni3.gestorhogwartsmovil.Student
import java.net.URI

/** Guarda la dirección elegida y la dirección de la última caché sincronizada. */
object ApiUrlSettings {
    private const val PREFERENCES = "api_url"
    private const val SELECTED_URL = "selected_url"
    private const val CACHED_URL = "cached_url"

    fun get(context: Context): String = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString(SELECTED_URL, null) ?: BuildConfig.API_BASE_URL

    fun save(context: Context, text: String): Boolean {
        val url = text.trim().trimEnd('/')
        val address = runCatching { URI(url) }.getOrNull() ?: return false
        if (address.scheme?.lowercase() !in listOf("http", "https") ||
            address.host.isNullOrBlank() || address.userInfo != null ||
            address.rawQuery != null || address.rawFragment != null) return false

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_URL, url).apply()
        return true
    }

    fun cacheMatches(context: Context): Boolean {
        val cachedUrl = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(CACHED_URL, null) ?: BuildConfig.API_BASE_URL
        return cachedUrl == get(context)
    }

    fun markCache(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(CACHED_URL, get(context)).apply()
    }
}

/** Muestra la caché SQLite solo cuando pertenece a la API seleccionada. */
class UrlScopedStudentCache(
    private val context: Context,
    private val cache: StudentCache
) : StudentCache {
    override suspend fun read(): StudentSnapshot? =
        if (ApiUrlSettings.cacheMatches(context)) cache.read() else null

    override suspend fun replaceComplete(students: List<Student>, syncedAt: Long) {
        cache.replaceComplete(students, syncedAt)
        ApiUrlSettings.markCache(context)
    }
}
