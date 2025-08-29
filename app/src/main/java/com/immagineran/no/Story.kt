package com.immagineran.no

data class Story(
    val id: Long,
    val title: String,
    val content: String,
    val segments: List<String> = emptyList(),
    val processed: Boolean = false
)
