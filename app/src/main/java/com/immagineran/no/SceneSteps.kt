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
            val referenceNotes = buildReferenceNotes(scene, context)
            val combinedDescription = buildString {
                append(description.trim())
                if (referenceNotes.isNotEmpty()) {
                    if (isNotEmpty()) {
                        append("\n\n")
                    }
                    append(referenceNotes.joinToString(separator = "\n"))
                }
            }
            val enrichedDescription = context.contextualizePrompt(combinedDescription)
            val prompt = PromptTemplates.load(appContext, R.raw.scene_image_prompt, style, enrichedDescription)
            val path = generator.generate(prompt, file, ImageProvider.OPENROUTER)
            updated += scene.copy(image = path)
        }
        context.scenes = updated
    }

    private fun buildReferenceNotes(scene: Scene, context: ProcessingContext): List<String> {
        val notes = mutableListOf<String>()
        resolveEnvironmentReference(scene, context)?.image?.takeIf { !it.isNullOrBlank() }?.let { path ->
            notes += "Environment reference image: $path"
        }
        val characterNotes = scene.characters.mapNotNull { character ->
            resolveCharacterImage(character, context)?.takeIf { it.isNotBlank() }?.let { path ->
                "${character.displayName} reference image: $path"
            }
        }
        if (characterNotes.isNotEmpty()) {
            notes += characterNotes.distinct()
        }
        return notes
    }

    private fun resolveEnvironmentReference(scene: Scene, context: ProcessingContext): EnvironmentAsset? {
        scene.environment?.takeIf { !it.image.isNullOrBlank() }?.let { return it }
        val candidateName = scene.environment?.displayName ?: scene.environmentName
        if (candidateName.isNullOrBlank()) {
            return null
        }
        return context.environments.firstOrNull { asset ->
            !asset.image.isNullOrBlank() && asset.matchesName(candidateName)
        }
    }

    private fun resolveCharacterImage(character: CharacterAsset, context: ProcessingContext): String? {
        character.image?.takeIf { it.isNotBlank() }?.let { return it }
        val candidateName = character.displayName
        return context.characters.firstOrNull { asset ->
            !asset.image.isNullOrBlank() && asset.matchesName(candidateName)
        }?.image
    }
}

