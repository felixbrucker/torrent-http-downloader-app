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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.felixbrucker.torrenthttpdownloader.INVALID_CHARACTERS_FOR_PATH
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.listSubdirectoriesAsRelativeStrings
import com.felixbrucker.torrenthttpdownloader.countItemsRecursively
import com.felixbrucker.torrenthttpdownloader.totalSizeBytesRecursively
import com.felixbrucker.torrenthttpdownloader.Formatter
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import java.io.File

data class DirectoryItemInfo(
    val relativePath: String,
    val itemCount: Int,
    val totalSize: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTorrentConfigFields(
    selectedSubDir: String?,
    createSubfolderByName: Boolean,
    notifyOnCompletion: Boolean,
    fileSelectionMode: FileSelectionMode,
    onSubdirectorySelected: (String?) -> Unit,
    onCreateSubfolderByNameChanged: (Boolean) -> Unit,
    onNotifyOnCompletionChanged: (Boolean) -> Unit,
    onFileSelectionModeChanged: (FileSelectionMode) -> Unit,
    suggestedSubDirectoryName: String? = null,
) {
    var subDirectories by remember { mutableStateOf<List<DirectoryItemInfo>>(emptyList()) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var directoryToDelete by remember { mutableStateOf<String?>(null) }
    var isEditingDirectories by remember { mutableStateOf(false) }

    fun loadSubDirectories() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val relativePaths = downloadsDir.listSubdirectoriesAsRelativeStrings(
            excludeRootDirectories = true,
            depth = 2,
        )

        // Reset to download directory if previously selected directory does not exist anymore
        if (selectedSubDir != null && !relativePaths.contains(selectedSubDir)) {
            onSubdirectorySelected(null)
        }

        subDirectories = relativePaths.map { relativePath ->
            val dir = File(downloadsDir, relativePath)
            DirectoryItemInfo(
                relativePath = relativePath,
                itemCount = dir.countItemsRecursively(),
                totalSize = dir.totalSizeBytesRecursively()
            )
        }
    }

    LifecycleResumeEffect(Unit) {
        loadSubDirectories()

        onPauseOrDispose {
            // Nothing to do
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.select_destination),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { isEditingDirectories = !isEditingDirectories }) {
            Icon(
                imageVector = if (isEditingDirectories) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = if (isEditingDirectories) "Done" else "Edit",
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (subDirectories.isEmpty() && !isEditingDirectories) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(id = R.string.no_subdirectories_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subDirectories.forEach { dirInfo ->
            FilterChip(
                selected = selectedSubDir == dirInfo.relativePath,
                onClick = {
                    onSubdirectorySelected(if (selectedSubDir == dirInfo.relativePath) null else dirInfo.relativePath)
                },
                label = {
                    Column {
                        Text(dirInfo.relativePath)
                        if (isEditingDirectories) {
                            Text(
                                text = "${dirInfo.itemCount} item${if (dirInfo.itemCount == 1) "" else "s"}, ${Formatter.formatBytes(dirInfo.totalSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                trailingIcon = if (isEditingDirectories) {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            modifier = Modifier
                                .size(FilterChipDefaults.IconSize)
                                .clickable {
                                    directoryToDelete = dirInfo.relativePath
                                }
                        )
                    }
                } else null
            )
        }
        if (isEditingDirectories) {
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

    if (directoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { directoryToDelete = null },
            title = { Text("Delete Subdirectory") },
            text = { Text("Are you sure you want to delete '$directoryToDelete' and all its contents?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val dirFile = File(downloadsDir, directoryToDelete!!)
                        if (dirFile.exists()) {
                            dirFile.deleteRecursively()
                            if (selectedSubDir == directoryToDelete) {
                                onSubdirectorySelected(null)
                            }
                            loadSubDirectories()
                        }
                        directoryToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { directoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "File Selection Mode",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    FileSelectionMode.entries.forEach { mode ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFileSelectionModeChanged(mode) }
        ) {
            RadioButton(
                selected = fileSelectionMode == mode,
                onClick = { onFileSelectionModeChanged(mode) }
            )
            val text = when (mode) {
                FileSelectionMode.ALL -> "Download all files"
                FileSelectionMode.BIGGEST -> "Only download the biggest file"
                FileSelectionMode.MANUAL -> "Manual selection"
            }
            Text(text = text)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

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