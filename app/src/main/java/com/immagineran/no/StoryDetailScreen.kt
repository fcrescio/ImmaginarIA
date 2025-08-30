package com.immagineran.no

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun StoryDetailScreen(story: Story, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(story.title, style = MaterialTheme.typography.h5, modifier = Modifier.padding(top = 8.dp))
        if (story.content.isNotBlank()) {
            Text(story.content, modifier = Modifier.padding(vertical = 8.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { Text(stringResource(R.string.characters_title), style = MaterialTheme.typography.h6) }
            items(story.characters) { c ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    c.image?.let {
                        val bmp = BitmapFactory.decodeFile(it)
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = c.name, modifier = Modifier.size(64.dp))
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
            item { Text(stringResource(R.string.environments_title), style = MaterialTheme.typography.h6, modifier = Modifier.padding(top = 8.dp)) }
            items(story.environments) { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    e.image?.let {
                        val bmp = BitmapFactory.decodeFile(it)
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = e.name, modifier = Modifier.size(64.dp))
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
}
