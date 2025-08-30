package com.immagineran.no

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

/**
 * Transcriber implementation that uses Groq's Whisper Turbo model.
 */
class GroqTranscriber(
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : Transcriber {
    override suspend fun transcribe(file: File): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank()) {
            Log.e("GroqTranscriber", "Missing Groq API key")
            crashlytics.log("Groq API key missing")
            return@withContext null
        }

        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("audio/m4a".toMediaType())
                )
                .addFormDataPart("model", "whisper-large-v3-turbo")
                .build()

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("GroqTranscriber", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("Groq transcription failed: ${'$'}{resp.code}")
                    return@withContext null
                }
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                json.optString("text", null)
            }
        }.getOrElse { e ->
            Log.e("GroqTranscriber", "Transcription error", e)
            crashlytics.recordException(e)
            null
        }
    }
}
