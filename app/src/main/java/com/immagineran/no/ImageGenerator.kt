package com.immagineran.no

import android.content.Context
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
    private val appContext: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) {
    /**
     * Generates an image based on the given [prompt].
     */
    suspend fun generate(prompt: String, file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            when (SettingsManager.getImageProvider(appContext)) {
                ImageProvider.OPENROUTER -> generateWithOpenRouter(prompt, file)
                ImageProvider.FAL -> generateWithFal(prompt, file)
            }
        }.getOrElse { e ->
            Log.e("ImageGenerator", "Generation error", e)
            LlmLogger.log(appContext, "ImageGenerator", prompt, e.message)
            crashlytics.recordException(e)
            null
        }
    }

    private fun generateWithOpenRouter(prompt: String, file: File): String? {
        val key = BuildConfig.OPENROUTER_API_KEY
        if (key.isBlank()) {
            Log.e("ImageGenerator", "Missing OpenRouter API key")
            crashlytics.log("OpenRouter API key missing")
            return null
        }
        val messages = JSONArray().apply { put(message(prompt)) }
        repeat(5) {
            val root = JSONObject().apply {
                put("model", "google/gemini-2.5-flash-image-preview")
                put("messages", messages)
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
                LlmLogger.log(appContext, "ImageGeneratorOpenRouter", reqJson, respBody)
                if (!resp.isSuccessful) {
                    Log.e("ImageGenerator", "OpenRouter HTTP ${'$'}{resp.code}")
                    crashlytics.log("OpenRouter image failed: ${'$'}{resp.code}")
                    return null
                }
                val json = JSONObject(respBody ?: return null)
                val choices = json.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
                val b64 = extractBase64(message)
                if (b64 != null) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    file.outputStream().use { it.write(bytes) }
                    return file.absolutePath
                }
            }
            messages.put(
                message(
                    "Please try a different composition emphasizing fresh framing, varied focal points, and an alternative mood while staying true to the prompt.",
                ),
            )
        }
        return null
    }

    private fun generateWithFal(prompt: String, file: File): String? {
        val key = BuildConfig.FAL_API_KEY
        if (key.isBlank()) {
            Log.e("ImageGenerator", "Missing fal.ai API key")
            crashlytics.log("fal.ai API key missing")
            return null
        }
        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("image_size", "square")
            put("num_images", 1)
        }
        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(FAL_API_URL)
            .header("Authorization", "Key $key")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string()
            LlmLogger.log(appContext, "ImageGeneratorFal", requestJson.toString(), respBody)
            if (!resp.isSuccessful) {
                Log.e("ImageGenerator", "fal.ai HTTP ${'$'}{resp.code}")
                crashlytics.log("fal.ai image failed: ${'$'}{resp.code}")
                return null
            }
            val json = JSONObject(respBody ?: return null)
            extractFalImageUrl(json)?.let { url ->
                return downloadImage(url, file)
            }
            val response = json.optJSONObject("response")
            extractFalImageUrl(response)?.let { url ->
                return downloadImage(url, file)
            }
            val responseUrl = json.optString("response_url", "")
            if (responseUrl.isNotBlank()) {
                return pollFalResponse(responseUrl, key, file)
            }
        }
        return null
    }

    private fun pollFalResponse(responseUrl: String, key: String, file: File): String? {
        repeat(10) { attempt ->
            val request = Request.Builder()
                .url(responseUrl)
                .header("Authorization", "Key $key")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string()
                val pollRequestJson = JSONObject().apply {
                    put("response_url", responseUrl)
                    put("attempt", attempt + 1)
                }
                LlmLogger.log(appContext, "ImageGeneratorFalPoll", pollRequestJson.toString(), body)
                if (!resp.isSuccessful) {
                    Log.e("ImageGenerator", "fal.ai poll HTTP ${'$'}{resp.code}")
                    crashlytics.log("fal.ai poll failed: ${'$'}{resp.code}")
                    return null
                }
                val json = JSONObject(body ?: return null)
                val response = json.optJSONObject("response") ?: json
                extractFalImageUrl(response)?.let { url ->
                    return downloadImage(url, file)
                }
                val status = json.optString("status", "")
                if (!status.equals("IN_PROGRESS", true) &&
                    !status.equals("IN_QUEUE", true) &&
                    !status.equals("PENDING", true)
                ) {
                    return null
                }
            }
            Thread.sleep(1_000)
        }
        return null
    }

    private fun downloadImage(url: String, file: File): String? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("ImageGenerator", "Failed to download image: HTTP ${'$'}{resp.code}")
                crashlytics.log("Image download failed: ${'$'}{resp.code}")
                val errorRequestJson = JSONObject().apply {
                    put("url", url)
                    put("status_code", resp.code)
                }
                LlmLogger.log(appContext, "ImageGeneratorFalDownload", errorRequestJson.toString(), resp.body?.string())
                return null
            }
            val body = resp.body ?: run {
                val errorRequestJson = JSONObject().apply { put("url", url) }
                LlmLogger.log(appContext, "ImageGeneratorFalDownload", errorRequestJson.toString(), "<empty body>")
                return null
            }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }

    private fun extractFalImageUrl(json: JSONObject?): String? {
        if (json == null) return null
        json.optJSONArray("images")?.let { images ->
            for (i in 0 until images.length()) {
                val obj = images.optJSONObject(i) ?: continue
                val url = obj.optString("url").takeIf { it.isNotBlank() }
                    ?: obj.optString("image_url").takeIf { it.isNotBlank() }
                if (!url.isNullOrBlank()) return url
            }
        }
        json.optJSONObject("image")?.let { imageObj ->
            val url = imageObj.optString("url")
            if (url.isNotBlank()) return url
        }
        val url = json.optString("url")
        return url.takeIf { it.isNotBlank() }
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

    private companion object {
        private const val FAL_API_URL = "https://fal.run/fal-ai/flux-1/schnell"
    }
}
