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
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }
            if (showSplash) {
                SplashScreen()
            } else {
                StoryListScreen(onStartSession = {
                    val context = this@MainActivity
                    val title = getString(
                        R.string.default_story_title,
                        DateFormat.getDateTimeInstance().format(Date())
                    )
                    val story = Story(id = System.currentTimeMillis(), title = title, content = "")
                    StoryRepository.addStory(context, story)
                })
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
fun StoryListScreen(onStartSession: () -> Unit) {
    val context = LocalContext.current
    var stories by remember { mutableStateOf(emptyList<Story>()) }
    LaunchedEffect(Unit) {
        stories = StoryRepository.getStories(context)
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.story_list_title),
            style = MaterialTheme.typography.h5
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (stories.isEmpty()) {
            Text(text = stringResource(R.string.no_stories))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(stories) { story ->
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        Button(
            onClick = {
                onStartSession()
                stories = StoryRepository.getStories(context)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(text = stringResource(R.string.start_new_session))
        }
    }
}
