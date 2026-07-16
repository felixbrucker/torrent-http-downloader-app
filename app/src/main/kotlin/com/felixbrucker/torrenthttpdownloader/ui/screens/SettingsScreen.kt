package com.felixbrucker.torrenthttpdownloader.ui.screens

import android.app.backup.BackupManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.Formatter.Companion.formatBytes
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.providers.RealDebridProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    val providers = listOf(
        LibTorrentProvider.NAME,
        RealDebridProvider.NAME,
    )
    var selectedProvider by remember {
        mutableStateOf(sharedPreferences.getString("provider", providers[0]) ?: providers[0])
    }
    var realDebridApiToken by remember { mutableStateOf(sharedPreferences.getString("real_debrid_api_token", "") ?: "") }
    var localParallelDownloads by remember { mutableStateOf(sharedPreferences.getInt("local_parallel_downloads", 2).toString()) }
    var libTorrentParallelDownloads by remember { mutableStateOf(sharedPreferences.getInt("libtorrent_parallel_downloads", 3).toString()) }
    var libTorrentRequireVpnConnection by remember { mutableStateOf(sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false)) }

    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(id = R.string.provider), modifier = Modifier.padding(bottom = 8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvider,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider) },
                                onClick = {
                                    selectedProvider = provider
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedProvider == RealDebridProvider.NAME) {
                    TextField(
                        value = realDebridApiToken,
                        onValueChange = { realDebridApiToken = it },
                        label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = localParallelDownloads,
                        onValueChange = { localParallelDownloads = it },
                        label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (selectedProvider == LibTorrentProvider.NAME) {
                    TextField(
                        value = libTorrentParallelDownloads,
                        onValueChange = { libTorrentParallelDownloads = it },
                        label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { libTorrentRequireVpnConnection = !libTorrentRequireVpnConnection }
                    ) {
                        Checkbox(
                            checked = libTorrentRequireVpnConnection,
                            onCheckedChange = { newValue -> libTorrentRequireVpnConnection = newValue }
                        )
                        Text(text = stringResource(id = R.string.require_vpn_connection))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(onClick = {
                    sharedPreferences.edit {
                        putString("provider", selectedProvider)
                        putString("real_debrid_api_token", realDebridApiToken.trim())
                        putInt("local_parallel_downloads", localParallelDownloads.toIntOrNull() ?: 2)
                        putInt("libtorrent_parallel_downloads", libTorrentParallelDownloads.toIntOrNull() ?: 3)
                        putBoolean("libtorrent_require_vpn_connection", libTorrentRequireVpnConnection)
                    }
                    onSave()
                    BackupManager.dataChanged(context.packageName)
                    onBack()
                }) {
                    Text("Save")
                }
            }

            val lastBackupTime = sharedPreferences.getLong("last_backup_time", 0L)
            val lastBackupSize = sharedPreferences.getLong("last_backup_size", 0L)
            if (lastBackupTime > 0) {
                val timeStr = DateUtils.getRelativeTimeSpanString(
                    lastBackupTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
                val sizeStr = formatBytes(lastBackupSize)
                Text(
                    text = stringResource(id = R.string.last_backup, timeStr, sizeStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp)
                )
            }
        }
    }
}
