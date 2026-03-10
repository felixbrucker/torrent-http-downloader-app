package com.felixbrucker.torrenthttpdownloader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.ui.theme.TorrentHttpDownloaderTheme

class MainActivity : ComponentActivity() {
    private var pendingConfig by mutableStateOf<TorrentPendingConfig?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            TorrentHttpDownloaderTheme {
                val context = LocalContext.current
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasNotificationPermission = isGranted
                    }
                )

                LaunchedEffect(Unit) {
                    if (!hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                MainScreen(
                    pendingConfig = pendingConfig,
                    onConfigDismiss = { pendingConfig = null },
                    onConfigConfirm = { config, subDir, createFolder, torrentName ->
                        val intent = Intent(this, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_ADD_TASK
                            putExtra(DownloadService.EXTRA_TORRENT_PATH, config.path)
                            putExtra(DownloadService.EXTRA_TORRENT_TYPE, config.type.name)
                            putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, subDir)
                            putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, createFolder)
                            putExtra(DownloadService.EXTRA_TORRENT_NAME, torrentName)
                        }
                        startService(intent)
                        pendingConfig = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        if (DownloadTracker.hasTasksWhichNeedProcessing()) {
            startService(Intent(this, DownloadService::class.java))
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            val path = data.toString()
            val type = if (data.scheme == "magnet") TorrentType.MAGNET else TorrentType.TORRENT
            pendingConfig = TorrentPendingConfig(path, type)
        }
    }
}

data class TorrentPendingConfig(val path: String, val type: TorrentType)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pendingConfig: TorrentPendingConfig?,
    onConfigDismiss: () -> Unit,
    onConfigConfirm: (TorrentPendingConfig, String?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val tasks by DownloadTracker.tasks.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    val anyDownloading = tasks.any { task -> task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING } }
    val anyPaused = tasks.any { task -> task.files.any { it.state == LocalDownloadState.PAUSED } }

    fun removeTask(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_REMOVE_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    if (anyDownloading) {
                        IconButton(onClick = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_PAUSE_ALL
                            }
                            context.startService(intent)
                        }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause All")
                        }
                    }
                    if (anyPaused) {
                        IconButton(onClick = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_RESUME_ALL
                            }
                            context.startService(intent)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
                        }
                    }

                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.action_settings)) },
                            onClick = {
                                showMenu = false
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        )
                    }
                }
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(tasks, key = { task -> task.id }) { task ->
                DownloadItem(task = task, onRemove = { removeTask(task.id) })
            }
        }

        pendingConfig?.let { config ->
            AddTorrentBottomSheet(
                config = config,
                onDismiss = onConfigDismiss,
                onConfirm = { subDir, createFolder, torrentName ->
                    onConfigConfirm(config, subDir, createFolder, torrentName)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTorrentBottomSheet(
    config: TorrentPendingConfig,
    onDismiss: () -> Unit,
    onConfirm: (String?, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSubDir by remember { mutableStateOf(sharedPreferences.getString("default_sub_dir", null)) }
    var createSubfolderByName by remember { mutableStateOf(sharedPreferences.getBoolean("default_create_subfolder", true)) }
    var subDirectories by remember { mutableStateOf<List<String>>(emptyList()) }
    val torrentName = remember(config) { TorrentUtils.tryToGetNameFromUri(context.contentResolver, config.path, config.type) }

    val commonIgnoredRootDirectories = setOf(
        "Adobe Acrobat",
        "Musicolet",
        "tmp",
        "update",
    )

    LaunchedEffect(Unit) {
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
    }

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
            if (torrentName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = torrentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                            selectedSubDir = if (selectedSubDir == dir) null else dir
                        },
                        label = { Text(dir) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { createSubfolderByName = !createSubfolderByName }
            ) {
                Checkbox(
                    checked = createSubfolderByName,
                    onCheckedChange = { createSubfolderByName = it }
                )
                Text(text = stringResource(id = R.string.create_subfolder))
            }

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
                    sharedPreferences.edit {
                        putString("default_sub_dir", selectedSubDir)
                        putBoolean("default_create_subfolder", createSubfolderByName)
                    }
                    onConfirm(selectedSubDir, createSubfolderByName, torrentName)
                }) {
                    Text(stringResource(id = R.string.add))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
