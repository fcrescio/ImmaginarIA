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

class StoryAssetExtractor(
    private val appContext: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    private suspend fun callLLM(prompt: String): JSONArray? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("StoryAssetExtractor", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return@withContext null
        }
        runCatching {
            val root = JSONObject().apply {
                put("model", "openrouter/sonoma-sky-alpha")
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            },
                        )
                    },
                )
                if (SettingsManager.useStructuredOutputs(appContext)) {
                    val schema = JSONObject(
                        """
                        {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "name": { "type": "string" },
                              "description": { "type": "string" }
                            },
                            "required": ["name", "description"]
                          }
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
                                    put("name", "assets")
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
                LlmLogger.log(appContext, "StoryAssetExtractor", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("StoryAssetExtractor", "HTTP ${resp.code}")
                    crashlytics.log("OpenRouter extract failed: ${resp.code}")
                    return@withContext null
                }
                val json = JSONObject(respBody ?: return@withContext null)
                val choices = json.optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                val message = choices.getJSONObject(0).optJSONObject("message")
                    ?: return@withContext null
                val parsed = message.opt("parsed")
                when (parsed) {
                    is JSONArray -> parsed
                    is JSONObject -> JSONArray().put(parsed)
                    else -> {
                        val content = message.optString("content")
                        if (content.isBlank()) return@withContext null
                        JSONArray(content)
                    }
                }
            }
        }.getOrElse { e ->
            Log.e("StoryAssetExtractor", "LLM error", e)
            LlmLogger.log(appContext, "StoryAssetExtractor", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }

    suspend fun extractCharacters(story: String, sourceLanguage: String? = null): List<CharacterAsset> {
        val languageHint = sourceLanguage?.takeIf { it.isNotBlank() && !it.equals("english", ignoreCase = true) }
            ?.let {
                "The original story language is $it. Translate names and descriptions into natural English while preserving culturally specific details."
            }
            ?: "Ensure every name and description you output is written in clear, natural English."
        val prompt = """
            Extract the distinct characters from the following story rewrite.
            Provide concise, visually descriptive summaries for each character.
            $languageHint
            Respond with a JSON array of objects where each item has the keys "name" and "description" and both values are in English.
            Story (English rewrite):
            $story
        """.trimIndent()
        val arr = callLLM(prompt) ?: return emptyList()
        val result = mutableListOf<CharacterAsset>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val desc = obj.optString("description").trim()
            if (name.isNotEmpty() && desc.isNotEmpty()) {
                result.add(
                    CharacterAsset(
                        name = name,
                        description = desc,
                        nameEnglish = name,
                        descriptionEnglish = desc,
                    )
                )
            }
        }
        return result
    }

    suspend fun extractEnvironments(story: String, sourceLanguage: String? = null): List<EnvironmentAsset> {
        val languageHint = sourceLanguage?.takeIf { it.isNotBlank() && !it.equals("english", ignoreCase = true) }
            ?.let {
                "The original story language is $it. Translate location names and descriptions into natural English while keeping important cultural nuances."
            }
            ?: "Ensure every environment name and description you output is written in clear, natural English."
        val prompt = """
            Extract the key environments and settings from the following story rewrite.
            Provide concise, visually descriptive summaries for each environment.
            $languageHint
            Respond with a JSON array of objects where each item has the keys "name" and "description" and both values are in English.
            Story (English rewrite):
            $story
        """.trimIndent()
        val arr = callLLM(prompt) ?: return emptyList()
        val result = mutableListOf<EnvironmentAsset>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val desc = obj.optString("description").trim()
            if (name.isNotEmpty() && desc.isNotEmpty()) {
                result.add(
                    EnvironmentAsset(
                        name = name,
                        description = desc,
                        nameEnglish = name,
                        descriptionEnglish = desc,
                    )
                )
            }
        }
        return result
    }
}
