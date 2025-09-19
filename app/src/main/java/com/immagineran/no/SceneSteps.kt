package com.immagineran.no

import android.content.Context
import java.io.File

class SceneCompositionStep(
    private val appContext: Context,
    private val builder: SceneBuilder = SceneBuilder(appContext),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
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
    override suspend fun process(context: ProcessingContext) {
        val dir = File(appContext.filesDir, context.id.toString()).apply { mkdirs() }
        val style = SettingsManager.getImageStyle(appContext)
        context.scenes = context.scenes.mapIndexed { idx, scene ->
            val file = File(dir, "scene_${idx}.png")
            val description = buildString {
                append(scene.displayCaptionEnglish)
                scene.environment?.let { append(" Environment: ${it.displayDescription}.") }
                if (scene.characters.isNotEmpty()) {
                    append(" Characters: ")
                    append(scene.characters.joinToString { it.displayDescription })
                }
            }
            val prompt = PromptTemplates.load(appContext, R.raw.scene_image_prompt, style, description)
            val path = generator.generate(prompt, file)
            scene.copy(image = path)
        }
    }
}

