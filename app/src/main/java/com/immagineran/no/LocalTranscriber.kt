package com.immagineran.no

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Simple wrapper around the on-device transcription engine.
 *
 * All failures are logged to Logcat and forwarded to Crashlytics so that
 * transcription issues can be debugged more easily in production.
 */
class LocalTranscriber(
    private val context: Context,
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
) : Transcriber {
    override suspend fun transcribe(file: File): String? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                cont.resume(text)
                recognizer.destroy()
            }

            override fun onError(error: Int) {
                crashlytics.log("Transcription failed for ${file.name} with code $error")
                crashlytics.recordException(Exception("SpeechRecognizer error code: $error"))
                Log.e("LocalTranscriber", "Transcription failed for ${file.name}: $error")
                cont.resume(null)
                recognizer.destroy()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val locale: Locale = context.resources.configuration.locales[0]
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, file.absolutePath)
        }
        recognizer.startListening(intent)
        cont.invokeOnCancellation { recognizer.destroy() }
    }
}

