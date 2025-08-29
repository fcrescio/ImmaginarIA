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
            var storyToResume by remember { mutableStateOf<Story?>(null) }
            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }
            if (showSplash) {
                SplashScreen()
            } else {
                if (showRecorder) {
                    StoryCreationScreen(
                        initialSegments = storyToResume?.segments?.map { File(it) } ?: emptyList(),
                        onDone = { segments ->
                            val context = this@MainActivity
                            if (storyToResume != null) {
                                val updated = storyToResume!!.copy(
                                    segments = segments.map { it.absolutePath }
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
                                    content = "",
                                    segments = segments.map { it.absolutePath },
                                    processed = false
                                )
                                StoryRepository.addStory(context, story)
                            }
                            showRecorder = false
                        }
                    )
                } else {
                    StoryListScreen(
                        onStartSession = {
                            storyToResume = null
                            showRecorder = true
                        },
                        onResumeStory = { story ->
                            storyToResume = story
                            showRecorder = true
                        }
                    )
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
fun StoryListScreen(onStartSession: () -> Unit, onResumeStory: (Story) -> Unit) {
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
            style = MaterialTheme.typography.h5
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(text = story.title, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            StoryRepository.deleteStory(context, story)
                            processed = processed.filter { it.id != story.id }
                        }) {
                            Text(text = stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onStartSession() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(text = stringResource(R.string.start_new_session))
        }
    }
}
