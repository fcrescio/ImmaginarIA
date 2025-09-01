package com.immagineran.no

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

/**
 * Screen presenting advanced options and utilities.
 */
@Composable
fun AdvancedOptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var generateImages by remember { mutableStateOf(SettingsManager.isAssetImageGenerationEnabled(context)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(text = stringResource(R.string.advanced), style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.generate_asset_images),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = generateImages, onCheckedChange = { generateImages = it })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val logs = LlmLogger.getLogFile(context)
                if (!logs.exists()) logs.createNewFile()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    logs,
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(
                        sendIntent,
                        context.getString(R.string.share_llm_logs),
                    ),
                )
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.share_llm_logs))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { LlmLogger.clear(context) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.clear_llm_logs))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { throw RuntimeException("Test Crash") },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.test_crash))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                SettingsManager.setAssetImageGenerationEnabled(context, generateImages)
                onBack()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = stringResource(R.string.save))
        }
    }
}
