package com.immagineran.no

import java.io.File

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Container for data moving through the processing pipeline.
 */
data class ProcessingContext(
    val prompt: String,
    val segments: List<String>,
    val id: Long,
    var story: String? = null,
    var storyTitle: String? = null,
    var storyLanguage: String? = null,
    var storyOriginal: String? = null,
    var storyEnglish: String? = null,
    var storyJson: String? = null,
    var characters: List<CharacterAsset> = emptyList(),
    var environments: List<EnvironmentAsset> = emptyList(),
    var scenes: List<Scene> = emptyList(),
    var storyContextTags: List<String> = emptyList(),
)

typealias ProgressReporter = suspend (String) -> Unit

/**
 * A single step in a processing pipeline.
 */
fun interface ProcessingStep {
    suspend fun process(context: ProcessingContext, reporter: ProgressReporter)
}

/**
 * Simple pipeline executing a list of [ProcessingStep]s sequentially.
 */
class ProcessingPipeline(private val steps: List<ProcessingStep>) {
    suspend fun run(
        context: ProcessingContext,
        onProgress: suspend (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
        onLog: ProgressReporter = {}
    ): ProcessingContext {
        steps.forEachIndexed { index, step ->
            step.process(context, onLog)
            onProgress(index + 1, steps.size, step.javaClass.simpleName)
        }
        return context
    }
}

/**
 * Step that stitches a list of transcribed segments into a story.
 */
class StoryStitchingStep(
    private val appContext: Context,
    private val stitcher: StoryStitcher = StoryStitcher(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        val stitched = stitcher.stitch(context.prompt, context.segments)
        context.storyJson = stitched
        if (stitched.isNullOrBlank()) {
            context.story = null
            context.storyTitle = null
            context.storyLanguage = null
            context.storyOriginal = null
            context.storyEnglish = null
            context.storyContextTags = emptyList()
            return
        }

        val json = runCatching { JSONObject(stitched) }.getOrNull()
        if (json != null) {
            context.storyTitle = json.optString("title_short").takeIf { it.isNotBlank() }
            context.storyLanguage = json.optString("language").takeIf { it.isNotBlank() }
            context.storyOriginal = json.optString("story_original").takeIf { it.isNotBlank() }
            context.storyEnglish = json.optString("story_english").takeIf { it.isNotBlank() }
            context.storyContextTags = collectStoryTags(json)
        } else {
            context.storyTitle = null
            context.storyLanguage = null
            context.storyOriginal = stitched
            context.storyEnglish = stitched
            context.storyContextTags = emptyList()
        }
        if (context.storyEnglish.isNullOrBlank()) {
            context.storyEnglish = context.storyOriginal
        }
        if (context.storyOriginal.isNullOrBlank()) {
            context.storyOriginal = context.storyEnglish
        }
        context.story = context.storyEnglish ?: context.storyOriginal
    }
}

class CharacterExtractionStep(
    private val appContext: Context,
    private val extractor: StoryAssetExtractor = StoryAssetExtractor(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        val story = context.storyEnglish ?: context.story ?: context.storyOriginal ?: return
        context.characters = extractor.extractCharacters(story, context.storyLanguage)
    }
}

class EnvironmentExtractionStep(
    private val appContext: Context,
    private val extractor: StoryAssetExtractor = StoryAssetExtractor(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        val story = context.storyEnglish ?: context.story ?: context.storyOriginal ?: return
        context.environments = extractor.extractEnvironments(story, context.storyLanguage)
    }
}

class CharacterImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        if (context.characters.isEmpty()) return
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        val total = context.characters.size
        val updated = mutableListOf<CharacterAsset>()
        context.characters.forEachIndexed { idx, asset ->
            reporter(appContext.getString(R.string.processing_character_image_progress, idx + 1, total))
            val file = File(dir, "character_${idx}.png")
            val enrichedDescription = context.contextualizePrompt(asset.description)
            val prompt = PromptTemplates.load(appContext, R.raw.character_image_prompt, style, enrichedDescription)
            val path = generator.generate(prompt, file)
            updated += asset.copy(image = path)
        }
        context.characters = updated
    }
}

class EnvironmentImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        if (context.environments.isEmpty()) return
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        val total = context.environments.size
        val updated = mutableListOf<EnvironmentAsset>()
        context.environments.forEachIndexed { idx, asset ->
            reporter(appContext.getString(R.string.processing_environment_image_progress, idx + 1, total))
            val file = File(dir, "environment_${idx}.png")
            val enrichedDescription = context.contextualizePrompt(asset.description)
            val prompt = PromptTemplates.load(appContext, R.raw.environment_image_prompt, style, enrichedDescription)
            val path = generator.generate(prompt, file)
            updated += asset.copy(image = path)
        }
        context.environments = updated
    }
}

private val STORY_METADATA_KEYS = setOf(
    "mood",
    "tone",
    "palette",
    "color_palette",
    "lighting",
    "genre",
    "keywords",
    "themes",
    "emotion",
    "atmosphere",
    "visual_style",
    "camera",
)

private fun collectStoryTags(json: JSONObject?): List<String> {
    if (json == null) return emptyList()
    val tags = mutableListOf<String>()
    val candidates = mutableListOf<JSONObject>()
    listOf("metadata", "story_metadata", "context").forEach { key ->
        json.optJSONObject(key)?.let { candidates += it }
    }
    candidates.forEach { obj ->
        STORY_METADATA_KEYS.forEach { key ->
            if (obj.has(key)) {
                collectMetadataValue(formatLabel(key), obj.opt(key), tags)
            }
        }
        obj.optJSONArray("tags")?.let { collectMetadataValue("Tags", it, tags) }
    }
    STORY_METADATA_KEYS.forEach { key ->
        if (json.has(key)) {
            collectMetadataValue(formatLabel(key), json.opt(key), tags)
        }
    }
    json.optJSONArray("tags")?.let { collectMetadataValue("Tags", it, tags) }
    return tags.distinct()
}

private fun collectMetadataValue(label: String, value: Any?, sink: MutableList<String>) {
    val labelText = label.trim()
    if (labelText.isEmpty()) return
    when (value) {
        is JSONArray -> {
            val items = mutableListOf<String>()
            for (i in 0 until value.length()) {
                val element = value.opt(i)
                when (element) {
                    is String -> {
                        val text = element.trim()
                        if (text.isNotEmpty()) {
                            items += text
                        }
                    }
                    is JSONObject -> {
                        val nestedSink = mutableListOf<String>()
                        element.keys().forEach { nestedKey ->
                            collectMetadataValue(
                                joinLabels(labelText, formatLabel(nestedKey)),
                                element.opt(nestedKey),
                                nestedSink,
                            )
                        }
                        if (nestedSink.isNotEmpty()) {
                            sink.addAll(nestedSink)
                        }
                    }
                    else -> {
                        val text = element?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            items += text
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                sink += "$labelText: ${items.joinToString(", ")}"
            }
        }
        is JSONObject -> {
            value.keys().forEach { nestedKey ->
                collectMetadataValue(joinLabels(labelText, formatLabel(nestedKey)), value.opt(nestedKey), sink)
            }
        }
        is String -> {
            val text = value.trim()
            if (text.isNotEmpty()) {
                sink += "$labelText: $text"
            }
        }
        is Number, is Boolean -> {
            sink += "$labelText: $value"
        }
    }
}

private fun formatLabel(key: String): String {
    return key
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.lowercase(java.util.Locale.getDefault()).replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(java.util.Locale.getDefault()) else ch.toString()
            }
        }
        .ifBlank { key }
}

private fun joinLabels(vararg parts: String): String =
    parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

fun ProcessingContext.contextualizePrompt(base: String): String {
    val trimmed = base.trim()
    if (storyContextTags.isEmpty()) {
        return trimmed
    }
    val tags = storyContextTags.joinToString(", ")
    return buildString {
        if (trimmed.isNotEmpty()) {
            append(trimmed)
            if (!trimmed.endsWith("\n")) {
                append('\n')
            }
        }
        append("Context tags: ")
        append(tags)
    }
}

