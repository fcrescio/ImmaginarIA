package com.immagineran.no

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            var showRecorder by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var storyToResume by remember { mutableStateOf<Story?>(null) }
            val scope = rememberCoroutineScope()
            val pipeline = remember { ProcessingPipeline(listOf(StoryStitchingStep())) }
            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }
            if (showSplash) {
                SplashScreen()
            } else {
                when {
                    showRecorder -> {
                            StoryCreationScreen(
                                initialSegments = storyToResume?.segments?.map { File(it) } ?: emptyList(),
                                onDone = { segments, transcriptions ->
                                    val context = this@MainActivity
                                    scope.launch {
                                        val allTranscribed = transcriptions.all { it != null }
                                        val content = if (allTranscribed) {
                                            val prompt = getString(R.string.story_prompt)
                                            val ctx = ProcessingContext(prompt, transcriptions.filterNotNull())
                                            pipeline.run(ctx).story ?: ""
                                        } else {
                                            ""
                                        }
                                        val processed = content.isNotBlank()
                                        val segmentPaths = if (processed) {
                                            segments.forEach { it.delete() }
                                            emptyList()
                                        } else {
                                            segments.map { it.absolutePath }
                                        }
                                        if (storyToResume != null) {
                                            val updated = storyToResume!!.copy(
                                                segments = segmentPaths,
                                                content = content,
                                                processed = processed,
                                            )
                                            StoryRepository.updateStory(context, updated)
                                            storyToResume = null
                                        } else {
                                            val title = getString(
                                                R.string.default_story_title,
                                                DateFormat.getDateTimeInstance().format(Date())
                                            )
                                            val story = Story(
                                                id = System.currentTimeMillis(),
                                                title = title,
                                                content = content,
                                                segments = segmentPaths,
                                                processed = processed,
                                            )
                                            StoryRepository.addStory(context, story)
                                        }
                                        showRecorder = false
                                    }
                                }
                            )
                        }
                    showSettings -> {
                        SettingsScreen(onBack = { showSettings = false })
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
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(id = R.string.app_name), style = MaterialTheme.typography.h3)
    }
}

@Composable
fun StoryListScreen(
    onStartSession: () -> Unit,
    onResumeStory: (Story) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var processed by remember { mutableStateOf(emptyList<Story>()) }
    var pending by remember { mutableStateOf(emptyList<Story>()) }
    LaunchedEffect(Unit) {
        val stories = StoryRepository.getStories(context)
        processed = stories.filter { it.processed }
        pending = stories.filter { !it.processed }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.story_list_title),
            style = MaterialTheme.typography.h5,
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = story.title, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                StoryRepository.deleteStory(context, story)
                                processed = processed.filter { it.id != story.id }
                            }) {
                                Text(text = stringResource(R.string.delete))
                            }
                        }
                        if (story.content.isNotBlank()) {
                            Text(
                                text = story.content,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onStartSession() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.start_new_session))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onOpenSettings() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.settings))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { throw RuntimeException("Test Crash") },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.test_crash))
        }
    }
}

