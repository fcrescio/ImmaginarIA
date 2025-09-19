package com.immagineran.no

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRail
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalDensity
import java.text.DateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

/**
 * Displays a story's details using a tabbed layout reminiscent of classic starship interfaces.
 */
@Composable
fun StoryDetailScreen(story: Story, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(StoryTab.STORY) }
    var currentStory by remember { mutableStateOf(story) }
    var editingTitle by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (editingTitle) {
                    TextField(
                        value = currentStory.title,
                        onValueChange = { currentStory = currentStory.copy(title = it) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        StoryRepository.updateStory(context, currentStory)
                        editingTitle = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save_title))
                    }
                } else {
                    Text(
                        currentStory.title,
                        style = MaterialTheme.typography.h5,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { editingTitle = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_title))
                    }
                }
            }
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(currentStory.timestamp)),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(selectedTab.title),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 8.dp)
            )
            when (selectedTab) {
                StoryTab.STORY -> StoryContent(currentStory)
                StoryTab.CHARACTERS -> CharacterList(currentStory.characters)
                StoryTab.ENVIRONMENTS -> EnvironmentList(currentStory.environments)
                StoryTab.SCENES -> SceneList(currentStory.scenes)
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
    val paragraphs = remember(
        story.storyOriginal,
        story.storyEnglish,
        story.content,
    ) {
        parseStoryboardParagraphs(story)
    }
    if (paragraphs.isEmpty()) return
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(paragraphs) { paragraph ->
            Text(paragraph)
        }
    }
}

private fun parseStoryboardParagraphs(story: Story): List<String> {
    val candidates = listOfNotNull(
        story.storyOriginal?.takeIf { it.isNotBlank() },
        story.storyEnglish?.takeIf { it.isNotBlank() },
        story.content.takeIf { it.isNotBlank() },
    )

    candidates.forEach { candidate ->
        val paragraphs = extractParagraphs(candidate)
        if (paragraphs.isNotEmpty()) {
            return paragraphs
        }
    }

    return emptyList()
}

private fun extractParagraphs(content: String): List<String> {
    val trimmed = content.trim()
    if (trimmed.isBlank()) return emptyList()

    val narrativeKeys = listOf("storyboard", "story", "text", "summary", "synopsis")
    val paragraphs = mutableListOf<String>()

    fun MutableList<String>.addParagraphs(raw: String) {
        raw.split(Regex("\\r?\\n\\s*\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { paragraph ->
                if (!contains(paragraph)) {
                    add(paragraph)
                }
            }
    }

    fun MutableList<String>.extract(value: Any?) {
        when (value) {
            is String -> addParagraphs(value)
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    extract(value.opt(i))
                }
            }
            is JSONObject -> {
                narrativeKeys.forEach { key ->
                    if (value.has(key)) {
                        extract(value.opt(key))
                    }
                }
            }
        }
    }

    runCatching { JSONObject(trimmed) }.getOrNull()?.let { obj ->
        narrativeKeys.forEach { key ->
            if (obj.has(key)) {
                paragraphs.extract(obj.opt(key))
            }
        }
        if (paragraphs.isNotEmpty()) {
            return paragraphs
        }
    }

    runCatching { JSONArray(trimmed) }.getOrNull()?.let { array ->
        for (i in 0 until array.length()) {
            paragraphs.extract(array.opt(i))
        }
        if (paragraphs.isNotEmpty()) {
            return paragraphs
        }
    }

    paragraphs.addParagraphs(trimmed)
    return paragraphs
}

@Composable
private fun CharacterList(characters: List<CharacterAsset>) {
    val galleryItems = remember(characters) {
        characters.mapNotNull { character ->
            character.image?.let {
                FullScreenImageData(
                    path = it,
                    title = character.displayName,
                    description = character.displayDescription
                )
            }
        }
    }
    val pathToIndex = remember(galleryItems) {
        galleryItems.mapIndexed { index, data -> data.path to index }.toMap()
    }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(characters) { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                c.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = c.displayName,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    pathToIndex[it]?.let { index ->
                                        selectedIndex = index
                                    }
                                }
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(c.displayName, style = MaterialTheme.typography.subtitle1)
                    Text(c.displayDescription)
                    if (c.image == null) {
                        Text(stringResource(R.string.image_generation_error))
                    }
                }
            }
        }
    }
    val currentIndex = selectedIndex
    if (currentIndex != null && galleryItems.isNotEmpty()) {
        FullScreenImageGallery(
            images = galleryItems,
            currentIndex = currentIndex,
            onIndexChange = { selectedIndex = it },
            onDismiss = { selectedIndex = null }
        )
    }
}

@Composable
private fun EnvironmentList(environments: List<EnvironmentAsset>) {
    val galleryItems = remember(environments) {
        environments.mapNotNull { environment ->
            environment.image?.let {
                FullScreenImageData(
                    path = it,
                    title = environment.displayName,
                    description = environment.displayDescription
                )
            }
        }
    }
    val pathToIndex = remember(galleryItems) {
        galleryItems.mapIndexed { index, data -> data.path to index }.toMap()
    }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(environments) { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                e.image?.let {
                    val bmp = BitmapFactory.decodeFile(it)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = e.displayName,
                            modifier = Modifier
                                .size(64.dp)
                                .clickable {
                                    pathToIndex[it]?.let { index ->
                                        selectedIndex = index
                                    }
                                }
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(e.displayName, style = MaterialTheme.typography.subtitle1)
                    Text(e.displayDescription)
                    if (e.image == null) {
                        Text(stringResource(R.string.image_generation_error))
                    }
                }
            }
        }
    }
    val currentIndex = selectedIndex
    if (currentIndex != null && galleryItems.isNotEmpty()) {
        FullScreenImageGallery(
            images = galleryItems,
            currentIndex = currentIndex,
            onIndexChange = { selectedIndex = it },
            onDismiss = { selectedIndex = null }
        )
    }
}

@Composable
private fun SceneList(scenes: List<Scene>) {
    val galleryItems = remember(scenes) {
        scenes.mapNotNull { scene ->
            scene.image?.let {
                FullScreenImageData(
                    path = it,
                    title = null,
                    description = scene.displayCaptionOriginal
                )
            }
        }
    }
    val pathToIndex = remember(galleryItems) {
        galleryItems.mapIndexed { index, data -> data.path to index }.toMap()
    }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
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
                                .clickable {
                                    pathToIndex[it]?.let { index ->
                                        selectedIndex = index
                                    }
                                }
                        )
                    }
                }
                Text(s.displayCaptionOriginal, modifier = Modifier.padding(top = 4.dp))
                if (s.image == null) {
                    Text(stringResource(R.string.image_generation_error))
                }
            }
        }
    }
    val currentIndex = selectedIndex
    if (currentIndex != null && galleryItems.isNotEmpty()) {
        FullScreenImageGallery(
            images = galleryItems,
            currentIndex = currentIndex,
            onIndexChange = { selectedIndex = it },
            onDismiss = { selectedIndex = null }
        )
    }
}

//@Composable
private data class FullScreenImageData(
    val path: String,
    val title: String?,
    val description: String?
)

@Composable
private fun FullScreenImageGallery(
    images: List<FullScreenImageData>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val imageData = images.getOrNull(currentIndex) ?: return
    val bitmap = remember(imageData.path) { BitmapFactory.decodeFile(imageData.path) }
    if (bitmap == null) {
        onDismiss()
        return
    }
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .pointerInput(currentIndex, images.size) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                totalDrag > swipeThresholdPx && currentIndex > 0 ->
                                    onIndexChange(currentIndex - 1)
                                totalDrag < -swipeThresholdPx && currentIndex < images.lastIndex ->
                                    onIndexChange(currentIndex + 1)
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    )
                }
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = imageData.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
                contentScale = ContentScale.Fit
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                imageData.title?.let {
                    Text(it, color = Color.White, style = MaterialTheme.typography.h6)
                }
                imageData.description?.let {
                    Text(it, color = Color.White, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

