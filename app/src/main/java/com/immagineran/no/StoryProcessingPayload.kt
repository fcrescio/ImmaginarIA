package com.immagineran.no

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * Serializable payload describing a story processing request.
 */
data class StoryProcessingPayload(
    val storyId: Long,
    val prompt: String,
    val transcriptions: List<String>,
    val userTitle: String,
    val timestamp: Long,
    val segmentPaths: List<String>,
)

private const val PAYLOAD_DIR = "processing_payloads"

private val gson = Gson()

/**
 * Persists a [StoryProcessingPayload] to internal storage and returns the created [File].
 */
fun Context.writeStoryProcessingPayload(payload: StoryProcessingPayload): File {
    val dir = File(filesDir, PAYLOAD_DIR).apply { mkdirs() }
    val fileName = "payload_${payload.storyId}_${UUID.randomUUID()}.json"
    return File(dir, fileName).also { file ->
        file.writeText(gson.toJson(payload))
    }
}

/**
 * Reads a [StoryProcessingPayload] from the given [file].
 */
fun readStoryProcessingPayload(file: File): StoryProcessingPayload =
    gson.fromJson(file.readText(), StoryProcessingPayload::class.java)
