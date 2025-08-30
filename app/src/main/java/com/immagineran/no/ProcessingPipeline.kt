package com.immagineran.no

import android.content.Context
import java.io.File

/**
 * Container for data moving through the processing pipeline.
 */
data class ProcessingContext(
    val prompt: String,
    val segments: List<String>,
    val id: Long,
    var story: String? = null,
    var characters: List<CharacterAsset> = emptyList(),
    var environments: List<EnvironmentAsset> = emptyList(),
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
    suspend fun run(context: ProcessingContext): ProcessingContext {
        steps.forEach { it.process(context) }
        return context
    }
}

/**
 * Step that stitches a list of transcribed segments into a story.
 */
class StoryStitchingStep(
    private val stitcher: StoryStitcher = StoryStitcher(),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        context.story = stitcher.stitch(context.prompt, context.segments)
    }
}

class CharacterExtractionStep(
    private val extractor: StoryAssetExtractor = StoryAssetExtractor(),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        val story = context.story ?: return
        context.characters = extractor.extractCharacters(story)
    }
}

class EnvironmentExtractionStep(
    private val extractor: StoryAssetExtractor = StoryAssetExtractor(),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        val story = context.story ?: return
        context.environments = extractor.extractEnvironments(story)
    }
}

class ImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        context.characters = context.characters.mapIndexed { idx, asset ->
            val file = File(dir, "character_${idx}.png")
            val path = generator.generate(asset.description, style, file)
            asset.copy(image = path)
        }
        context.environments = context.environments.mapIndexed { idx, asset ->
            val file = File(dir, "environment_${idx}.png")
            val path = generator.generate(asset.description, style, file)
            asset.copy(image = path)
        }
    }
}

