package com.immagineran.no

import android.content.Context

private const val PREFS_NAME = "app_settings"
private const val KEY_TRANSCRIPTION_METHOD = "transcription_method"
private const val KEY_IMAGE_STYLE = "image_style"
private const val KEY_GENERATE_ASSET_IMAGES = "generate_asset_images"

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

    /**
     * Retrieves the preferred [ImageStyle].
     */
    fun getImageStyle(context: Context): ImageStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_IMAGE_STYLE, ImageStyle.PHOTOREALISTIC.name)
        return runCatching { ImageStyle.valueOf(name!!) }.getOrDefault(ImageStyle.PHOTOREALISTIC)
    }

    /**
     * Persists the preferred [ImageStyle].
     */
    fun setImageStyle(context: Context, style: ImageStyle) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IMAGE_STYLE, style.name).apply()
    }

    /**
     * Returns whether character and environment images should be generated.
     */
    fun isAssetImageGenerationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GENERATE_ASSET_IMAGES, true)
    }

    /**
     * Persists the asset image generation preference.
     */
    fun setAssetImageGenerationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GENERATE_ASSET_IMAGES, enabled).apply()
    }

    /**
     * Structured outputs are always enabled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun useStructuredOutputs(context: Context): Boolean = true
}

