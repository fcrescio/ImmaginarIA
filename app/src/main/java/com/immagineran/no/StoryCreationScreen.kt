package com.immagineran.no

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun StoryCreationScreen(initialSegments: List<File> = emptyList(), onDone: (List<File>) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    val segments = remember { mutableStateListOf<File>().apply { addAll(initialSegments) } }
    var currentIndex by remember { mutableStateOf(-1) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording(
                segments,
                context,
                currentIndex,
                onRecorder = { recorder = it },
                onStart = { isRecording = true },
                onIndex = { currentIndex = it }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = {
            if (isRecording) {
                stopRecording(recorder) { recorder = null }
                isRecording = false
                currentIndex = -1
            } else {
                val permission = Manifest.permission.RECORD_AUDIO
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    startRecording(
                        segments,
                        context,
                        currentIndex,
                        onRecorder = { recorder = it },
                        onStart = { isRecording = true },
                        onIndex = { currentIndex = it }
                    )
                } else {
                    permissionLauncher.launch(permission)
                }
            }
        }) {
            Text(text = if (isRecording) stringResource(R.string.stop) else stringResource(R.string.record))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(segments) { index, file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.segment_number, index + 1),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        playSegment(file, player) { player = it }
                    }) {
                        Text(stringResource(R.string.play))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        startRecording(
                            segments,
                            context,
                            index,
                            onRecorder = { recorder = it },
                            onStart = { isRecording = true },
                            onIndex = { currentIndex = it }
                        )
                    }) {
                        Text(stringResource(R.string.re_record))
                    }
                }
            }
        }

        Button(
            onClick = { onDone(segments.toList()) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

private fun startRecording(
    segments: MutableList<File>,
    context: android.content.Context,
    index: Int,
    onRecorder: (MediaRecorder) -> Unit,
    onStart: () -> Unit,
    onIndex: (Int) -> Unit = {}
) {
    val file = File(context.filesDir, "segment_${System.currentTimeMillis()}.m4a")
    if (index >= 0 && index < segments.size) {
        segments[index].delete()
        segments[index] = file
        onIndex(index)
    } else {
        segments.add(file)
        onIndex(segments.lastIndex)
    }
    val rec = MediaRecorder()
    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    rec.setOutputFile(file.absolutePath)
    rec.prepare()
    rec.start()
    onRecorder(rec)
    onStart()
}

private fun stopRecording(recorder: MediaRecorder?, onRelease: () -> Unit) {
    recorder?.run {
        stop()
        release()
    }
    onRelease()
}

private fun playSegment(file: File, currentPlayer: MediaPlayer?, onPlayer: (MediaPlayer) -> Unit) {
    currentPlayer?.release()
    val mp = MediaPlayer()
    mp.setDataSource(file.absolutePath)
    mp.prepare()
    mp.start()
    onPlayer(mp)
}

