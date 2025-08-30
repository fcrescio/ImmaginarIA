package com.immagineran.no

import android.content.Context

/**
 * Creates [Transcriber] instances based on the chosen [TranscriptionMethod].
 */
object TranscriberFactory {
    fun create(context: Context, method: TranscriptionMethod): Transcriber = when (method) {
        TranscriptionMethod.LOCAL -> LocalTranscriber(context)
        TranscriptionMethod.GROQ -> GroqTranscriber()
    }
}

