package com.immagineran.no

import android.content.Context
import java.io.File

class SceneCompositionStep(
    private val appContext: Context,
    private val builder: SceneBuilder = SceneBuilder(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        val storyOriginal = context.storyOriginal ?: context.story
        val storyEnglish = context.storyEnglish ?: context.story
        if (storyOriginal.isNullOrBlank() && storyEnglish.isNullOrBlank()) {
            return
        }
        context.scenes = builder.buildScenes(
            storyOriginal = storyOriginal,
            storyEnglish = storyEnglish,
            characters = context.characters,
            environments = context.environments,
        )
    }
}

class SceneImageGenerationStep(
    private val appContext: Context,
    private val generator: ImageGenerator = ImageGenerator(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext, reporter: ProgressReporter) {
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        val total = context.scenes.size
        val updated = mutableListOf<Scene>()
        context.scenes.forEachIndexed { idx, scene ->
            reporter(appContext.getString(R.string.processing_scene_image_progress, idx + 1, total))
            val file = File(dir, "scene_${idx}.png")
            val description = buildString {
                append(scene.displayCaptionEnglish)
                when {
                    scene.environment != null ->
                        append(" Environment: ${scene.environment.displayDescription}.")
                    !scene.environmentName.isNullOrBlank() ->
                        append(" Environment: ${scene.environmentName}.")
                }
                if (scene.characters.isNotEmpty()) {
                    append(" Characters: ")
                    append(scene.characters.joinToString { it.displayDescription })
                }
            }
            val enrichedDescription = context.contextualizePrompt(description)
            val prompt = PromptTemplates.load(appContext, R.raw.scene_image_prompt, style, enrichedDescription)
            val path = generator.generate(prompt, file, ImageProvider.OPENROUTER)
            updated += scene.copy(image = path)
        }
        context.scenes = updated
    }
}

