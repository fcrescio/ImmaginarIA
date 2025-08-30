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

data class Story(
    val id: Long,
    val title: String,
    val content: String,
    val segments: List<String> = emptyList(),
    val processed: Boolean = false,
    val characters: List<CharacterAsset> = emptyList(),
    val environments: List<EnvironmentAsset> = emptyList(),
)
