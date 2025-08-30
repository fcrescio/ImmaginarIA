package com.immagineran.no

import android.content.Context

private const val PREFS_NAME = "app_settings"
private const val KEY_TRANSCRIPTION_METHOD = "transcription_method"

/**
 * Persists user-configurable settings.
 */
object SettingsManager {
    fun getTranscriptionMethod(context: Context): TranscriptionMethod {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_TRANSCRIPTION_METHOD, null)
        return name?.let {
            runCatching { TranscriptionMethod.valueOf(it) }.getOrNull()
        } ?: TranscriptionMethod.LOCAL
    }

    fun setTranscriptionMethod(context: Context, method: TranscriptionMethod) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TRANSCRIPTION_METHOD, method.name).apply()
    }
}

