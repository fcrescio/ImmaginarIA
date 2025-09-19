package com.immagineran.no

import java.io.File

import android.content.Context
import org.json.JSONObject

/**
 * Container for data moving through the processing pipeline.
 */
data class ProcessingContext(
    val prompt: String,
    val segments: List<String>,
    val id: Long,
    var story: String? = null,
    var storyLanguage: String? = null,
    var storyOriginal: String? = null,
    var storyEnglish: String? = null,
    var storyJson: String? = null,
    var characters: List<CharacterAsset> = emptyList(),
    var environments: List<EnvironmentAsset> = emptyList(),
    var scenes: List<Scene> = emptyList(),
)

/**
 * A single step in a processing pipeline.
 */
fun interface ProcessingStep {
    suspend fun process(context: ProcessingContext)
}

/**
 * Simple pipeline executing a list of [ProcessingStep]s sequentially.
 */
class ProcessingPipeline(private val steps: List<ProcessingStep>) {
    suspend fun run(
        context: ProcessingContext,
        onProgress: suspend (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): ProcessingContext {
        steps.forEachIndexed { index, step ->
            step.process(context)
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
    override suspend fun process(context: ProcessingContext) {
        val stitched = stitcher.stitch(context.prompt, context.segments)
        context.storyJson = stitched
        if (stitched.isNullOrBlank()) {
            context.story = null
            context.storyLanguage = null
            context.storyOriginal = null
            context.storyEnglish = null
            return
        }

        val json = runCatching { JSONObject(stitched) }.getOrNull()
        if (json != null) {
            context.storyLanguage = json.optString("language").takeIf { it.isNotBlank() }
            context.storyOriginal = json.optString("story_original").takeIf { it.isNotBlank() }
            context.storyEnglish = json.optString("story_english").takeIf { it.isNotBlank() }
        } else {
            context.storyLanguage = null
            context.storyOriginal = stitched
            context.storyEnglish = stitched
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
    override suspend fun process(context: ProcessingContext) {
        val story = context.storyEnglish ?: context.story ?: context.storyOriginal ?: return
        context.characters = extractor.extractCharacters(story, context.storyLanguage)
    }
}

class EnvironmentExtractionStep(
    private val appContext: Context,
    private val extractor: StoryAssetExtractor = StoryAssetExtractor(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        val story = context.storyEnglish ?: context.story ?: context.storyOriginal ?: return
        context.environments = extractor.extractEnvironments(story, context.storyLanguage)
    }
}

class CharacterImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        if (context.characters.isEmpty()) return
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        context.characters = context.characters.mapIndexed { idx, asset ->
            val file = File(dir, "character_${idx}.png")
            val prompt = PromptTemplates.load(appContext, R.raw.character_image_prompt, style, asset.description)
            val path = generator.generate(prompt, file)
            asset.copy(image = path)
        }
    }
}

class EnvironmentImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        if (context.environments.isEmpty()) return
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        context.environments = context.environments.mapIndexed { idx, asset ->
            val file = File(dir, "environment_${idx}.png")
            val prompt = PromptTemplates.load(appContext, R.raw.environment_image_prompt, style, asset.description)
            val path = generator.generate(prompt, file)
            asset.copy(image = path)
        }
    }
}

