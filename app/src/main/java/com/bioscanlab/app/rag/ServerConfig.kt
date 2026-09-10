package com.bioscanlab.app.rag

import android.content.Context
import com.bioscanlab.app.BuildConfig

/**
 * Gestiona y persiste la URL del servidor backend RAG.
 *
 * Permite cambiar la URL en tiempo de ejecución (por ejemplo, al cambiar de
 * túnel Cloudflare o servidor en la nube) sin tener que recompilar el APK.
 */
object ServerConfig {
    private const val PREFS_NAME = "bioscanlab_config"
    private const val KEY_SERVER_URL = "server_url"

    @Volatile
    private var cachedUrl: String? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SERVER_URL, null)
        cachedUrl = if (!saved.isNullOrBlank()) saved.trim().removeSuffix("/") else null
    }

    fun getUrl(context: Context? = null): String {
        cachedUrl?.let { return it }
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_SERVER_URL, null)
            if (!saved.isNullOrBlank()) {
                val clean = saved.trim().removeSuffix("/")
                cachedUrl = clean
                return clean
            }
        }
        val def = getDefaultUrl()
        cachedUrl = def
        return def
    }

    fun setUrl(context: Context, url: String) {
        val clean = url.trim().removeSuffix("/")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, clean)
            .apply()
        cachedUrl = clean
    }

    fun resetToDefault(context: Context): String {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SERVER_URL)
            .apply()
        cachedUrl = null
        val def = getDefaultUrl()
        cachedUrl = def
        return def
    }

    fun getDefaultUrl(): String {
        val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.HARDWARE.contains("goldfish") ||
            android.os.Build.HARDWARE.contains("ranchu")

        return if (isEmulator) "http://10.0.2.2:8080" else BuildConfig.BACKEND_URL
    }
}
