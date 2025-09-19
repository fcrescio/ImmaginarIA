package com.immagineran.no

import java.io.File

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object StoryRepository {
    private const val FILE_NAME = "stories.json"

    fun getStories(context: Context): List<Story> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = file.readText()
        val array = JSONArray(json)
        val result = mutableListOf<Story>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val segmentsArray = obj.optJSONArray("segments")
            val segments = mutableListOf<String>()
            if (segmentsArray != null) {
                for (j in 0 until segmentsArray.length()) {
                    segments.add(segmentsArray.getString(j))
                }
            }
            val content = obj.optString("content", "")
            var language = obj.optString("language").takeIf { it.isNotBlank() }
            var storyOriginal = obj.optString("story_original").takeIf { it.isNotBlank() }
            var storyEnglish = obj.optString("story_english").takeIf { it.isNotBlank() }

            if ((language == null || storyOriginal == null || storyEnglish == null) && content.isNotBlank()) {
                runCatching { JSONObject(content) }.getOrNull()?.let { storyObj ->
                    if (language == null) {
                        language = storyObj.optString("language").takeIf { it.isNotBlank() }
                    }
                    if (storyOriginal == null) {
                        storyOriginal = storyObj.optString("story_original").takeIf { it.isNotBlank() }
                    }
                    if (storyEnglish == null) {
                        storyEnglish = storyObj.optString("story_english").takeIf { it.isNotBlank() }
                    }
                }
            }

            if (storyOriginal.isNullOrBlank() && storyEnglish.isNullOrBlank() && content.isNotBlank()) {
                storyOriginal = content
                storyEnglish = content
            }
            if (storyEnglish.isNullOrBlank()) {
                storyEnglish = storyOriginal
            }
            if (storyOriginal.isNullOrBlank()) {
                storyOriginal = storyEnglish
            }

            val characters = mutableListOf<CharacterAsset>()
            val charArray = obj.optJSONArray("characters")
            if (charArray != null) {
                for (j in 0 until charArray.length()) {
                    val cObj = charArray.optJSONObject(j) ?: continue
                    val nameEnglish = cObj.optString("name_english").takeIf { it.isNotBlank() }
                    val descriptionEnglish = cObj.optString("description_english").takeIf { it.isNotBlank() }
                    val name = cObj.optString("name").takeIf { it.isNotBlank() }
                        ?: nameEnglish
                        ?: ""
                    val description = cObj.optString("description").takeIf { it.isNotBlank() }
                        ?: descriptionEnglish
                        ?: ""
                    characters.add(
                        CharacterAsset(
                            name = name,
                            description = description,
                            image = cObj.optString("image", null),
                            nameEnglish = nameEnglish,
                            descriptionEnglish = descriptionEnglish,
                        )
                    )
                }
            }
            val environments = mutableListOf<EnvironmentAsset>()
            val envArray = obj.optJSONArray("environments")
            if (envArray != null) {
                for (j in 0 until envArray.length()) {
                    val eObj = envArray.optJSONObject(j) ?: continue
                    val nameEnglish = eObj.optString("name_english").takeIf { it.isNotBlank() }
                    val descriptionEnglish = eObj.optString("description_english").takeIf { it.isNotBlank() }
                    val name = eObj.optString("name").takeIf { it.isNotBlank() }
                        ?: nameEnglish
                        ?: ""
                    val description = eObj.optString("description").takeIf { it.isNotBlank() }
                        ?: descriptionEnglish
                        ?: ""
                    environments.add(
                        EnvironmentAsset(
                            name = name,
                            description = description,
                            image = eObj.optString("image", null),
                            nameEnglish = nameEnglish,
                            descriptionEnglish = descriptionEnglish,
                        )
                    )
                }
            }
            val scenes = mutableListOf<Scene>()
            val sceneArray = obj.optJSONArray("scenes")
            if (sceneArray != null) {
                for (j in 0 until sceneArray.length()) {
                    val sObj = sceneArray.optJSONObject(j) ?: continue
                    val captionOriginal = sObj.optString("caption_original").takeIf { it.isNotBlank() }
                        ?: sObj.optString("text").takeIf { it.isNotBlank() }
                        ?: ""
                    val captionEnglish = sObj.optString("caption_english").takeIf { it.isNotBlank() }
                        ?: captionOriginal
                    val envName = sObj.optString("environment")
                    val env = environments.find { environment ->
                        environment.matchesName(envName)
                    }
                    val chars = mutableListOf<CharacterAsset>()
                    val names = sObj.optJSONArray("characters")
                    if (names != null) {
                        for (k in 0 until names.length()) {
                            val n = names.optString(k)
                            characters.find { character -> character.matchesName(n) }
                                ?.let { chars.add(it) }
                        }
                    }
                    scenes.add(
                        Scene(
                            captionOriginal = captionOriginal,
                            captionEnglish = captionEnglish,
                            environment = env,
                            characters = chars,
                            image = sObj.optString("image", null)
                        )
                    )
                }
            }
            result.add(
                Story(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    content = content,
                    language = language,
                    storyOriginal = storyOriginal,
                    storyEnglish = storyEnglish,
                    segments = segments,
                    processed = obj.optBoolean("processed", false),
                    characters = characters,
                    environments = environments,
                    scenes = scenes,
                )
            )
        }
        return result
    }

    fun addStory(context: Context, story: Story) {
        val stories = getStories(context).toMutableList()
        stories.add(story)
        saveStories(context, stories)
    }

    fun updateStory(context: Context, story: Story) {
        val stories = getStories(context).toMutableList()
        val index = stories.indexOfFirst { it.id == story.id }
        if (index >= 0) {
            stories[index] = story
        } else {
            stories.add(story)
        }
        saveStories(context, stories)
    }

    fun deleteStory(context: Context, story: Story) {
        // Remove associated files
        story.segments.forEach { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {
                // Ignore failures deleting segment files
            }
        }
        // Remove any directory named after the story id
        val dir = File(context.filesDir, story.id.toString())
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        val stories = getStories(context).filter { it.id != story.id }
        saveStories(context, stories)
    }

    private fun saveStories(context: Context, stories: List<Story>) {
        val array = JSONArray()
        stories.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("timestamp", s.timestamp)
            obj.put("content", s.content)
            obj.put("processed", s.processed)
            s.language?.let { obj.put("language", it) }
            s.storyOriginal?.let { obj.put("story_original", it) }
            s.storyEnglish?.let { obj.put("story_english", it) }
            val segmentsArray = JSONArray()
            s.segments.forEach { segmentsArray.put(it) }
            obj.put("segments", segmentsArray)
            val charArray = JSONArray()
            s.characters.forEach { c ->
                val cObj = JSONObject()
                cObj.put("name", c.name.ifBlank { c.displayName })
                cObj.put("description", c.description.ifBlank { c.displayDescription })
                c.image?.let { cObj.put("image", it) }
                c.nameEnglish?.takeIf { it.isNotBlank() }?.let { cObj.put("name_english", it) }
                c.descriptionEnglish?.takeIf { it.isNotBlank() }?.let { cObj.put("description_english", it) }
                charArray.put(cObj)
            }
            obj.put("characters", charArray)
            val envArray = JSONArray()
            s.environments.forEach { e ->
                val eObj = JSONObject()
                eObj.put("name", e.name.ifBlank { e.displayName })
                eObj.put("description", e.description.ifBlank { e.displayDescription })
                e.image?.let { eObj.put("image", it) }
                e.nameEnglish?.takeIf { it.isNotBlank() }?.let { eObj.put("name_english", it) }
                e.descriptionEnglish?.takeIf { it.isNotBlank() }?.let { eObj.put("description_english", it) }
                envArray.put(eObj)
            }
            obj.put("environments", envArray)
            val sceneArray = JSONArray()
            s.scenes.forEach { scene ->
                val scObj = JSONObject()
                scObj.put("caption_original", scene.captionOriginal)
                scObj.put("caption_english", scene.captionEnglish)
                scObj.put("text", scene.displayCaptionOriginal)
                scene.environment?.let { scObj.put("environment", it.displayName) }
                val charNames = JSONArray()
                scene.characters.forEach { c -> charNames.put(c.displayName) }
                scObj.put("characters", charNames)
                scene.image?.let { scObj.put("image", it) }
                sceneArray.put(scObj)
            }
            obj.put("scenes", sceneArray)
            array.put(obj)
        }
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(array.toString())
    }
}
