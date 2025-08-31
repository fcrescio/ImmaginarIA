package com.immagineran.no

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
            val characters = mutableListOf<CharacterAsset>()
            val charArray = obj.optJSONArray("characters")
            if (charArray != null) {
                for (j in 0 until charArray.length()) {
                    val cObj = charArray.optJSONObject(j) ?: continue
                    characters.add(
                        CharacterAsset(
                            name = cObj.optString("name"),
                            description = cObj.optString("description"),
                            image = cObj.optString("image", null)
                        )
                    )
                }
            }
            val environments = mutableListOf<EnvironmentAsset>()
            val envArray = obj.optJSONArray("environments")
            if (envArray != null) {
                for (j in 0 until envArray.length()) {
                    val eObj = envArray.optJSONObject(j) ?: continue
                    environments.add(
                        EnvironmentAsset(
                            name = eObj.optString("name"),
                            description = eObj.optString("description"),
                            image = eObj.optString("image", null)
                        )
                    )
                }
            }
            val scenes = mutableListOf<Scene>()
            val sceneArray = obj.optJSONArray("scenes")
            if (sceneArray != null) {
                for (j in 0 until sceneArray.length()) {
                    val sObj = sceneArray.optJSONObject(j) ?: continue
                    val text = sObj.optString("text")
                    val envName = sObj.optString("environment")
                    val env = environments.find { it.name == envName }
                    val chars = mutableListOf<CharacterAsset>()
                    val names = sObj.optJSONArray("characters")
                    if (names != null) {
                        for (k in 0 until names.length()) {
                            val n = names.optString(k)
                            characters.find { it.name == n }?.let { chars.add(it) }
                        }
                    }
                    scenes.add(
                        Scene(
                            text = text,
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
                    content = obj.optString("content", ""),
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
            val segmentsArray = JSONArray()
            s.segments.forEach { segmentsArray.put(it) }
            obj.put("segments", segmentsArray)
            val charArray = JSONArray()
            s.characters.forEach { c ->
                val cObj = JSONObject()
                cObj.put("name", c.name)
                cObj.put("description", c.description)
                c.image?.let { cObj.put("image", it) }
                charArray.put(cObj)
            }
            obj.put("characters", charArray)
            val envArray = JSONArray()
            s.environments.forEach { e ->
                val eObj = JSONObject()
                eObj.put("name", e.name)
                eObj.put("description", e.description)
                e.image?.let { eObj.put("image", it) }
                envArray.put(eObj)
            }
            obj.put("environments", envArray)
            val sceneArray = JSONArray()
            s.scenes.forEach { scene ->
                val scObj = JSONObject()
                scObj.put("text", scene.text)
                scene.environment?.let { scObj.put("environment", it.name) }
                val charNames = JSONArray()
                scene.characters.forEach { c -> charNames.put(c.name) }
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
