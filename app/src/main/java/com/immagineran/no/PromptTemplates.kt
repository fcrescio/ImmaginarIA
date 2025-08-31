package com.immagineran.no

import android.content.Context
import androidx.annotation.RawRes

/**
 * Utility for loading prompt templates from raw resources and applying data.
 */
object PromptTemplates {
    /**
     * Loads a template from [resId] and replaces placeholders with [style] and [description].
     */
    fun load(context: Context, @RawRes resId: Int, style: ImageStyle, description: String): String {
        val template = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        return template
            .replace("{STYLE}", style.prompt)
            .replace("{DESCRIPTION}", description)
    }
}

