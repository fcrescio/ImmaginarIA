package com.immagineran.no

import android.content.Context
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
    private val appContext: Context,
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
            val content = buildString {
                appendLine(
                    "You are a multilingual storyteller. Analyze the provided prompt and " +
                        "transcript segments to detect the primary language used."
                )
                appendLine(
                    "Write a cohesive narrative in that same language, keeping the voice and " +
                        "details consistent."
                )
                appendLine(
                    "Also produce an English translation of the narrative."
                )
                appendLine(
                    "Respond strictly with JSON containing the keys 'language', 'story_original', " +
                        "and 'story_english'."
                )
                appendLine("Language should be identified using either the common name or ISO 639-1 code.")
                appendLine()
                appendLine("Prompt:")
                appendLine(prompt)
                appendLine()
                appendLine("Segments:")
                segments.forEach { segment ->
                    append("- ")
                    appendLine(segment)
                }
            }

            val root = JSONObject().apply {
                put("model", "mistralai/mistral-nemo")
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", content.toString())
                            },
                        )
                    },
                )
                if (SettingsManager.useStructuredOutputs(appContext)) {
                    val schema = JSONObject(
                        """
                        {
                          "type": "object",
                          "properties": {
                            "language": { "type": "string" },
                            "story_original": { "type": "string" },
                            "story_english": { "type": "string" }
                          },
                          "required": ["language", "story_original", "story_english"]
                        }
                        """.trimIndent(),
                    )
                    put(
                        "response_format",
                        JSONObject().apply {
                            put("type", "json_schema")
                            put(
                                "json_schema",
                                JSONObject().apply {
                                    put("name", "story")
                                    put("schema", schema)
                                },
                            )
                        },
                    )
                }
            }

            val reqJson = root.toString()
            val body = reqJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string()
                LlmLogger.log(appContext, "StoryStitcher", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("StoryStitcher", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter stitch failed: ${'$'}{resp.code}")
                    return@withContext null
                }
                val json = JSONObject(respBody ?: return@withContext null)
                val choices = json.optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                val message = choices.getJSONObject(0).optJSONObject("message")
                val parsed = message?.opt("parsed")
                when (parsed) {
                    is JSONObject, is JSONArray -> parsed.toString()
                    is String -> parsed
                    else -> {
                        val content = message?.opt("content")
                        when (content) {
                            is JSONObject, is JSONArray -> content.toString()
                            is String -> content
                            else -> null
                        }
                    }
                }
            }
        }.getOrElse { e ->
            Log.e("StoryStitcher", "LLM error", e)
            LlmLogger.log(appContext, "StoryStitcher", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }
}
