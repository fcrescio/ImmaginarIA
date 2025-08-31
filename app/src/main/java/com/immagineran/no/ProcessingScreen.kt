package com.immagineran.no

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Screen displaying progress of the processing pipeline.
 *
 * @param progress Value between 0f and 1f indicating completion percentage.
 * @param logs Log messages to show in a terminal-like list.
 */
@Composable
fun ProcessingScreen(progress: Float, logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = stringResource(R.string.processing), style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
        ) {
            items(logs) { log ->
                Text(text = log, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

