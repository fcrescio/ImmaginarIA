package com.immagineran.no

import androidx.annotation.StringRes

/**
 * Available transcription backends.
 */
enum class TranscriptionMethod(@StringRes val labelRes: Int) {
    LOCAL(R.string.transcription_method_local),
    GROQ(R.string.transcription_method_groq);
}

