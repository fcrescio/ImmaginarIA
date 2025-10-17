package com.immagineran.no

import android.content.Context
import androidx.annotation.RawRes

/**
 * Utility for loading prompt templates from raw resources and applying data.
 */
object PromptTemplates {
    /**
     * Loads the raw template referenced by [resId] without applying substitutions.
     */
    fun load(context: Context, @RawRes resId: Int): String =
        context.resources.openRawResource(resId).bufferedReader().use { it.readText() }

    /**
     * Loads a template from [resId] and replaces placeholders with values from [placeholders].
     */
    fun load(context: Context, @RawRes resId: Int, placeholders: Map<String, String>): String {
        return placeholders.entries.fold(load(context, resId)) { acc, (key, value) ->
            acc.replace("{$key}", value)
        }
    }

    /**
     * Loads a template from [resId] and replaces placeholders with [style] and [description].
     */
    fun load(context: Context, @RawRes resId: Int, style: ImageStyle, description: String): String {
        return load(
            context,
            resId,
            mapOf(
                "STYLE" to style.prompt,
                "DESCRIPTION" to description,
            ),
        )
    }
}

