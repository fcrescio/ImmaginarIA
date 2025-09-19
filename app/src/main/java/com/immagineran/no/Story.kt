package com.immagineran.no

data class CharacterAsset(
    val name: String,
    val description: String,
    val image: String? = null,
)

data class EnvironmentAsset(
    val name: String,
    val description: String,
    val image: String? = null,
)

data class Scene(
    val text: String,
    val environment: EnvironmentAsset? = null,
    val characters: List<CharacterAsset> = emptyList(),
    val image: String? = null,
)

data class Story(
    val id: Long,
    val title: String,
    val timestamp: Long,
    val content: String,
    val language: String? = null,
    val storyOriginal: String? = null,
    val storyEnglish: String? = null,
    val segments: List<String> = emptyList(),
    val processed: Boolean = false,
    val characters: List<CharacterAsset> = emptyList(),
    val environments: List<EnvironmentAsset> = emptyList(),
    val scenes: List<Scene> = emptyList(),
)
