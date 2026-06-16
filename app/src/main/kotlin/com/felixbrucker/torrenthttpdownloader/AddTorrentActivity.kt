package com.felixbrucker.torrenthttpdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentBottomSheet
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentConfig
import com.felixbrucker.torrenthttpdownloader.ui.theme.TorrentHttpDownloaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class AddTorrentActivity : ComponentActivity() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var pendingConfig by mutableStateOf<AddTorrentConfig?>(null)
    private var isResolvingTorrent by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            TorrentHttpDownloaderTheme {
                pendingConfig?.let { config ->
                    AddTorrentBottomSheet(
                        config = config,
                        onDismiss = {
                            pendingConfig = null
                            finish()
                        },
                        onConfirm = { updatedConfig ->
                            val intent = Intent(this, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_ADD_TASK
                                putExtra(DownloadService.EXTRA_TORRENT_URI, updatedConfig.uri)
                                putExtra(DownloadService.EXTRA_TORRENT_TYPE, updatedConfig.type.name)
                                putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, updatedConfig.destinationSubdirectory)
                                putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, updatedConfig.createSubfolderByName)
                                putExtra(DownloadService.EXTRA_NOTIFY_ON_COMPLETION, updatedConfig.notifyOnCompletion)
                                putExtra(DownloadService.EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE, updatedConfig.onlyDownloadBiggestFile)
                                putExtra(DownloadService.EXTRA_TORRENT_NAME, updatedConfig.name)
                            }
                            startService(intent)
                            pendingConfig = null
                            finish()
                        }
                    )
                }

                if (isResolvingTorrent) {
                    Dialog(onDismissRequest = { finish() }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                        val trimmedText = text.trim()
                        if (trimmedText.startsWith("magnet:") ||
                            trimmedText.startsWith("http://") ||
                            trimmedText.startsWith("https://")
                        ) {
                            trimmedText.toUri()
                        } else {
                            null
                        }
                    }
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                }
            }
            else -> null
        }

        if (data != null) {
            serviceScope.launch {
                isResolvingTorrent = true
                try {
                    val resolvedTorrent = TorrentUriResolver(contentResolver).resolve(data)
                    pendingConfig = AddTorrentConfig(
                        uri = resolvedTorrent.uri.toString(),
                        type = resolvedTorrent.type,
                        name = resolvedTorrent.name,
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    finish()
                } finally {
                    isResolvingTorrent = false
                }
            }
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
