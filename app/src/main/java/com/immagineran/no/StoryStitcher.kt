package com.immagineran.no

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

/**
 * Uses an LLM via OpenRouter to stitch transcribed segments into a story.
 */
class StoryStitcher(
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    suspend fun stitch(prompt: String, segments: List<String>): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("StoryStitcher", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return@withContext null
        }

        runCatching {
            val content = StringBuilder(prompt)
            segments.forEach { content.append("\n- ").append(it) }

            val root = JSONObject().apply {
                put("model", "mistralai/mistral-nemo")
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", content.toString())
                            }
                        )
                    }
                )
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
                    Log.e("StoryStitcher", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter stitch failed: ${'$'}{resp.code}")
                    return@withContext null
                }
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                val choices = json.optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                val message = choices.getJSONObject(0).optJSONObject("message")
                message?.optString("content")
            }
        }.getOrElse { e ->
            Log.e("StoryStitcher", "LLM error", e)
            crashlytics.recordException(e)
            null
        }
    }
}
