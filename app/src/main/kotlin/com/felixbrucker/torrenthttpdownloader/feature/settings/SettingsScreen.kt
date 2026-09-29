package com.felixbrucker.torrenthttpdownloader.feature.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val initialState = remember { viewModel.loadSettings() }

    var selectedProvider by remember { mutableStateOf(initialState.selectedProvider) }
    var realDebridApiToken by remember { mutableStateOf(initialState.realDebridApiToken) }
    var localParallelDownloads by remember { mutableStateOf(initialState.localParallelDownloads) }
    var libTorrentParallelDownloads by remember { mutableStateOf(initialState.libTorrentParallelDownloads) }
    var libTorrentRequireVpnConnection by remember { mutableStateOf(initialState.libTorrentRequireVpnConnection) }

    Scaffold(
        topBar = { SettingsTopBar(onBack = onBack) }
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
                ProviderSelectionDropdown(
                    providers = viewModel.availableProviders,
                    selectedProvider = selectedProvider,
                    onProviderSelected = { selectedProvider = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ProviderConfigSection(
                    selectedProvider = selectedProvider,
                    realDebridApiToken = realDebridApiToken,
                    onApiTokenChange = { realDebridApiToken = it },
                    localParallelDownloads = localParallelDownloads,
                    onLocalParallelDownloadsChange = { localParallelDownloads = it },
                    libTorrentParallelDownloads = libTorrentParallelDownloads,
                    onLibTorrentParallelDownloadsChange = { libTorrentParallelDownloads = it },
                    libTorrentRequireVpnConnection = libTorrentRequireVpnConnection,
                    onLibTorrentRequireVpnChange = { libTorrentRequireVpnConnection = it }
                )

                Button(onClick = {
                    viewModel.saveSettings(
                        selectedProvider = selectedProvider,
                        realDebridApiToken = realDebridApiToken,
                        localParallelDownloads = localParallelDownloads,
                        libTorrentParallelDownloads = libTorrentParallelDownloads,
                        libTorrentRequireVpnConnection = libTorrentRequireVpnConnection
                    )
                    onSave()
                    onBack()
                }) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(id = R.string.settings)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun ProviderConfigSection(
    selectedProvider: String,
    realDebridApiToken: String,
    onApiTokenChange: (String) -> Unit,
    localParallelDownloads: String,
    onLocalParallelDownloadsChange: (String) -> Unit,
    libTorrentParallelDownloads: String,
    onLibTorrentParallelDownloadsChange: (String) -> Unit,
    libTorrentRequireVpnConnection: Boolean,
    onLibTorrentRequireVpnChange: (Boolean) -> Unit
) {
    if (selectedProvider == RealDebridProvider.NAME) {
        RealDebridSettingsSection(
            apiToken = realDebridApiToken,
            onApiTokenChange = onApiTokenChange,
            parallelDownloads = localParallelDownloads,
            onParallelDownloadsChange = onLocalParallelDownloadsChange
        )
    } else if (selectedProvider == LibTorrentProvider.NAME) {
        LibTorrentSettingsSection(
            parallelDownloads = libTorrentParallelDownloads,
            onParallelDownloadsChange = onLibTorrentParallelDownloadsChange,
            requireVpn = libTorrentRequireVpnConnection,
            onRequireVpnChange = onLibTorrentRequireVpnChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSelectionDropdown(
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
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
                            onProviderSelected(provider)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RealDebridSettingsSection(
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    parallelDownloads: String,
    onParallelDownloadsChange: (String) -> Unit
) {
    Column {
        TextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = parallelDownloads,
            onValueChange = onParallelDownloadsChange,
            label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LibTorrentSettingsSection(
    parallelDownloads: String,
    onParallelDownloadsChange: (String) -> Unit,
    requireVpn: Boolean,
    onRequireVpnChange: (Boolean) -> Unit
) {
    Column {
        TextField(
            value = parallelDownloads,
            onValueChange = onParallelDownloadsChange,
            label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onRequireVpnChange(!requireVpn) }
        ) {
            Checkbox(
                checked = requireVpn,
                onCheckedChange = onRequireVpnChange
            )
            Text(text = stringResource(id = R.string.require_vpn_connection))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

