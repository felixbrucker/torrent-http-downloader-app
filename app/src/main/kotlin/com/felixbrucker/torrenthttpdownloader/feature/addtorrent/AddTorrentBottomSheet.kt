package com.felixbrucker.torrenthttpdownloader.feature.addtorrent

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.extensions.asFile
import com.felixbrucker.torrenthttpdownloader.extensions.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.extensions.deleteIfExists

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

@Composable
fun AddTorrentContent(
    onFinish: () -> Unit = {},
    viewModel: AddTorrentViewModel = viewModel()
) {
    val pendingConfig by viewModel.resolvedConfig.collectAsState()
    val name by viewModel.nameState.collectAsState()
    val selectedSubDir by viewModel.selectedSubDirState.collectAsState()
    val createSubfolderByName by viewModel.createSubfolderByNameState.collectAsState()
    val notifyOnCompletion by viewModel.notifyOnCompletionState.collectAsState()
    val fileSelectionMode by viewModel.fileSelectionModeState.collectAsState()

    if (pendingConfig != null) {
        AddTorrentBottomSheet(
            name = name,
            selectedSubDir = selectedSubDir,
            createSubfolderByName = createSubfolderByName,
            notifyOnCompletion = notifyOnCompletion,
            fileSelectionMode = fileSelectionMode,
            onNameChange = { viewModel.updateName(it) },
            onSubdirectorySelected = { viewModel.updateSelectedSubDir(it) },
            onCreateSubfolderByNameChanged = { viewModel.updateCreateSubfolderByName(it) },
            onNotifyOnCompletionChanged = { viewModel.updateNotifyOnCompletion(it) },
            onFileSelectionModeChanged = { viewModel.updateFileSelectionMode(it) },
            onDismiss = {
                viewModel.dismissAddTorrent()
                onFinish()
            },
            onConfirm = {
                viewModel.confirmAddTorrent()
                onFinish()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTorrentBottomSheet(
    name: String,
    selectedSubDir: String?,
    createSubfolderByName: Boolean,
    notifyOnCompletion: Boolean,
    fileSelectionMode: FileSelectionMode,
    onNameChange: (String) -> Unit,
    onSubdirectorySelected: (String?) -> Unit,
    onCreateSubfolderByNameChanged: (Boolean) -> Unit,
    onNotifyOnCompletionChanged: (Boolean) -> Unit,
    onFileSelectionModeChanged: (FileSelectionMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            AddTorrentHeaderAndName(
                name = name,
                onNameChange = onNameChange
            )
            Spacer(modifier = Modifier.height(16.dp))

            AddTorrentConfigFields(
                selectedSubDir = selectedSubDir,
                createSubfolderByName = createSubfolderByName,
                notifyOnCompletion = notifyOnCompletion,
                fileSelectionMode = fileSelectionMode,
                onSubdirectorySelected = onSubdirectorySelected,
                onCreateSubfolderByNameChanged = onCreateSubfolderByNameChanged,
                onNotifyOnCompletionChanged = onNotifyOnCompletionChanged,
                onFileSelectionModeChanged = onFileSelectionModeChanged,
                suggestedSubDirectoryName = name.cleanedForUseAsPath()
            )

            Spacer(modifier = Modifier.height(24.dp))

            AddTorrentBottomButtons(
                onDismiss = onDismiss,
                onConfirm = onConfirm
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AddTorrentHeaderAndName(
    name: String,
    onNameChange: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(id = R.string.add_torrent),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.name)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

@Composable
private fun AddTorrentBottomButtons(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(id = R.string.cancel))
        }
        Button(onClick = onConfirm) {
            Text(stringResource(id = R.string.add))
        }
    }
}
