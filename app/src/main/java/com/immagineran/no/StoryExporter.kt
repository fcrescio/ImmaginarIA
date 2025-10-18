package com.immagineran.no

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Creates shareable archives for processed [Story] instances.
 */
object StoryExporter {
    /**
     * Builds a zip archive containing text, metadata, and referenced images for [story].
     *
     * The generated file is stored under `<files>/exports/` and is safe to share through
     * apps that accept attachments (for example email clients).
     *
     * @return The exported file or `null` if no data could be written.
     */
    suspend fun export(context: Context, story: Story): File? = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val sanitizedTitle = sanitize(story.title).ifBlank { "story" }
        val zipFile = File(exportDir, "${story.id}-${sanitizedTitle}.zip")
        if (zipFile.exists()) {
            zipFile.delete()
        }

        val attachments = mutableListOf<Pair<File, String>>()
        val metadata = buildMetadata(story, attachments)

        var wroteContent = false
        runCatching {
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                story.storyOriginal?.takeIf { it.isNotBlank() }?.let { original ->
                    zip.putNextEntry(ZipEntry("text/story_original.txt"))
                    zip.write(original.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    wroteContent = true
                }
                story.storyEnglish?.takeIf { it.isNotBlank() && it != story.storyOriginal }?.let { english ->
                    zip.putNextEntry(ZipEntry("text/story_english.txt"))
                    zip.write(english.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    wroteContent = true
                }

                zip.putNextEntry(ZipEntry("metadata.json"))
                zip.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                wroteContent = true

                attachments.forEach { (file, entryName) ->
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(entryName))
                        BufferedInputStream(FileInputStream(file)).use { input ->
                            input.copyTo(zip)
                        }
                        zip.closeEntry()
                        wroteContent = true
                    }
                }
            }
        }.getOrElse {
            zipFile.delete()
            return@withContext null
        }

        return@withContext zipFile.takeIf { wroteContent && it.exists() }
    }

    private fun buildMetadata(story: Story, attachments: MutableList<Pair<File, String>>): JSONObject {
        val metadata = JSONObject()
        metadata.put("id", story.id)
        metadata.put("title", story.title)
        metadata.put("timestamp", story.timestamp)
        story.language?.let { metadata.put("language", it) }
        metadata.put("processed", story.processed)
        story.storyOriginal?.let { metadata.put("story_original", it) }
        story.storyEnglish?.let { metadata.put("story_english", it) }
        if (story.content.isNotBlank()) {
            metadata.put("content", story.content)
        }

        if (story.segments.isNotEmpty()) {
            val segmentsArray = JSONArray()
            story.segments.forEachIndexed { index, path ->
                val segmentObj = JSONObject()
                segmentObj.put("source_path", path)
                val file = File(path)
                if (file.exists()) {
                    val entryName = "audio/segment_${index}.${file.extension.ifBlank { "wav" }}"
                    attachments.add(file to entryName)
                    segmentObj.put("file", entryName)
                }
                segmentsArray.put(segmentObj)
            }
            metadata.put("segments", segmentsArray)
        }

        val characters = JSONArray()
        story.characters.forEachIndexed { index, character ->
            val characterObj = JSONObject()
            characterObj.put("name", character.name)
            characterObj.put("description", character.description)
            character.nameEnglish?.let { characterObj.put("name_english", it) }
            character.descriptionEnglish?.let { characterObj.put("description_english", it) }
            character.image?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val entryName = imageEntryName("characters", index, character.displayName, file)
                    attachments.add(file to entryName)
                    characterObj.put("image", entryName)
                }
            }
            characters.put(characterObj)
        }
        metadata.put("characters", characters)

        val environments = JSONArray()
        story.environments.forEachIndexed { index, environment ->
            val environmentObj = JSONObject()
            environmentObj.put("name", environment.name)
            environmentObj.put("description", environment.description)
            environment.nameEnglish?.let { environmentObj.put("name_english", it) }
            environment.descriptionEnglish?.let { environmentObj.put("description_english", it) }
            environment.image?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val entryName = imageEntryName("environments", index, environment.displayName, file)
                    attachments.add(file to entryName)
                    environmentObj.put("image", entryName)
                }
            }
            environments.put(environmentObj)
        }
        metadata.put("environments", environments)

        val scenes = JSONArray()
        story.scenes.forEachIndexed { index, scene ->
            val sceneObj = JSONObject()
            sceneObj.put("caption_original", scene.captionOriginal)
            sceneObj.put("caption_english", scene.captionEnglish)
            val environmentLabel = scene.environment?.displayName ?: scene.environmentName
            environmentLabel?.let { sceneObj.put("environment", it) }
            if (scene.characters.isNotEmpty()) {
                val characterArray = JSONArray()
                scene.characters.forEach { characterArray.put(it.displayName) }
                sceneObj.put("characters", characterArray)
            }
            scene.image?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val entryName = imageEntryName("scenes", index, scene.displayCaptionEnglish, file)
                    attachments.add(file to entryName)
                    sceneObj.put("image", entryName)
                }
            }
            scenes.put(sceneObj)
        }
        metadata.put("scenes", scenes)
        return metadata
    }

    private fun imageEntryName(category: String, index: Int, name: String?, file: File): String {
        val sanitized = sanitize(name ?: "${category}_${index}").ifBlank { "${category}_${index}" }
        val extension = file.extension.takeIf { it.isNotBlank() } ?: "png"
        return "images/${category}/${index + 1}-${sanitized}.${extension}"
    }

    private fun sanitize(value: String): String {
        return value.lowercase(Locale.US)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
    }
}
