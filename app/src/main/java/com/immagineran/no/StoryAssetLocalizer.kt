package com.immagineran.no

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class StoryAssetLocalizer(
    private val appContext: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    private suspend fun callAssetLlm(prompt: String): JSONArray? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("StoryAssetLocalizer", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return@withContext null
        }

        runCatching {
            val root = JSONObject().apply {
                put("model", "mistralai/mistral-nemo")
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
                              "id": { "type": "string" },
                              "name": { "type": "string" },
                              "description": { "type": "string" }
                            },
                            "required": ["id", "name", "description"]
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
                                    put("name", "localized_assets")
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
                LlmLogger.log(appContext, "StoryAssetLocalizer", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("StoryAssetLocalizer", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter asset localization failed: ${'$'}{resp.code}")
                    return@withContext null
                }
                val json = JSONObject(respBody ?: return@withContext null)
                val choices = json.optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                val message = choices.getJSONObject(0).optJSONObject("message") ?: return@withContext null
                val parsed = message.opt("parsed")
                when (parsed) {
                    is JSONArray -> parsed
                    is JSONObject -> JSONArray().put(parsed)
                    else -> {
                        val content = message.optString("content")
                        if (content.isBlank()) return@withContext null
                        runCatching { JSONArray(content) }
                            .onFailure { e ->
                                crashlytics.log("StoryAssetLocalizer asset parse failure")
                                crashlytics.recordException(e)
                                Log.e("StoryAssetLocalizer", "Failed to parse asset localization response", e)
                            }
                            .getOrNull()
                    }
                }
            }
        }.getOrElse { e ->
            Log.e("StoryAssetLocalizer", "LLM error", e)
            LlmLogger.log(appContext, "StoryAssetLocalizer", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }

    private suspend fun callTitleLlm(prompt: String): JSONObject? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("StoryAssetLocalizer", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return@withContext null
        }

        runCatching {
            val root = JSONObject().apply {
                put("model", "mistralai/mistral-nemo")
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
                          "type": "object",
                          "properties": {
                            "title": { "type": "string" }
                          },
                          "required": ["title"]
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
                                    put("name", "localized_title")
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
                LlmLogger.log(appContext, "StoryAssetLocalizer", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("StoryAssetLocalizer", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter title localization failed: ${'$'}{resp.code}")
                    return@withContext null
                }
                val json = JSONObject(respBody ?: return@withContext null)
                val choices = json.optJSONArray("choices") ?: return@withContext null
                if (choices.length() == 0) return@withContext null
                val message = choices.getJSONObject(0).optJSONObject("message") ?: return@withContext null
                val parsed = message.opt("parsed")
                when (parsed) {
                    is JSONObject -> parsed
                    is JSONArray -> parsed.optJSONObject(0)
                    is String -> runCatching { JSONObject(parsed) }.getOrNull()
                    else -> {
                        val content = message.optString("content")
                        if (content.isBlank()) return@withContext null
                        runCatching { JSONObject(content) }
                            .onFailure { e ->
                                crashlytics.log("StoryAssetLocalizer title parse failure")
                                crashlytics.recordException(e)
                                Log.e("StoryAssetLocalizer", "Failed to parse title localization response", e)
                            }
                            .getOrNull()
                    }
                }
            }
        }.getOrElse { e ->
            Log.e("StoryAssetLocalizer", "LLM error", e)
            LlmLogger.log(appContext, "StoryAssetLocalizer", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }

    private fun shouldLocalize(language: String?): Boolean {
        if (language.isNullOrBlank()) return false
        val normalized = language.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        if (normalized == "english") return false
        if (normalized.startsWith("en")) return false
        return true
    }

    suspend fun localizeCharacters(
        assets: List<CharacterAsset>,
        language: String?,
    ): List<CharacterAsset> {
        if (!shouldLocalize(language) || assets.isEmpty()) {
            return assets
        }
        val items = JSONArray()
        assets.forEachIndexed { index, asset ->
            val englishName = asset.nameEnglish?.takeIf { it.isNotBlank() } ?: asset.name
            val englishDescription = asset.descriptionEnglish?.takeIf { it.isNotBlank() } ?: asset.description
            if (englishName.isBlank() && englishDescription.isBlank()) {
                return@forEachIndexed
            }
            items.put(
                JSONObject().apply {
                    put("id", "character_$index")
                    put("name_english", englishName)
                    put("description_english", englishDescription)
                },
            )
        }
        if (items.length() == 0) {
            return assets
        }
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.asset_localizer_prompt,
            mapOf(
                "ASSET_TYPE" to "characters",
                "TARGET_LANGUAGE" to language ?: "",
                "ITEMS" to items.toString(2),
            ),
        ).trim()
        val response = callAssetLlm(prompt) ?: return assets
        val localizedById = mutableMapOf<String, Pair<String, String>>()
        for (i in 0 until response.length()) {
            val obj = response.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val description = obj.optString("description")
            if (id.isNotBlank() && name.isNotBlank()) {
                localizedById[id] = name to description
            }
        }
        if (localizedById.isEmpty()) {
            return assets
        }
        return assets.mapIndexed { index, asset ->
            val englishName = asset.nameEnglish?.takeIf { it.isNotBlank() } ?: asset.name
            val englishDescription = asset.descriptionEnglish?.takeIf { it.isNotBlank() } ?: asset.description
            val key = "character_$index"
            val localized = localizedById[key]
            if (localized != null) {
                asset.copy(
                    name = localized.first,
                    description = localized.second.ifBlank { asset.description },
                    nameEnglish = englishName,
                    descriptionEnglish = englishDescription,
                )
            } else {
                asset.copy(
                    nameEnglish = englishName,
                    descriptionEnglish = englishDescription,
                )
            }
        }
    }

    suspend fun localizeEnvironments(
        assets: List<EnvironmentAsset>,
        language: String?,
    ): List<EnvironmentAsset> {
        if (!shouldLocalize(language) || assets.isEmpty()) {
            return assets
        }
        val items = JSONArray()
        assets.forEachIndexed { index, asset ->
            val englishName = asset.nameEnglish?.takeIf { it.isNotBlank() } ?: asset.name
            val englishDescription = asset.descriptionEnglish?.takeIf { it.isNotBlank() } ?: asset.description
            if (englishName.isBlank() && englishDescription.isBlank()) {
                return@forEachIndexed
            }
            items.put(
                JSONObject().apply {
                    put("id", "environment_$index")
                    put("name_english", englishName)
                    put("description_english", englishDescription)
                },
            )
        }
        if (items.length() == 0) {
            return assets
        }
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.asset_localizer_prompt,
            mapOf(
                "ASSET_TYPE" to "environments",
                "TARGET_LANGUAGE" to language ?: "",
                "ITEMS" to items.toString(2),
            ),
        ).trim()
        val response = callAssetLlm(prompt) ?: return assets
        val localizedById = mutableMapOf<String, Pair<String, String>>()
        for (i in 0 until response.length()) {
            val obj = response.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            val description = obj.optString("description")
            if (id.isNotBlank() && name.isNotBlank()) {
                localizedById[id] = name to description
            }
        }
        if (localizedById.isEmpty()) {
            return assets
        }
        return assets.mapIndexed { index, asset ->
            val englishName = asset.nameEnglish?.takeIf { it.isNotBlank() } ?: asset.name
            val englishDescription = asset.descriptionEnglish?.takeIf { it.isNotBlank() } ?: asset.description
            val key = "environment_$index"
            val localized = localizedById[key]
            if (localized != null) {
                asset.copy(
                    name = localized.first,
                    description = localized.second.ifBlank { asset.description },
                    nameEnglish = englishName,
                    descriptionEnglish = englishDescription,
                )
            } else {
                asset.copy(
                    nameEnglish = englishName,
                    descriptionEnglish = englishDescription,
                )
            }
        }
    }

    suspend fun localizeTitle(titleEnglish: String?, language: String?): String? {
        if (titleEnglish.isNullOrBlank()) return titleEnglish
        if (!shouldLocalize(language)) return titleEnglish
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.title_localizer_prompt,
            mapOf(
                "TARGET_LANGUAGE" to language ?: "",
                "TITLE" to titleEnglish,
            ),
        ).trim()
        val response = callTitleLlm(prompt) ?: return titleEnglish
        val translated = response.optString("title").takeIf { it.isNotBlank() }
        return translated ?: titleEnglish
    }
}
