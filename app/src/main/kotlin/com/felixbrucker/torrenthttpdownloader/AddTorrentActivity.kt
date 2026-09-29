package com.felixbrucker.torrenthttpdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import com.felixbrucker.torrenthttpdownloader.core.designsystem.theme.TorrentHttpDownloaderTheme
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentContent
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentViewModel
import timber.log.Timber

@AndroidEntryPoint
class AddTorrentActivity : ComponentActivity() {
    private val viewModel: AddTorrentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("AddTorrentActivity created")

        handleIntent(intent)

        setContent {
            TorrentHttpDownloaderTheme {
                val isResolvingTorrent by viewModel.isResolvingTorrent.collectAsState()

                AddTorrentContent(
                    onFinish = { finish() },
                    viewModel = viewModel
                )

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
            viewModel.resolveTorrentUri(
                uri = data,
                onFailure = { finish() }
            )
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("AddTorrentActivity destroyed")
    }
}
