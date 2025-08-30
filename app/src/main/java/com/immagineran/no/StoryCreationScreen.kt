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
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StoryCreationScreen(initialSegments: List<File> = emptyList(), onDone: (List<File>, List<String?>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    val segments = remember { mutableStateListOf<File>().apply { addAll(initialSegments) } }
    val transcriptions = remember { mutableStateListOf<String?>().apply { repeat(initialSegments.size) { add(null) } } }
    var currentIndex by remember { mutableStateOf(-1) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val method = remember { SettingsManager.getTranscriptionMethod(context) }
    val transcriber = remember { TranscriberFactory.create(context, method) }

    LaunchedEffect(Unit) {
        initialSegments.forEachIndexed { idx, file ->
            transcribeSegment(context, file, idx, transcriptions, transcriber, scope)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording(
                segments,
                transcriptions,
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
                transcribeSegment(context, segments[currentIndex], currentIndex, transcriptions, transcriber, scope)
                isRecording = false
                currentIndex = -1
            } else {
                val permission = Manifest.permission.RECORD_AUDIO
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    startRecording(
                        segments,
                        transcriptions,
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
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
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
                                transcriptions,
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
                    val t = transcriptions.getOrNull(index)
                    Text(
                        text = if (t != null) {
                            stringResource(R.string.transcription_label, t)
                        } else {
                            stringResource(R.string.transcribing)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        Button(
            onClick = { onDone(segments.toList(), transcriptions.toList()) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

private fun transcribeSegment(
    context: android.content.Context,
    file: File,
    index: Int,
    transcriptions: MutableList<String?>,
    transcriber: Transcriber,
    scope: kotlinx.coroutines.CoroutineScope
) {
    transcriptions[index] = null
    scope.launch {
        val result = transcriber.transcribe(file)
            ?: context.getString(R.string.transcription_failed)
        transcriptions[index] = result
    }
}

private fun startRecording(
    segments: MutableList<File>,
    transcriptions: MutableList<String?>,
    context: android.content.Context,
    index: Int,
    onRecorder: (MediaRecorder) -> Unit,
    onStart: () -> Unit,
    onIndex: (Int) -> Unit = {},
) {
    val file = File(context.filesDir, "segment_${'$'}{System.currentTimeMillis()}.m4a")
    val actualIndex = if (index >= 0 && index < segments.size) {
        segments[index].delete()
        segments[index] = file
        index
    } else {
        segments.add(file)
        segments.lastIndex
    }
    if (actualIndex >= transcriptions.size) {
        transcriptions.add(null)
    } else {
        transcriptions[actualIndex] = null
    }
    onIndex(actualIndex)
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

