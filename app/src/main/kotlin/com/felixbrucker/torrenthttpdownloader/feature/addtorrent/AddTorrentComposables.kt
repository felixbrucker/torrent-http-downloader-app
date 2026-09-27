package com.felixbrucker.torrenthttpdownloader.feature.addtorrent

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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.util.Formatter
import com.felixbrucker.torrenthttpdownloader.extensions.INVALID_CHARACTERS_FOR_PATH
import com.felixbrucker.torrenthttpdownloader.extensions.countItemsRecursively
import com.felixbrucker.torrenthttpdownloader.extensions.listSubdirectoriesAsRelativeStrings
import com.felixbrucker.torrenthttpdownloader.extensions.totalSizeBytesRecursively

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
        }
    }

    DestinationHeader(
        isEditingDirectories = isEditingDirectories,
        onToggleEditing = { isEditingDirectories = !isEditingDirectories }
    )

    if (subDirectories.isEmpty() && !isEditingDirectories) {
        EmptySubdirectoriesNotice()
    }

    SubdirectoryChipsSection(
        subDirectories = subDirectories,
        selectedSubDir = selectedSubDir,
        isEditingDirectories = isEditingDirectories,
        onSubdirectorySelected = onSubdirectorySelected,
        onDeleteDirectory = { directoryToDelete = it },
        onCreateFolderClick = { showCreateFolderDialog = true }
    )

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

    directoryToDelete?.let { dirPath ->
        DeleteSubdirectoryDialog(
            directoryPath = dirPath,
            onDismiss = { directoryToDelete = null },
            onConfirmDelete = {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dirFile = File(downloadsDir, dirPath)
                if (dirFile.exists()) {
                    dirFile.deleteRecursively()
                    if (selectedSubDir == dirPath) {
                        onSubdirectorySelected(null)
                    }
                    loadSubDirectories()
                }
                directoryToDelete = null
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    FileSelectionModeSection(
        fileSelectionMode = fileSelectionMode,
        onFileSelectionModeChanged = onFileSelectionModeChanged
    )

    Spacer(modifier = Modifier.height(8.dp))

    AddTorrentCheckboxOptions(
        createSubfolderByName = createSubfolderByName,
        onCreateSubfolderByNameChanged = onCreateSubfolderByNameChanged,
        notifyOnCompletion = notifyOnCompletion,
        onNotifyOnCompletionChanged = onNotifyOnCompletionChanged
    )
}

@Composable
private fun DestinationHeader(
    isEditingDirectories: Boolean,
    onToggleEditing: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(id = R.string.select_destination),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onToggleEditing) {
            Icon(
                imageVector = if (isEditingDirectories) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = if (isEditingDirectories) "Done" else "Edit",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EmptySubdirectoriesNotice() {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubdirectoryChipsSection(
    subDirectories: List<DirectoryItemInfo>,
    selectedSubDir: String?,
    isEditingDirectories: Boolean,
    onSubdirectorySelected: (String?) -> Unit,
    onDeleteDirectory: (String) -> Unit,
    onCreateFolderClick: () -> Unit
) {
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
                                    onDeleteDirectory(dirInfo.relativePath)
                                }
                        )
                    }
                } else null
            )
        }
        if (isEditingDirectories) {
            FilterChip(
                selected = false,
                onClick = onCreateFolderClick,
                label = { Icon(Icons.Default.Add, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun DeleteSubdirectoryDialog(
    directoryPath: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Subdirectory") },
        text = { Text("Are you sure you want to delete '$directoryPath' and all its contents?") },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FileSelectionModeSection(
    fileSelectionMode: FileSelectionMode,
    onFileSelectionModeChanged: (FileSelectionMode) -> Unit
) {
    Column {
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
    }
}

@Composable
private fun AddTorrentCheckboxOptions(
    createSubfolderByName: Boolean,
    onCreateSubfolderByNameChanged: (Boolean) -> Unit,
    notifyOnCompletion: Boolean,
    onNotifyOnCompletionChanged: (Boolean) -> Unit
) {
    Column {
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
                    ParentDirSelectionSection(
                        subDirectories = subDirectories,
                        selectedParentDir = selectedParentDir,
                        onParentDirSelected = { selectedParentDir = it }
                    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentDirSelectionSection(
    subDirectories: List<String>,
    selectedParentDir: String?,
    onParentDirSelected: (String?) -> Unit
) {
    Column {
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
                        onParentDirSelected(if (selectedParentDir == dir) null else dir)
                    },
                    label = { Text(dir) }
                )
            }
        }
    }
}
