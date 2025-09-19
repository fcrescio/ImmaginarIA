package com.immagineran.no

data class CharacterAsset(
    val name: String,
    val description: String,
    val image: String? = null,
    val nameEnglish: String? = null,
    val descriptionEnglish: String? = null,
) {
    val displayName: String
        get() = nameEnglish?.takeIf { it.isNotBlank() } ?: name

    val displayDescription: String
        get() = descriptionEnglish?.takeIf { it.isNotBlank() } ?: description

    fun matchesName(candidate: String): Boolean {
        if (candidate.equals(nameEnglish, ignoreCase = true)) return true
        if (candidate.equals(name, ignoreCase = true)) return true
        return candidate.equals(displayName, ignoreCase = true)
    }
}

data class EnvironmentAsset(
    val name: String,
    val description: String,
    val image: String? = null,
    val nameEnglish: String? = null,
    val descriptionEnglish: String? = null,
) {
    val displayName: String
        get() = nameEnglish?.takeIf { it.isNotBlank() } ?: name

    val displayDescription: String
        get() = descriptionEnglish?.takeIf { it.isNotBlank() } ?: description

    fun matchesName(candidate: String): Boolean {
        if (candidate.equals(nameEnglish, ignoreCase = true)) return true
        if (candidate.equals(name, ignoreCase = true)) return true
        return candidate.equals(displayName, ignoreCase = true)
    }
}

data class Scene(
    val captionOriginal: String,
    val captionEnglish: String,
    val environment: EnvironmentAsset? = null,
    val characters: List<CharacterAsset> = emptyList(),
    val image: String? = null,
) {
    val displayCaptionOriginal: String
        get() = captionOriginal.takeIf { it.isNotBlank() } ?: captionEnglish

    val displayCaptionEnglish: String
        get() = captionEnglish.takeIf { it.isNotBlank() } ?: captionOriginal
}

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
