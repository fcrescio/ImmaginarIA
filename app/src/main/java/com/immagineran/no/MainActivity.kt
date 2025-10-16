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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        delay(2000)
                        showSplash = false
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
                                        val context = this@MainActivity
                                        showRecorder = false
                                        scope.launch {
                                            val allTranscribed = transcriptions.all { it != null }
                                            val storyId = storyToResume?.id ?: System.currentTimeMillis()
                                            val timestamp = storyToResume?.timestamp ?: System.currentTimeMillis()
                                            val procContext = if (allTranscribed) {
                                                val prompt = getString(R.string.story_prompt)
                                                ProcessingContext(prompt, transcriptions.filterNotNull(), storyId)
                                            } else null
                                            if (procContext != null) {
                                                showProcessing = true
                                                processingLogs.clear()
                                                processingProgress = 0f
                                                withContext(Dispatchers.IO) {
                                                    val steps = mutableListOf<ProcessingStep>(
                                                        StoryStitchingStep(this@MainActivity),
                                                        CharacterExtractionStep(this@MainActivity),
                                                    )
                                                    if (
                                                        SettingsManager.isCharacterImageGenerationEnabled(
                                                            this@MainActivity
                                                        )
                                                    ) {
                                                        steps.add(CharacterImageGenerationStep(this@MainActivity))
                                                    }
                                                    steps.add(EnvironmentExtractionStep(this@MainActivity))
                                                    if (
                                                        SettingsManager.isEnvironmentImageGenerationEnabled(
                                                            this@MainActivity
                                                        )
                                                    ) {
                                                        steps.add(EnvironmentImageGenerationStep(this@MainActivity))
                                                    }
                                                    steps.add(SceneCompositionStep(this@MainActivity))
                                                    steps.add(SceneImageGenerationStep(this@MainActivity))
                                                    ProcessingPipeline(steps).run(
                                                        procContext,
                                                        onProgress = { current, total, message ->
                                                            withContext(Dispatchers.Main) {
                                                                processingProgress = current / total.toFloat()
                                                                val stepName = message
                                                                    .removeSuffix("Step")
                                                                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                                                                    .uppercase()
                                                                processingLogs.add("[$current/$total] >>> $stepName")
                                                            }
                                                        },
                                                        onLog = { logMessage ->
                                                            withContext(Dispatchers.Main) {
                                                                processingLogs.add(logMessage)
                                                            }
                                                        }
                                                    )
                                                }
                                                processingLogs.add(getString(R.string.processing_complete))
                                                showProcessing = false
                                            }
                                            val content = procContext?.storyJson
                                                ?: procContext?.story
                                                ?: ""
                                            val processed = !procContext?.story.isNullOrBlank()
                                            val segmentPaths = if (processed) {
                                                segments.filterNotNull().forEach { it.delete() }
                                                emptyList()
                                            } else {
                                                segments.filterNotNull().map { it.absolutePath }
                                            }
                                            if (storyToResume != null) {
                                                val updated = storyToResume!!.copy(
                                                    title = title,
                                                    segments = segmentPaths,
                                                    content = content,
                                                    language = procContext?.storyLanguage
                                                        ?: storyToResume!!.language,
                                                    storyOriginal = procContext?.storyOriginal
                                                        ?: storyToResume!!.storyOriginal,
                                                    storyEnglish = procContext?.storyEnglish
                                                        ?: storyToResume!!.storyEnglish,
                                                    processed = processed,
                                                    characters = procContext?.characters ?: emptyList(),
                                                    environments = procContext?.environments ?: emptyList(),
                                                    scenes = procContext?.scenes ?: emptyList(),
                                                )
                                                StoryRepository.updateStory(context, updated)
                                                storyToResume = null
                                            } else {
                                                val story = Story(
                                                    id = storyId,
                                                    title = title,
                                                    timestamp = timestamp,
                                                    content = content,
                                                    language = procContext?.storyLanguage,
                                                    storyOriginal = procContext?.storyOriginal,
                                                    storyEnglish = procContext?.storyEnglish,
                                                    segments = segmentPaths,
                                                    processed = processed,
                                                    characters = procContext?.characters ?: emptyList(),
                                                    environments = procContext?.environments ?: emptyList(),
                                                    scenes = procContext?.scenes ?: emptyList(),
                                                )
                                                StoryRepository.addStory(context, story)
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
                                StoryDetailScreen(story = storyToView!!, onBack = { storyToView = null })
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

