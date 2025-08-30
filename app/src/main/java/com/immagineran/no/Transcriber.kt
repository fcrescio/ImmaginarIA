package com.immagineran.no

import java.io.File

/**
 * Abstraction for different transcription providers.
 */
interface Transcriber {
    /**
     * Transcribes the given audio file and returns the recognized text or null on failure.
     */
    suspend fun transcribe(file: File): String?
}

