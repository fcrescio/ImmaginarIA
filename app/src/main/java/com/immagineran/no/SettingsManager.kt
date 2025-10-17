package com.immagineran.no

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "app_settings"
private const val KEY_TRANSCRIPTION_METHOD = "transcription_method"
private const val KEY_IMAGE_STYLE = "image_style"
private const val KEY_IMAGE_PROVIDER = "image_provider"
private const val KEY_GENERATE_ASSET_IMAGES = "generate_asset_images"
private const val KEY_GENERATE_CHARACTER_IMAGES = "generate_character_images"
private const val KEY_GENERATE_ENVIRONMENT_IMAGES = "generate_environment_images"

/**
 * Persists user-configurable settings.
 */
object SettingsManager {
    fun getTranscriptionMethod(context: Context): TranscriptionMethod {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_TRANSCRIPTION_METHOD, null)
        return name?.let {
            runCatching { TranscriptionMethod.valueOf(it) }.getOrNull()
        } ?: TranscriptionMethod.GROQ
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

    fun getImageProvider(context: Context): ImageProvider {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_IMAGE_PROVIDER, ImageProvider.OPENROUTER.name)
        return runCatching { ImageProvider.valueOf(name!!) }.getOrDefault(ImageProvider.OPENROUTER)
    }

    fun setImageProvider(context: Context, provider: ImageProvider) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IMAGE_PROVIDER, provider.name).apply()
    }

    private fun legacyAssetGenerationPreference(prefs: SharedPreferences): Boolean? {
        return if (prefs.contains(KEY_GENERATE_ASSET_IMAGES)) {
            prefs.getBoolean(KEY_GENERATE_ASSET_IMAGES, true)
        } else {
            null
        }
    }

    /**
     * Returns whether character images should be generated.
     */
    fun isCharacterImageGenerationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = legacyAssetGenerationPreference(prefs)
        return when {
            prefs.contains(KEY_GENERATE_CHARACTER_IMAGES) ->
                prefs.getBoolean(KEY_GENERATE_CHARACTER_IMAGES, false)

            legacy != null -> legacy

            else -> false
        }
    }

    /**
     * Persists the character image generation preference.
     */
    fun setCharacterImageGenerationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GENERATE_CHARACTER_IMAGES, enabled).apply()
    }

    /**
     * Returns whether environment images should be generated.
     */
    fun isEnvironmentImageGenerationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = legacyAssetGenerationPreference(prefs)
        return when {
            prefs.contains(KEY_GENERATE_ENVIRONMENT_IMAGES) ->
                prefs.getBoolean(KEY_GENERATE_ENVIRONMENT_IMAGES, false)

            legacy != null -> legacy

            else -> false
        }
    }

    /**
     * Persists the environment image generation preference.
     */
    fun setEnvironmentImageGenerationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GENERATE_ENVIRONMENT_IMAGES, enabled).apply()
    }

    /**
     * Structured outputs are always enabled.
     */
    @Suppress("UNUSED_PARAMETER")
    fun useStructuredOutputs(context: Context): Boolean = true
}

