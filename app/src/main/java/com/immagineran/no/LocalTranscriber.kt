package com.immagineran.no

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File

/**
 * Simple wrapper around the on-device transcription engine.
 *
 * All failures are logged to Logcat and forwarded to Crashlytics so that
 * transcription issues can be debugged more easily in production.
 */
class LocalTranscriber(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
) {
    fun transcribe(file: File): String? {
        return try {
            // TODO: Invoke real local ASR engine
            "" // Placeholder for the transcription result
        } catch (e: Exception) {
            crashlytics.log("Transcription failed for ${file.name}")
            crashlytics.recordException(e)
            Log.e("LocalTranscriber", "Transcription failed for ${file.name}", e)
            null
        }
    }
}
