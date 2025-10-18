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
    private fun <T> parseAssets(
        arr: JSONArray,
        assetType: String,
        builder: (name: String, description: String) -> T,
    ): List<T> {
        val results = mutableListOf<T>()
        val validationErrors = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val obj = item as? JSONObject
            if (obj == null) {
                validationErrors.add(
                    "$assetType[$i]: expected object but was ${item?.let { it::class.simpleName } ?: "null"}"
                )
                continue
            }
            val missingKeys = mutableListOf<String>()
            if (!obj.has("name")) missingKeys.add("name")
            if (!obj.has("description")) missingKeys.add("description")
            if (missingKeys.isNotEmpty()) {
                validationErrors.add(
                    "$assetType[$i]: missing keys ${missingKeys.joinToString(", ")}"
                )
                continue
            }
            val name = obj.optString("name").trim()
            val description = obj.optString("description").trim()
            if (name.isEmpty()) {
                validationErrors.add("$assetType[$i]: empty \"name\" value")
                continue
            }
            if (description.isEmpty()) {
                validationErrors.add("$assetType[$i]: empty \"description\" value")
                continue
            }
            results.add(builder(name, description))
        }
        if (validationErrors.isNotEmpty()) {
            val message = "$assetType validation issues -> ${validationErrors.joinToString("; ")}"
            Log.w("StoryAssetExtractor", message)
            crashlytics.log(message)
        }
        return results
    }

    private suspend fun callLLM(prompt: String): JSONArray? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("StoryAssetExtractor", "Missing OpenRouter API key")
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
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.story_asset_characters_prompt,
            mapOf(
                "LANGUAGE_HINT" to languageHint,
                "STORY" to story,
            ),
        ).trim()
        val arr = callLLM(prompt) ?: return emptyList()
        return parseAssets(arr, "characters") { name, description ->
            CharacterAsset(
                name = name,
                description = description,
                nameEnglish = name,
                descriptionEnglish = description,
            )
        }
    }

    suspend fun extractEnvironmentForScene(
        scene: Scene,
        sourceLanguage: String? = null,
        previousEnvironmentName: String? = null,
    ): EnvironmentAsset? {
        val languageHint = sourceLanguage?.takeIf { it.isNotBlank() && !it.equals("english", ignoreCase = true) }
            ?.let {
                "The original story language is $it. Translate location names and descriptions into natural English while keeping important cultural nuances."
            }
            ?: "Ensure the environment name and description you output are written in clear, natural English."
        val previousHint = previousEnvironmentName?.takeIf { it.isNotBlank() }
            ?.let {
                "The previous scene took place in \"$it\". Only reuse that location if the narrative clearly remains there; otherwise choose a distinct setting."
            }
            ?: "There is no previous scene to reference."
        val environmentHint = scene.environmentName?.takeIf { it.isNotBlank() }
            ?.let { "Suggested environment from the scene builder: $it" }
            ?: "No suggested environment name was provided."
        val characters = scene.characters.joinToString(separator = "\n") { character ->
            "- ${character.displayName}: ${character.displayDescription}"
        }.ifBlank { "- None" }
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.scene_environment_prompt,
            mapOf(
                "LANGUAGE_HINT" to languageHint,
                "PREVIOUS_HINT" to previousHint,
                "ENVIRONMENT_HINT" to environmentHint,
                "SCENE_ORIGINAL" to scene.captionOriginal.ifBlank { "(Not provided)" },
                "SCENE_ENGLISH" to scene.captionEnglish.ifBlank { "(Not provided)" },
                "CHARACTER_LIST" to characters,
            ),
        ).trim()
        val arr = callLLM(prompt) ?: return null
        return parseAssets(arr, "environment") { name, description ->
            EnvironmentAsset(
                name = name,
                description = description,
                nameEnglish = name,
                descriptionEnglish = description,
            )
        }.firstOrNull()
    }

    suspend fun extractEnvironments(story: String, sourceLanguage: String? = null): List<EnvironmentAsset> {
        val languageHint = sourceLanguage?.takeIf { it.isNotBlank() && !it.equals("english", ignoreCase = true) }
            ?.let {
                "The original story language is $it. Translate location names and descriptions into natural English while keeping important cultural nuances."
            }
            ?: "Ensure every environment name and description you output is written in clear, natural English."
        val prompt = PromptTemplates.load(
            appContext,
            R.raw.story_asset_environments_prompt,
            mapOf(
                "LANGUAGE_HINT" to languageHint,
                "STORY" to story,
            ),
        ).trim()
        val arr = callLLM(prompt) ?: return emptyList()
        return parseAssets(arr, "environments") { name, description ->
            EnvironmentAsset(
                name = name,
                description = description,
                nameEnglish = name,
                descriptionEnglish = description,
            )
        }
    }
}
