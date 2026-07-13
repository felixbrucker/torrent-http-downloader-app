package com.felixbrucker.torrenthttpdownloader.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.felixbrucker.torrenthttpdownloader.INVALID_CHARACTERS_FOR_PATH
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.listSubdirectoriesAsRelativeStrings
import java.io.File

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
    suggestedSubDirectoryName: String? = null,
) {
    var subDirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    fun loadSubDirectories() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        subDirectories = downloadsDir.listSubdirectoriesAsRelativeStrings(
            excludeRootDirectories = true,
            depth = 2,
        )
    }

    LifecycleResumeEffect(Unit) {
        loadSubDirectories()

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
        FilterChip(
            selected = false,
            onClick = { showCreateFolderDialog = true },
            label = { Icon(Icons.Default.Add, contentDescription = null) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            suggestedSelectedParentDir = selectedSubDir,
            suggestedDirectoryName = suggestedSubDirectoryName,
            onDismiss = { showCreateFolderDialog = false },
            onFolderCreated = { newPath ->
                loadSubDirectories()
                onSubdirectorySelected(newPath)
                onCreateSubfolderByNameChanged(false)
                showCreateFolderDialog = false
            }
        )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFolderDialog(
    suggestedSelectedParentDir: String?,
    suggestedDirectoryName: String?,
    onDismiss: () -> Unit,
    onFolderCreated: (String) -> Unit
) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val preselectedParentDir = if (suggestedSelectedParentDir?.contains("/") == true) {
        suggestedSelectedParentDir.substringAfterLast("/")
    } else {
        suggestedSelectedParentDir
    }
    var selectedParentDir by remember { mutableStateOf(preselectedParentDir) }
    var folderName by remember { mutableStateOf(suggestedDirectoryName ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val subDirectories = remember {
        downloadsDir.listSubdirectoriesAsRelativeStrings(
            excludeRootDirectories = true,
            depth = 1
        )
    }

    fun validateName() {
        val trimmedName = folderName.trim()
        if (trimmedName.isEmpty()) {
            errorMessage = "Name cannot be empty"
            return
        }
        if (trimmedName.length > 127) {
            errorMessage = "Name too long (max 127 characters)"
            return
        }
        INVALID_CHARACTERS_FOR_PATH.forEach { invalidChar ->
            if (trimmedName.contains(invalidChar)) {
                errorMessage = "Invalid characters"
                return
            }
        }
        val relativePath = if (selectedParentDir != null) {
            "$selectedParentDir/$trimmedName"
        } else {
            trimmedName
        }

        val newDir = File(downloadsDir, relativePath)
        if (newDir.exists()) {
            errorMessage = "Folder already exists"
            return
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create new Subdirectory") },
        text = {
            Column {
                if (subDirectories.isNotEmpty()) {
                    Text(
                        text = "Select Parent (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        subDirectories.forEach { dir ->
                            FilterChip(
                                selected = selectedParentDir == dir,
                                onClick = {
                                    selectedParentDir = if (selectedParentDir == dir) null else dir
                                },
                                label = { Text(dir) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorMessage = null
                        validateName()
                    },
                    label = { Text("Folder Name") },
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage!!) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = errorMessage == null,
                onClick = {
                    val trimmedName = folderName.trim()
                    val relativePath = if (selectedParentDir != null) {
                        "$selectedParentDir/$trimmedName"
                    } else {
                        trimmedName
                    }

                    val newDir = File(downloadsDir, relativePath)
                    try {
                        if (newDir.mkdirs()) {
                            onFolderCreated(relativePath)
                        } else {
                            errorMessage = "Failed to create folder"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}