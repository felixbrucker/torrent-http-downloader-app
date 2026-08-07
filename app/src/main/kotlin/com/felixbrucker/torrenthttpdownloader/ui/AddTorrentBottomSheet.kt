package com.felixbrucker.torrenthttpdownloader.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.asFile
import com.felixbrucker.torrenthttpdownloader.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.deleteIfExists
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.TorrentType

data class AddTorrentConfig(
    val id: String,
    val uri: String,
    val type: TorrentType,
    val name: String? = null,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean? = null,
    val notifyOnCompletion: Boolean? = null,
    val fileSelectionMode: FileSelectionMode? = null,
    val feedId: String? = null,
    val feedItemId: String? = null,
) {
    fun cleanupTemporaryTorrentFile() {
        if (type != TorrentType.TORRENT_FILE || !uri.contains("tmp/torrents/")) {
            return
        }
        uri.asFile().deleteIfExists()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTorrentBottomSheet(
    config: AddTorrentConfig,
    onDismiss: () -> Unit,
    onConfirm: (AddTorrentConfig) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSubDir by remember { mutableStateOf(config.destinationSubdirectory ?: sharedPreferences.getString("default_sub_dir", null)) }
    var createSubfolderByName by remember { mutableStateOf(config.createSubfolderByName ?: sharedPreferences.getBoolean("default_create_subfolder", true)) }
    var notifyOnCompletion by remember { mutableStateOf(config.notifyOnCompletion ?: sharedPreferences.getBoolean("default_notify_on_completion", false)) }
    var fileSelectionMode by remember {
        mutableStateOf(
            config.fileSelectionMode ?: FileSelectionMode.valueOf(
                sharedPreferences.getString("default_file_selection_mode", FileSelectionMode.ALL.name)
                    ?: FileSelectionMode.ALL.name
            )
        )
    }
    var name by remember { mutableStateOf(config.name ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(id = R.string.add_torrent),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(modifier = Modifier.height(16.dp))

            AddTorrentConfigFields(
                selectedSubDir = selectedSubDir,
                createSubfolderByName = createSubfolderByName,
                notifyOnCompletion = notifyOnCompletion,
                fileSelectionMode = fileSelectionMode,
                onSubdirectorySelected = { selectedSubDir = it },
                onCreateSubfolderByNameChanged = { createSubfolderByName = it },
                onNotifyOnCompletionChanged = { notifyOnCompletion = it },
                onFileSelectionModeChanged = { fileSelectionMode = it },
                suggestedSubDirectoryName = name.cleanedForUseAsPath()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel))
                }
                Button(onClick = {
                    // Only persist last selection for regular adds
                    if (config.feedId == null) {
                        sharedPreferences.edit {
                            putString("default_sub_dir", selectedSubDir)
                            putBoolean("default_create_subfolder", createSubfolderByName)
                            putBoolean("default_notify_on_completion", notifyOnCompletion)
                            putString("default_file_selection_mode", fileSelectionMode.name)
                        }
                    }
                    onConfirm(config.copy(
                        name = name.takeIf { it.isNotBlank() },
                        destinationSubdirectory = selectedSubDir,
                        createSubfolderByName = createSubfolderByName,
                        notifyOnCompletion = notifyOnCompletion,
                        fileSelectionMode = fileSelectionMode,
                    ))
                }) {
                    Text(stringResource(id = R.string.add))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}