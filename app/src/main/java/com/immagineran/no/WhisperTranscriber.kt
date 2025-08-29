package com.immagineran.no

import android.content.Context
import io.github.aallam.whisper.WhisperContext
import io.github.aallam.whisper.WhisperResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Utility object that performs on-device transcription using a small
 * multilingual Whisper model. The model is downloaded on demand and
 * cached in the application's private storage.
 */
object WhisperTranscriber {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    private const val MODEL_FILE = "ggml-tiny.bin"

    private val client = OkHttpClient()
    @Volatile private var whisper: WhisperContext? = null

    /** Download the Whisper model if not already available. */
    private suspend fun ensureModel(context: Context): File = withContext(Dispatchers.IO) {
        val model = File(context.filesDir, MODEL_FILE)
        if (!model.exists()) {
            val request = Request.Builder().url(MODEL_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Failed to download model: ${'$'}{response.code}")
                response.body?.byteStream()?.use { input ->
                    model.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        model
    }

    /** Perform transcription of [audioFile] and return the text. */
    suspend fun transcribe(context: Context, audioFile: File): String = withContext(Dispatchers.Default) {
        val modelFile = ensureModel(context)
        val ctx = whisper ?: WhisperContext.create(modelFile.absolutePath).also { whisper = it }
        val result: WhisperResult = ctx.transcribe(audioFile.absolutePath)
        result.text.trim()
    }
}
