package com.immagineran.no

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.StringRes

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
                    Icon(
                        painterResource(R.drawable.ic_tab_story),
                        contentDescription = stringResource(R.string.story_tab)
                    )
                }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.CHARACTERS,
                onClick = { selectedTab = StoryTab.CHARACTERS },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_tab_characters),
                        contentDescription = stringResource(R.string.characters_title)
                    )
                }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.ENVIRONMENTS,
                onClick = { selectedTab = StoryTab.ENVIRONMENTS },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_tab_environments),
                        contentDescription = stringResource(R.string.environments_title)
                    )
                }
            )
            NavigationRailItem(
                selected = selectedTab == StoryTab.SCENES,
                onClick = { selectedTab = StoryTab.SCENES },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_tab_scenes),
                        contentDescription = stringResource(R.string.scenes_title)
                    )
                }
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
            Text(
                text = stringResource(selectedTab.title),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp)
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

private enum class StoryTab(@StringRes val title: Int) {
    STORY(R.string.story_tab),
    CHARACTERS(R.string.characters_title),
    ENVIRONMENTS(R.string.environments_title),
    SCENES(R.string.scenes_title)
}

@Composable
private fun StoryContent(story: Story) {
    if (story.content.isBlank()) return
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        item { Text(story.content) }
    }
}

@Composable
private fun CharacterList(characters: List<CharacterAsset>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(characters) { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                c.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = c.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { selectedImage = it }
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
    selectedImage?.let {
        FullScreenImage(imagePath = it) { selectedImage = null }
    }
}

@Composable
private fun EnvironmentList(environments: List<EnvironmentAsset>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(environments) { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                e.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = e.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { selectedImage = it }
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
    selectedImage?.let {
        FullScreenImage(imagePath = it) { selectedImage = null }
    }
}

@Composable
private fun SceneList(scenes: List<Scene>) {
    var selectedImage by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
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
                                .clickable { selectedImage = it }
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
    selectedImage?.let {
        FullScreenImage(imagePath = it) { selectedImage = null }
    }
}

@Composable
private fun FullScreenImage(imagePath: String, onDismiss: () -> Unit) {
    val bmp = BitmapFactory.decodeFile(imagePath)
    if (bmp != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

