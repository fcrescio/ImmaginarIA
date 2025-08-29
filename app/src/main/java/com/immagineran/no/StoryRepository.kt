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
            result.add(
                Story(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    content = obj.optString("content", ""),
                    segments = segments,
                    processed = obj.optBoolean("processed", false)
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
            obj.put("content", s.content)
            obj.put("processed", s.processed)
            val segmentsArray = JSONArray()
            s.segments.forEach { segmentsArray.put(it) }
            obj.put("segments", segmentsArray)
            array.put(obj)
        }
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(array.toString())
    }
}
