package com.immagineran.no

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/**
 * Worker responsible for executing the story processing pipeline in the background.
 */
class StoryProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val payloadPath = inputData.getString(KEY_PAYLOAD_PATH) ?: return Result.failure()
        val payloadFile = File(payloadPath)
        if (!payloadFile.exists()) return Result.failure()

        val payload = runCatching { readStoryProcessingPayload(payloadFile) }.getOrElse {
            payloadFile.delete()
            return Result.failure()
        }

        val processingContext = ProcessingContext(payload.prompt, payload.transcriptions, payload.storyId)
        val steps = buildSteps()
        ensureChannel()
        setForeground(createForegroundInfo(applicationContext.getString(R.string.processing)))
        emitLog(applicationContext.getString(R.string.processing))

        val result = runCatching {
            ProcessingPipeline(steps).run(
                processingContext,
                onProgress = { current, total, message ->
                    updateProgress(current, total, message)
                },
                onLog = { message ->
                    if (message.isNotBlank()) {
                        emitLog(message)
                    }
                }
            )
        }.mapCatching {
            finalizeStory(processingContext, payload)
        }

        payloadFile.delete()

        return result.fold(
            onSuccess = {
                emitLog(applicationContext.getString(R.string.processing_complete))
                Result.success()
            },
            onFailure = {
                emitLog(applicationContext.getString(R.string.processing_failed))
                Result.failure()
            }
        )
    }

    private fun buildSteps(): MutableList<ProcessingStep> {
        val steps = mutableListOf<ProcessingStep>(
            StoryStitchingStep(applicationContext),
            CharacterExtractionStep(applicationContext),
        )
        if (SettingsManager.isCharacterImageGenerationEnabled(applicationContext)) {
            steps.add(CharacterImageGenerationStep(applicationContext))
        }
        steps.add(SceneCompositionStep(applicationContext))
        steps.add(EnvironmentExtractionStep(applicationContext))
        if (SettingsManager.isEnvironmentImageGenerationEnabled(applicationContext)) {
            steps.add(EnvironmentImageGenerationStep(applicationContext))
        }
        steps.add(SceneImageGenerationStep(applicationContext))
        return steps
    }

    private suspend fun finalizeStory(context: ProcessingContext, payload: StoryProcessingPayload) {
        val processed = !context.story.isNullOrBlank()
        val content = context.storyJson ?: context.story ?: ""
        val segments = if (processed) {
            payload.segmentPaths.forEach { path ->
                runCatching { File(path).delete() }
            }
            emptyList()
        } else {
            payload.segmentPaths
        }

        val existing = StoryRepository
            .getStories(applicationContext)
            .find { it.id == payload.storyId }

        val resolvedTitle = resolveFinalTitle(
            context = applicationContext,
            userTitle = payload.userTitle,
            extractedTitleEnglish = context.storyTitleEnglish,
            extractedTitleLocalized = context.storyTitle,
            timestamp = existing?.timestamp ?: payload.timestamp,
        )

        val updated = existing?.copy(
            title = resolvedTitle,
            segments = segments,
            content = content,
            language = context.storyLanguage ?: existing.language,
            storyOriginal = context.storyOriginal ?: existing.storyOriginal,
            storyEnglish = context.storyEnglish ?: existing.storyEnglish,
            processed = processed,
            characters = context.characters,
            environments = context.environments,
            scenes = context.scenes,
        ) ?: Story(
            id = payload.storyId,
            title = resolvedTitle,
            timestamp = payload.timestamp,
            content = content,
            language = context.storyLanguage,
            storyOriginal = context.storyOriginal,
            storyEnglish = context.storyEnglish,
            segments = segments,
            processed = processed,
            characters = context.characters,
            environments = context.environments,
            scenes = context.scenes,
        )

        if (existing != null) {
            StoryRepository.updateStory(applicationContext, updated)
        } else {
            StoryRepository.addStory(applicationContext, updated)
        }
    }

    private suspend fun updateProgress(current: Int, total: Int, rawMessage: String) {
        lastStep = current
        totalSteps = total
        val stepName = formatStep(rawMessage)
        lastStepName = stepName
        logCounter += 1
        val logEntry = "[$current/$total] >>> $stepName"
        setProgress(
            workDataOf(
                KEY_STEP_CURRENT to current,
                KEY_STEP_TOTAL to total,
                KEY_STEP_NAME to stepName,
                KEY_LOG_MESSAGE to logEntry,
                KEY_LOG_ID to logCounter,
            )
        )
        setForeground(createForegroundInfo(stepName))
    }

    private suspend fun emitLog(message: String) {
        logCounter += 1
        setProgress(
            workDataOf(
                KEY_STEP_CURRENT to lastStep,
                KEY_STEP_TOTAL to totalSteps,
                KEY_STEP_NAME to (lastStepName ?: ""),
                KEY_LOG_MESSAGE to message,
                KEY_LOG_ID to logCounter,
            )
        )
        setForeground(createForegroundInfo(message))
    }

    private fun formatStep(raw: String): String = raw
        .removeSuffix("Step")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .uppercase()
        .ifBlank { raw.uppercase() }

    private fun ensureChannel(): String {
        val channelId = "${applicationContext.packageName}.processing"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    applicationContext.getString(R.string.processing_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = applicationContext.getString(R.string.processing_notification_channel_description)
                }
                manager?.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    private fun createForegroundInfo(message: String?): ForegroundInfo {
        val channelId = ensureChannel()
        val content = message?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.processing)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.processing_notification_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_PAYLOAD_PATH = "payload_path"
        const val KEY_STEP_CURRENT = "progress_current"
        const val KEY_STEP_TOTAL = "progress_total"
        const val KEY_STEP_NAME = "progress_name"
        const val KEY_LOG_MESSAGE = "log_message"
        const val KEY_LOG_ID = "log_id"
        const val WORK_TAG = "story_processing"
        const val UNIQUE_WORK_NAME = "story_processing_unique"
        private const val NOTIFICATION_ID = 1001

        fun enqueue(
            context: Context,
            request: androidx.work.OneTimeWorkRequest,
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    private var logCounter = 0L
    private var lastStep = 0
    private var totalSteps = 0
    private var lastStepName: String? = null
}
