package com.immagineran.no

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * Resolves a final story title using user input and extracted metadata.
 */
fun resolveFinalTitle(
    context: Context,
    userTitle: String,
    extractedTitleEnglish: String?,
    extractedTitleLocalized: String?,
    timestamp: Long,
): String {
    val dateLabel = DateFormat.getDateTimeInstance().format(Date(timestamp))
    val defaultTitle = context.getString(R.string.default_story_title, dateLabel)
    val sanitizedExtracted = sanitizeExtractedTitle(extractedTitleEnglish)
    val localizedCandidate = extractedTitleLocalized?.takeIf { it.isNotBlank() }
    return when {
        sanitizedExtracted != null -> {
            val displayTitle = localizedCandidate ?: sanitizedExtracted
            context.getString(
                R.string.story_title_with_date,
                displayTitle,
                dateLabel,
            )
        }
        localizedCandidate != null -> localizedCandidate
        userTitle.isNotBlank() -> userTitle
        else -> defaultTitle
    }
}

private fun sanitizeExtractedTitle(raw: String?): String? {
    val normalized = raw
        ?.replace('\n', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?: return null
    if (normalized.isEmpty()) return null
    val withoutQuotes = normalized.trim('"', '\'', '“', '”')
    if (withoutQuotes.isEmpty()) return null
    val cleaned = withoutQuotes.trimEnd('.', '!', '?', ':', ';', ',')
    if (cleaned.isEmpty()) return null
    if (cleaned.length > 48) return null
    val wordCount = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
    if (wordCount > 8) return null
    return cleaned
}
