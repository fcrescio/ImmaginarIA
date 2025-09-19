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
                              "text": { "type": "string" },
                              "environment": { "type": "string" },
                              "characters": {
                                "type": "array",
                                "items": { "type": "string" }
                              }
                            },
                            "required": ["text", "environment", "characters"]
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
        story: String,
        characters: List<CharacterAsset>,
        environments: List<EnvironmentAsset>,
    ): List<Scene> {
        val charList = characters.joinToString { "${it.displayName}: ${it.displayDescription}" }
        val envList = environments.joinToString { "${it.displayName}: ${it.displayDescription}" }
        val prompt = """
            Given the following story, characters, and environments, split the story into scenes.
            For each scene provide the narrative text, the environment name, and the list of character names present.
            Reply in a JSON array where each item has keys 'text', 'environment', and 'characters'.
            Story:
            $story
            Characters: $charList
            Environments: $envList
        """.trimIndent()
        val arr = callLLM(prompt) ?: return emptyList()
        val scenes = mutableListOf<Scene>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val text = obj.optString("text")
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
            if (text.isNotBlank()) {
                scenes.add(
                    Scene(
                        text = text,
                        environment = env,
                        characters = chars,
                    )
                )
            }
        }
        return scenes
    }
}

