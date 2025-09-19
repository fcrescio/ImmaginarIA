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

class SceneBuilder(
    private val appContext: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    private suspend fun callLLM(prompt: String): JSONArray? = withContext(Dispatchers.IO) {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("SceneBuilder", "Missing OpenRouter API key")
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
                              "caption_original": { "type": "string" },
                              "caption_english": { "type": "string" },
                              "environment": { "type": "string" },
                              "characters": {
                                "type": "array",
                                "items": { "type": "string" }
                              }
                            },
                            "required": ["caption_original", "environment", "characters"]
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
                                    put("name", "scenes")
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
                LlmLogger.log(appContext, "SceneBuilder", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("SceneBuilder", "HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter scene failed: ${'$'}{resp.code}")
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
            Log.e("SceneBuilder", "LLM error", e)
            LlmLogger.log(appContext, "SceneBuilder", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }

    suspend fun buildScenes(
        storyOriginal: String?,
        storyEnglish: String?,
        characters: List<CharacterAsset>,
        environments: List<EnvironmentAsset>,
    ): List<Scene> {
        val original = storyOriginal?.takeIf { it.isNotBlank() }
        val providedEnglish = storyEnglish?.takeIf { it.isNotBlank() }
        val english = providedEnglish ?: original
        if (original.isNullOrBlank() && english.isNullOrBlank()) {
            return emptyList()
        }

        fun CharacterAsset.englishName(): String =
            nameEnglish?.takeIf { it.isNotBlank() } ?: displayName

        fun CharacterAsset.englishDescription(): String =
            descriptionEnglish?.takeIf { it.isNotBlank() } ?: displayDescription

        fun EnvironmentAsset.englishName(): String =
            nameEnglish?.takeIf { it.isNotBlank() } ?: displayName

        fun EnvironmentAsset.englishDescription(): String =
            descriptionEnglish?.takeIf { it.isNotBlank() } ?: displayDescription

        val charList = characters.joinToString(separator = "\n") { asset ->
            buildString {
                val englishName = asset.englishName()
                append("- ${englishName}: ${asset.englishDescription()}")
                val originalName = asset.name.takeIf { it.isNotBlank() && !it.equals(englishName, ignoreCase = true) }
                if (originalName != null) {
                    append(" (Original name: ${originalName})")
                }
            }
        }.ifBlank { "- None" }

        val envList = environments.joinToString(separator = "\n") { asset ->
            buildString {
                val englishName = asset.englishName()
                append("- ${englishName}: ${asset.englishDescription()}")
                val originalName = asset.name.takeIf { it.isNotBlank() && !it.equals(englishName, ignoreCase = true) }
                if (originalName != null) {
                    append(" (Original name: ${originalName})")
                }
            }
        }.ifBlank { "- None" }

        val prompt = buildString {
            appendLine("Given the following story, split it into coherent scenes.")
            appendLine("For each scene reply with: caption_original (the narrative in the original language), caption_english (the same narrative in English), environment (choose an English name from the list), and characters (an array of English character names from the list).")
            appendLine("Use only the English names exactly as provided when listing environments or characters.")
            if (original == null && english != null) {
                appendLine("Only the English translation is available; repeat it for caption_original as well.")
            } else if (original != null && providedEnglish == null) {
                appendLine("Only the original narrative is available; translate it into English for caption_english.")
            }
            original?.let {
                appendLine()
                appendLine("Original story:")
                appendLine(it)
            }
            english?.let {
                appendLine()
                appendLine("English translation:")
                appendLine(it)
            }
            appendLine()
            appendLine("Characters (English reference):")
            appendLine(charList)
            appendLine()
            appendLine("Environments (English reference):")
            appendLine(envList)
        }
        val arr = callLLM(prompt) ?: return emptyList()
        val scenes = mutableListOf<Scene>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val captionOriginal = obj.optString("caption_original").takeIf { it.isNotBlank() }
                ?: obj.optString("text").takeIf { it.isNotBlank() }
            val captionEnglish = obj.optString("caption_english").takeIf { it.isNotBlank() }
                ?: captionOriginal
            val narrativeOriginal = captionOriginal ?: captionEnglish ?: continue
            val narrativeEnglish = captionEnglish ?: narrativeOriginal
            val envName = obj.optString("environment")
            val env = environments.find { it.matchesName(envName) }
            val charNames = obj.optJSONArray("characters")
            val chars = mutableListOf<CharacterAsset>()
            if (charNames != null) {
                for (j in 0 until charNames.length()) {
                    val name = charNames.optString(j)
                    characters.find { it.matchesName(name) }?.let { chars.add(it) }
                }
            }
            if (narrativeOriginal.isNotBlank() || narrativeEnglish.isNotBlank()) {
                scenes.add(
                    Scene(
                        captionOriginal = narrativeOriginal,
                        captionEnglish = narrativeEnglish,
                        environment = env,
                        characters = chars,
                    )
                )
            }
        }
        return scenes
    }
}

