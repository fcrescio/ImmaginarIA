package com.immagineran.no

import android.util.Base64
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ImageGenerator(
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    /**
     * Generates an image based on the given [prompt].
     * Retries up to five times if the response does not contain an image.
     */
    suspend fun generate(prompt: String, file: File): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("ImageGenerator", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return@withContext null
        }
        runCatching {
            val messages = JSONArray().apply {
                put(message(prompt))
            }
            repeat(5) { attempt ->
                val root = JSONObject().apply {
                    put("model", "google/gemini-2.5-flash-image-preview")
                    put("messages", messages)
                }
                val body = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $key")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e("ImageGenerator", "HTTP ${'$'}{resp.code}")
                        crashlytics.log("OpenRouter image failed: ${'$'}{resp.code}")
                        return@withContext null
                    }
                    val json = JSONObject(resp.body?.string() ?: return@withContext null)
                    val choices = json.optJSONArray("choices") ?: return@withContext null
                    if (choices.length() == 0) return@withContext null
                    val message = choices.getJSONObject(0).optJSONObject("message") ?: return@withContext null
                    val b64 = extractBase64(message)
                    if (b64 != null) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        file.outputStream().use { it.write(bytes) }
                        return@withContext file.absolutePath
                    }
                }
                messages.put(message("you did not generate any image, retry"))
            }
            null
        }.getOrElse { e ->
            Log.e("ImageGenerator", "Generation error", e)
            crashlytics.recordException(e)
            null
        }
    }

    private fun extractBase64(message: JSONObject): String? {
        message.optJSONArray("images")?.let { images ->
            if (images.length() > 0) {
                val url = images.getJSONObject(0)
                    .optJSONObject("image_url")
                    ?.optString("url")
                    ?: ""
                val b64 = url.substringAfter("base64,", "")
                if (b64.isNotBlank()) return b64
            }
        }
        message.optJSONArray("content")?.let { content ->
            if (content.length() > 0) {
                val imgObj = content.getJSONObject(0)
                val b64 = imgObj.optString("image_base64")
                if (b64.isNotBlank()) return b64
            }
        }
        return null
    }

    private fun message(text: String): JSONObject = JSONObject().apply {
        put("role", "user")
        put("content", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            })
        })
    }
}
