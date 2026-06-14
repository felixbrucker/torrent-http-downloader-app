package com.felixbrucker.torrenthttpdownloader.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.TorrentType

data class AddTorrentConfig(
    val uri: String,
    val type: TorrentType,
    val name: String? = null,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean? = null,
    val notifyOnCompletion: Boolean? = null,
    val onlyDownloadBiggestFile: Boolean? = null,
    val feedId: String? = null,
    val feedItemId: String? = null,
)

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
    var onlyDownloadBiggestFile by remember { mutableStateOf(config.onlyDownloadBiggestFile ?: sharedPreferences.getBoolean("default_only_download_biggest_file", false)) }

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
            if (config.name != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            AddTorrentConfigFields(
                selectedSubDir = selectedSubDir,
                createSubfolderByName = createSubfolderByName,
                notifyOnCompletion = notifyOnCompletion,
                onlyDownloadBiggestFile = onlyDownloadBiggestFile,
                onSubdirectorySelected = { selectedSubDir = it },
                onCreateSubfolderByNameChanged = { createSubfolderByName = it },
                onNotifyOnCompletionChanged = { notifyOnCompletion = it },
                onOnlyDownloadBiggestFileChanged = { onlyDownloadBiggestFile = it },
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
                            putBoolean("default_only_download_biggest_file", onlyDownloadBiggestFile)
                        }
                    }
                    onConfirm(config.copy(
                        destinationSubdirectory = selectedSubDir,
                        createSubfolderByName = createSubfolderByName,
                        notifyOnCompletion = notifyOnCompletion,
                        onlyDownloadBiggestFile = onlyDownloadBiggestFile,
                    ))
                }) {
                    Text(stringResource(id = R.string.add))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTorrentConfigFields(
    selectedSubDir: String?,
    createSubfolderByName: Boolean,
    notifyOnCompletion: Boolean,
    onlyDownloadBiggestFile: Boolean,
    onSubdirectorySelected: (String?) -> Unit,
    onCreateSubfolderByNameChanged: (Boolean) -> Unit,
    onNotifyOnCompletionChanged: (Boolean) -> Unit,
    onOnlyDownloadBiggestFileChanged: (Boolean) -> Unit,
) {
    var subDirectories by remember { mutableStateOf<List<String>>(emptyList()) }

    val commonIgnoredRootDirectories = setOf(
        "Adobe Acrobat",
        "Musicolet",
        "tmp",
        "update",
    )

    LifecycleResumeEffect(Unit) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val subDirs = downloadsDir.listFiles { file ->
            file.isDirectory
                    && !file.name.startsWith(".")
                    && !commonIgnoredRootDirectories.contains(file.name)
        }
        val subDirsWithSubSubDirsStrings = subDirs
            ?.flatMap { subDir ->
                listOf<String>(subDir.name).plus(
                    subDir
                        .listFiles { it.isDirectory && !it.name.startsWith(".") }
                        ?.map { "${subDir.name}/${it.name}" }
                        ?: emptyList()
                )
            } ?: emptyList()

        subDirectories = subDirsWithSubSubDirsStrings.sorted()

        onPauseOrDispose {
            // Nothing to do
        }
    }

    Text(
        text = stringResource(id = R.string.select_destination),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subDirectories.forEach { dir ->
            FilterChip(
                selected = selectedSubDir == dir,
                onClick = {
                    onSubdirectorySelected(if (selectedSubDir == dir) null else dir)
                },
                label = { Text(dir) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCreateSubfolderByNameChanged(!createSubfolderByName) }
    ) {
        Checkbox(
            checked = createSubfolderByName,
            onCheckedChange = { onCreateSubfolderByNameChanged(it) }
        )
        Text(text = stringResource(id = R.string.create_subfolder))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onOnlyDownloadBiggestFileChanged(!onlyDownloadBiggestFile) }
    ) {
        Checkbox(
            checked = onlyDownloadBiggestFile,
            onCheckedChange = { onOnlyDownloadBiggestFileChanged(it) }
        )
        Text(text = stringResource(id = R.string.only_download_biggest_file))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onNotifyOnCompletionChanged(!notifyOnCompletion) }
    ) {
        Checkbox(
            checked = notifyOnCompletion,
            onCheckedChange = { onNotifyOnCompletionChanged(it) }
        )
        Text(text = stringResource(id = R.string.notify_on_completion))
    }
}