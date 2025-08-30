package com.immagineran.no

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTranscription by remember { mutableStateOf(SettingsManager.getTranscriptionMethod(context)) }
    var selectedStyle by remember { mutableStateOf(SettingsManager.getImageStyle(context)) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.transcription_method),
            style = MaterialTheme.typography.h6,
        )
        TranscriptionMethod.values().forEach { method ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = method == selectedTranscription,
                    onClick = { selectedTranscription = method },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = method.labelRes))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.image_style),
            style = MaterialTheme.typography.h6,
        )
        ImageStyle.values().forEach { style ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = style == selectedStyle,
                    onClick = { selectedStyle = style },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = style.labelRes))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                SettingsManager.setTranscriptionMethod(context, selectedTranscription)
                SettingsManager.setImageStyle(context, selectedStyle)
                onBack()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.save))
        }
    }
}
