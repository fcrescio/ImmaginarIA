package com.immagineran.no

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Displays a story's details using a tabbed layout reminiscent of classic starship interfaces.
 */
@Composable
fun StoryDetailScreen(story: Story, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(StoryTab.STORY) }
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                selected = selectedTab == StoryTab.STORY,
                onClick = { selectedTab = StoryTab.STORY },
                icon = {
                    Icon(Icons.Filled.MenuBook, contentDescription = stringResource(R.string.story_tab))
                },
                label = { Text(stringResource(R.string.story_tab)) }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.CHARACTERS,
                onClick = { selectedTab = StoryTab.CHARACTERS },
                icon = {
                    Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.characters_title))
                },
                label = { Text(stringResource(R.string.characters_title)) }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.ENVIRONMENTS,
                onClick = { selectedTab = StoryTab.ENVIRONMENTS },
                icon = {
                    Icon(Icons.Filled.Public, contentDescription = stringResource(R.string.environments_title))
                },
                label = { Text(stringResource(R.string.environments_title)) }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.SCENES,
                onClick = { selectedTab = StoryTab.SCENES },
                icon = {
                    Icon(Icons.Filled.Movie, contentDescription = stringResource(R.string.scenes_title))
                },
                label = { Text(stringResource(R.string.scenes_title)) }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                story.title,
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(top = 8.dp)
            )
            when (selectedTab) {
                StoryTab.STORY -> StoryContent(story)
                StoryTab.CHARACTERS -> CharacterList(story.characters)
                StoryTab.ENVIRONMENTS -> EnvironmentList(story.environments)
                StoryTab.SCENES -> SceneList(story.scenes)
            }
        }
    }
}

private enum class StoryTab { STORY, CHARACTERS, ENVIRONMENTS, SCENES }

@Composable
private fun StoryContent(story: Story) {
    if (story.content.isBlank()) return
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        item {
            Text(stringResource(R.string.story_tab), style = MaterialTheme.typography.h6)
        }
        item {
            Text(story.content, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CharacterList(characters: List<CharacterAsset>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        item { Text(stringResource(R.string.characters_title), style = MaterialTheme.typography.h6) }
        items(characters) { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                c.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = c.name,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(c.name, style = MaterialTheme.typography.subtitle1)
                    Text(c.description)
                    if (c.image == null) {
                        Text(stringResource(R.string.image_generation_error))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentList(environments: List<EnvironmentAsset>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        item { Text(stringResource(R.string.environments_title), style = MaterialTheme.typography.h6) }
        items(environments) { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                e.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = e.name,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(e.name, style = MaterialTheme.typography.subtitle1)
                    Text(e.description)
                    if (e.image == null) {
                        Text(stringResource(R.string.image_generation_error))
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneList(scenes: List<Scene>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        item { Text(stringResource(R.string.scenes_title), style = MaterialTheme.typography.h6) }
        items(scenes) { s ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                s.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(128.dp)
                                .fillMaxWidth()
                        )
                    }
                }
                Text(s.text, modifier = Modifier.padding(top = 4.dp))
                if (s.image == null) {
                    Text(stringResource(R.string.image_generation_error))
                }
            }
        }
    }
}

