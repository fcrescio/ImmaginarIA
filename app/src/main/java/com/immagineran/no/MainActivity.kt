package com.immagineran.no

import java.io.File
import java.text.DateFormat
import java.util.Date

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.firebase.FirebaseApp
import com.immagineran.no.ui.theme.ImmaginarIATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            ImmaginarIATheme {
                Surface(color = MaterialTheme.colors.background) {
                    var showSplash by remember { mutableStateOf(true) }
                    var showRecorder by remember { mutableStateOf(false) }
                    var showProcessing by remember { mutableStateOf(false) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showAdvanced by remember { mutableStateOf(false) }
                    var storyToResume by remember { mutableStateOf<Story?>(null) }
                    var storyToView by remember { mutableStateOf<Story?>(null) }
                    var processingProgress by remember { mutableStateOf(0f) }
                    val processingLogs = remember { mutableStateListOf<String>() }
                    var processingWorkId by rememberSaveable { mutableStateOf<String?>(null) }
                    var lastObservedWorkId by remember { mutableStateOf<String?>(null) }
                    var lastLogId by remember { mutableStateOf<Long?>(null) }
                    val workManager = remember { WorkManager.getInstance(this@MainActivity.applicationContext) }
                    val workInfos by workManager
                        .getWorkInfosByTagLiveData(StoryProcessingWorker.WORK_TAG)
                        .observeAsState(emptyList())
                    val trackedWorkInfo = remember(processingWorkId, workInfos) {
                        processingWorkId?.let { id ->
                            workInfos.firstOrNull { it.id.toString() == id }
                        } ?: workInfos.firstOrNull { !it.state.isFinished }
                    }
                    val scope = rememberCoroutineScope()
                    val activityContext = this@MainActivity
                    suspend fun enqueueProcessing(payload: StoryProcessingPayload) {
                        val payloadFile = withContext(Dispatchers.IO) {
                            activityContext.writeStoryProcessingPayload(payload)
                        }
                        val request = OneTimeWorkRequestBuilder<StoryProcessingWorker>()
                            .setInputData(
                                workDataOf(
                                    StoryProcessingWorker.KEY_PAYLOAD_PATH to payloadFile.absolutePath,
                                ),
                            )
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()
                            )
                            .addTag(StoryProcessingWorker.WORK_TAG)
                            .build()
                        processingWorkId = request.id.toString()
                        processingLogs.clear()
                        processingProgress = 0f
                        lastLogId = null
                        showProcessing = true
                        StoryProcessingWorker.enqueue(activityContext, request)
                    }
                    LaunchedEffect(Unit) {
                        delay(2000)
                        showSplash = false
                    }
                    LaunchedEffect(workInfos) {
                        if (processingWorkId == null) {
                            workInfos.firstOrNull { !it.state.isFinished }?.let { info ->
                                processingWorkId = info.id.toString()
                                showProcessing = true
                            }
                        }
                    }
                    LaunchedEffect(trackedWorkInfo?.id) {
                        val newId = trackedWorkInfo?.id?.toString()
                        if (newId != null && newId != lastObservedWorkId) {
                            processingLogs.clear()
                            processingProgress = 0f
                            lastLogId = null
                            lastObservedWorkId = newId
                        } else if (newId == null) {
                            lastObservedWorkId = null
                        }
                    }
                    LaunchedEffect(trackedWorkInfo?.progress) {
                        trackedWorkInfo?.progress?.let { data ->
                            val current = data.getInt(StoryProcessingWorker.KEY_STEP_CURRENT, 0)
                            val total = data.getInt(StoryProcessingWorker.KEY_STEP_TOTAL, 0)
                            if (total > 0) {
                                processingProgress = current / total.toFloat()
                            }
                            val logId = data.getLong(StoryProcessingWorker.KEY_LOG_ID, 0L)
                            if (logId != 0L && logId != lastLogId) {
                                data.getString(StoryProcessingWorker.KEY_LOG_MESSAGE)?.let { log ->
                                    processingLogs.add(log)
                                    lastLogId = logId
                                }
                            }
                        }
                    }
                    LaunchedEffect(trackedWorkInfo?.state) {
                        when (trackedWorkInfo?.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                processingProgress = 1f
                                showProcessing = false
                                processingWorkId = null
                                storyToResume = null
                            }
                            WorkInfo.State.FAILED -> {
                                showProcessing = false
                                processingWorkId = null
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.processing_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            WorkInfo.State.CANCELLED -> {
                                showProcessing = false
                                processingWorkId = null
                            }
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                                showProcessing = true
                            }
                            else -> Unit
                        }
                    }
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        when {
                            showProcessing -> {
                                ProcessingScreen(progress = processingProgress, logs = processingLogs)
                            }
                            showRecorder -> {
                                StoryCreationScreen(
                                    initialTitle = storyToResume?.title ?: getString(
                                        R.string.default_story_title,
                                        DateFormat.getDateTimeInstance().format(Date())
                                    ),
                                    initialSegments = storyToResume?.segments?.map { File(it) } ?: emptyList(),
                                    onDone = { segments, transcriptions, title ->
                                        showRecorder = false
                                        scope.launch {
                                            val allTranscribed = transcriptions.all { it != null }
                                            val storyId = storyToResume?.id ?: System.currentTimeMillis()
                                            val timestamp = storyToResume?.timestamp ?: System.currentTimeMillis()
                                            if (allTranscribed) {
                                                val payload = StoryProcessingPayload(
                                                    storyId = storyId,
                                                    prompt = getString(R.string.story_prompt),
                                                    transcriptions = transcriptions.filterNotNull(),
                                                    userTitle = title,
                                                    timestamp = timestamp,
                                                    segmentPaths = segments.filterNotNull().map { it.absolutePath },
                                                )
                                                enqueueProcessing(payload)
                                                storyToResume = null
                                            } else {
                                                val segmentPaths = segments.filterNotNull().map { it.absolutePath }
                                                if (storyToResume != null) {
                                                    withContext(Dispatchers.IO) {
                                                        storyToResume!!.segments
                                                            .filter { it !in segmentPaths }
                                                            .forEach { path ->
                                                                runCatching { File(path).delete() }
                                                            }
                                                    }
                                                }
                                                val resolvedTitle = resolveFinalTitle(
                                                    context = activityContext,
                                                    userTitle = title,
                                                    extractedTitleEnglish = null,
                                                    extractedTitleLocalized = null,
                                                    timestamp = timestamp,
                                                )
                                                val pendingStory = storyToResume?.copy(
                                                    title = resolvedTitle,
                                                    timestamp = timestamp,
                                                    segments = segmentPaths,
                                                    content = storyToResume?.content ?: "",
                                                    processed = false,
                                                    characters = emptyList(),
                                                    environments = emptyList(),
                                                    scenes = emptyList(),
                                                ) ?: Story(
                                                    id = storyId,
                                                    title = resolvedTitle,
                                                    timestamp = timestamp,
                                                    content = "",
                                                    segments = segmentPaths,
                                                    processed = false,
                                                )
                                                if (storyToResume != null) {
                                                    StoryRepository.updateStory(activityContext, pendingStory)
                                                    storyToResume = null
                                                } else {
                                                    StoryRepository.addStory(activityContext, pendingStory)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            showAdvanced -> {
                                AdvancedOptionsScreen(onBack = { showAdvanced = false })
                            }
                            showSettings -> {
                                SettingsScreen(
                                    onBack = { showSettings = false },
                                    onAdvanced = { showAdvanced = true },
                                )
                            }
                            storyToView != null -> {
                                StoryDetailScreen(
                                    story = storyToView!!,
                                    onBack = { storyToView = null },
                                    onRegenerateImages = { targetStory ->
                                        scope.launch {
                                            val payload = StoryProcessingPayload(
                                                storyId = targetStory.id,
                                                prompt = getString(R.string.story_prompt),
                                                transcriptions = emptyList(),
                                                userTitle = targetStory.title,
                                                timestamp = targetStory.timestamp,
                                                segmentPaths = targetStory.segments,
                                                regenerateImagesOnly = true,
                                            )
                                            enqueueProcessing(payload)
                                            storyToView = null
                                        }
                                    }
                                )
                            }
                            else -> {
                                StoryListScreen(
                                    onStartSession = {
                                        storyToResume = null
                                        showRecorder = true
                                    },
                                    onResumeStory = { story ->
                                        storyToResume = story
                                        showRecorder = true
                                    },
                                    onOpenSettings = { showSettings = true },
                                    onViewStory = { story -> storyToView = story }
                                )
                            }
                        }
                    }
            }
        }
    }
}

}

/**
 * Splash screen showing the placeholder logo and app title.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.retro_logo_placeholder),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.h3,
                color = MaterialTheme.colors.primary
            )
        }
    }
}

/**
 * Displays recorded stories and navigation actions.
 */
@Composable
fun StoryListScreen(
    onStartSession: () -> Unit,
    onResumeStory: (Story) -> Unit,
    onOpenSettings: () -> Unit,
    onViewStory: (Story) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processed by remember { mutableStateOf(emptyList<Story>()) }
    var pending by remember { mutableStateOf(emptyList<Story>()) }
    var exportingStoryId by remember { mutableStateOf<Long?>(null) }
    var storyPendingDeletion by remember { mutableStateOf<Story?>(null) }
    LaunchedEffect(Unit) {
        val stories = StoryRepository.getStories(context)
        processed = stories.filter { it.processed }
        pending = stories.filter { !it.processed }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_list_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = stringResource(R.string.unprocessed_stories_title),
                    style = MaterialTheme.typography.h6,
                )
            }
            if (pending.isEmpty()) {
                item { Text(text = stringResource(R.string.no_unprocessed_stories)) }
            } else {
                items(pending) { story ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(text = story.title, modifier = Modifier.weight(1f))
                        Button(onClick = { onResumeStory(story) }) {
                            Text(text = stringResource(R.string.resume))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            StoryRepository.deleteStory(context, story)
                            pending = pending.filter { it.id != story.id }
                        }) {
                            Text(text = stringResource(R.string.delete))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Text(
                    text = stringResource(R.string.processed_stories_title),
                    style = MaterialTheme.typography.h6,
                )
            }
            if (processed.isEmpty()) {
                item { Text(text = stringResource(R.string.no_stories)) }
            } else {
                items(processed) { story ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(text = story.title, modifier = Modifier.weight(1f))
                        Button(onClick = { onViewStory(story) }) {
                            Text(text = stringResource(R.string.view))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    exportingStoryId = story.id
                                    try {
                                        val file = StoryExporter.export(context, story)
                                        if (file != null) {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                file,
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, story.title)
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    context.getString(
                                                        R.string.export_story_message,
                                                        story.title,
                                                    ),
                                                )
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    sendIntent,
                                                    context.getString(
                                                        R.string.share_story_chooser_title,
                                                        story.title,
                                                    ),
                                                ),
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.export_story_error),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.export_story_error),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } finally {
                                        exportingStoryId = null
                                    }
                                }
                            },
                            enabled = exportingStoryId != story.id,
                        ) {
                            Text(text = stringResource(R.string.export_story))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { storyPendingDeletion = story }) {
                            Text(text = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
        val storyToDelete = storyPendingDeletion
        if (storyToDelete != null) {
            AlertDialog(
                onDismissRequest = { storyPendingDeletion = null },
                title = { Text(text = stringResource(R.string.delete_completed_story_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.delete_completed_story_message,
                            storyToDelete.title
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        StoryRepository.deleteStory(context, storyToDelete)
                        processed = processed.filter { it.id != storyToDelete.id }
                        storyPendingDeletion = null
                    }) {
                        Text(text = stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { storyPendingDeletion = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        Button(
            onClick = { onStartSession() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.start_new_session))
        }
        }
    }
}

