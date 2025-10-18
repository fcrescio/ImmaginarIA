package com.immagineran.no

import android.content.Context
import android.util.Base64
import android.util.Log
import ai.fal.client.ClientConfig
import ai.fal.client.CredentialsResolver
import ai.fal.client.kt.SubscribeOptions
import ai.fal.client.kt.createFalClient
import ai.fal.client.kt.subscribe
import ai.fal.client.queue.QueueStatus
import ai.fal.client.type.RequestLog
import com.google.gson.JsonObject
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
        try {
            val provider = SettingsManager.getImageProvider(appContext)
            Log.d("ImageGenerator", "Selected image provider: $provider")
            crashlytics.log("ImageGenerator provider: $provider")
            when (provider) {
                ImageProvider.OPENROUTER -> generateWithOpenRouter(prompt, file)
                ImageProvider.FAL -> generateWithFal(prompt, file)
                ImageProvider.FAL_KOTLIN -> generateWithFalKotlin(prompt, file)
            }
        } catch (e: Exception) {
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
                    Log.e("ImageGenerator", "OpenRouter HTTP ${resp.code}")
                    crashlytics.log("OpenRouter image failed: ${resp.code}")
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
            LlmLogger.log(appContext, "ImageGeneratorFal", prompt, "Missing API key")
            return null
        }
        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("image_size", "square")
            put("num_images", 1)
        }
        Log.d("ImageGenerator", "fal.ai request body: $requestJson")
        crashlytics.log("fal.ai request prepared")
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
                Log.e("ImageGenerator", "fal.ai HTTP ${resp.code}")
                crashlytics.log("fal.ai image failed: ${resp.code}")
                return null
            }
            val json = JSONObject(respBody ?: return null)
            Log.d("ImageGenerator", "fal.ai initial response: $json")
            extractFalImageUrl(json)?.let { url ->
                Log.d("ImageGenerator", "fal.ai returned image url: $url")
                return downloadImage(url, file)
            }
            val response = json.optJSONObject("response")
            extractFalImageUrl(response)?.let { url ->
                Log.d("ImageGenerator", "fal.ai response object contained url: $url")
                return downloadImage(url, file)
            }
            val responseUrl = json.optString("response_url", "")
            if (responseUrl.isNotBlank()) {
                Log.d("ImageGenerator", "fal.ai response_url present: $responseUrl")
                return pollFalResponse(responseUrl, key, file)
            }
            Log.w("ImageGenerator", "fal.ai response missing image url")
            crashlytics.log("fal.ai response missing image url")
        }
        return null
    }

    private suspend fun generateWithFalKotlin(prompt: String, file: File): String? {
        val key = BuildConfig.FAL_API_KEY
        if (key.isBlank()) {
            Log.e("ImageGenerator", "Missing fal.ai API key")
            crashlytics.log("fal.ai API key missing")
            LlmLogger.log(appContext, "ImageGeneratorFalKotlin", prompt, "Missing API key")
            return null
        }
        val requestJson = JSONObject().apply {
            put("prompt", prompt)
            put("image_size", "square")
            put("num_images", 1)
        }
        Log.d("ImageGenerator", "fal.ai kotlin request body: $requestJson")
        crashlytics.log("fal.ai kotlin request prepared")
        val clientConfig = ClientConfig.withCredentials(CredentialsResolver.fromApiKey(key))
        val fal = createFalClient(clientConfig)
        val input = mapOf<String, Any?>(
            "prompt" to prompt,
            "image_size" to "square",
            "num_images" to 1,
        )
        val requestOutput = fal.subscribe<JsonObject>(
            FAL_MODEL_NAME,
            input,
            SubscribeOptions(logs = true),
        ) { update ->
            when (update) {
                is QueueStatus.InQueue -> {
                    update.queuePosition?.let { position ->
                        Log.d("ImageGenerator", "fal.ai client queue position: $position")
                        crashlytics.log("fal.ai client queue position: $position")
                    }
                }
                is QueueStatus.InProgress -> {
                    update.logs?.forEach { logEntry: RequestLog ->
                        val message = "fal.ai client log: ${logEntry.message}"
                        Log.d("ImageGenerator", message)
                        crashlytics.log(message)
                    }
                }
                is QueueStatus.Completed -> {
                    update.logs?.forEach { logEntry: RequestLog ->
                        val message = "fal.ai client completed: ${logEntry.message}"
                        Log.d("ImageGenerator", message)
                        crashlytics.log(message)
                    }
                }
                else -> {
                    // Ignore other intermediate statuses.
                }
            }
        }
        val responseJsonString = requestOutput.data.toString()
        val requestLogJson = JSONObject(requestJson.toString()).apply {
            put("request_id", requestOutput.requestId)
        }
        LlmLogger.log(
            appContext,
            "ImageGeneratorFalKotlin",
            requestLogJson.toString(),
            responseJsonString,
        )
        val requestId = requestOutput.requestId
        if (requestId.isNotBlank()) {
            Log.d("ImageGenerator", "fal.ai client request id: $requestId")
            crashlytics.log("fal.ai client request id: $requestId")
        }
        val json = responseJsonString.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        if (json == null) {
            Log.w("ImageGenerator", "fal.ai kotlin response empty")
            crashlytics.log("fal.ai kotlin response empty")
            return null
        }
        extractFalImageUrl(json)?.let { url ->
            Log.d("ImageGenerator", "fal.ai kotlin returned image url: $url")
            return downloadImage(url, file)
        }
        val response = json.optJSONObject("response")
        extractFalImageUrl(response)?.let { url ->
            Log.d("ImageGenerator", "fal.ai kotlin response object contained url: $url")
            return downloadImage(url, file)
        }
        val responseUrl = json.optString("response_url", "")
        if (responseUrl.isNotBlank()) {
            Log.d("ImageGenerator", "fal.ai kotlin response_url present: $responseUrl")
            crashlytics.log("fal.ai kotlin response_url present")
            return pollFalResponse(responseUrl, key, file)
        }
        Log.w("ImageGenerator", "fal.ai kotlin response missing image url")
        crashlytics.log("fal.ai kotlin response missing image url")
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
                    Log.e("ImageGenerator", "fal.ai poll HTTP ${resp.code}")
                    crashlytics.log("fal.ai poll failed: ${resp.code}")
                    return null
                }
                val json = JSONObject(body ?: return null)
                Log.d("ImageGenerator", "fal.ai poll attempt ${attempt + 1} response: $json")
                val response = json.optJSONObject("response") ?: json
                extractFalImageUrl(response)?.let { url ->
                    Log.d("ImageGenerator", "fal.ai poll delivered url: $url")
                    return downloadImage(url, file)
                }
                val status = json.optString("status", "")
                if (!status.equals("IN_PROGRESS", true) &&
                    !status.equals("IN_QUEUE", true) &&
                    !status.equals("PENDING", true)
                ) {
                    Log.w("ImageGenerator", "fal.ai poll finished without image: status=$status")
                    crashlytics.log("fal.ai poll finished without image: status=$status")
                    return null
                }
            }
            Thread.sleep(1_000)
        }
        Log.w("ImageGenerator", "fal.ai poll exceeded retries")
        crashlytics.log("fal.ai poll exceeded retries")
        return null
    }

    private fun downloadImage(url: String, file: File): String? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.e("ImageGenerator", "Failed to download image: HTTP ${resp.code}")
                crashlytics.log("Image download failed: ${resp.code}")
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
        Log.d("ImageGenerator", "fal.ai image downloaded to ${file.absolutePath}")
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
        private const val FAL_MODEL_NAME = "fal-ai/flux-1/schnell"
        private const val FAL_API_URL = "https://fal.run/fal-ai/flux-1/schnell"
    }
}
