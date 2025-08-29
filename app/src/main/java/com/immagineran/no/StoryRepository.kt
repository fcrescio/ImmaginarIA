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
            result.add(
                Story(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    content = obj.getString("content")
                )
            )
        }
        return result
    }

    fun addStory(context: Context, story: Story) {
        val stories = getStories(context).toMutableList()
        stories.add(story)
        val array = JSONArray()
        stories.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("content", s.content)
            array.put(obj)
        }
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(array.toString())
    }
}
