package com.immagineran.no

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists verbose logs of LLM interactions.
 */
object LlmLogger {
    private const val FILE_NAME = "llm_logs.txt"

    /**
     * Appends a log entry containing the raw [request] and [response].
     * Any embedded base64 data is stripped to keep logs small.
     */
    fun log(context: Context, tag: String, request: String, response: String?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = buildString {
            append(timestamp).append(' ').append('[').append(tag).append("]\n")
            append("REQUEST:\n").append(stripBase64(request)).append("\n")
            response?.let { append("RESPONSE:\n").append(stripBase64(it)).append("\n") }
            append("\n")
        }
        context.openFileOutput(FILE_NAME, Context.MODE_APPEND).use { out ->
            out.write(entry.toByteArray())
        }
    }

    /**
     * Removes base64 encoded image data from [content].
     */
    private fun stripBase64(content: String): String {
        val urlRegex = Regex("data:image/[^;]+;base64,[A-Za-z0-9+/=\\r\\n]+")
        val fieldRegex = Regex("\"image_base64\"\\s*:\\s*\"[A-Za-z0-9+/=\\r\\n]+\"")
        return content
            .replace(urlRegex) { match ->
                match.value.substringBefore("base64,") + "<base64 removed>"
            }
            .replace(fieldRegex, "\"image_base64\":\"<base64 removed>\"")
    }

    /**
     * Returns the file containing the logs.
     */
    fun getLogFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /**
     * Clears all stored logs.
     */
    fun clear(context: Context) {
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).close()
    }
}

