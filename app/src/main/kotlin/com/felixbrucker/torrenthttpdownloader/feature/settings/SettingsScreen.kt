package com.felixbrucker.torrenthttpdownloader.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val initialState = remember { viewModel.loadSettings() }

    var selectedProvider by remember { mutableStateOf(initialState.selectedProvider) }
    var realDebridApiToken by remember { mutableStateOf(initialState.realDebridApiToken) }
    var localParallelDownloads by remember { mutableStateOf(initialState.localParallelDownloads) }
    var libTorrentParallelDownloads by remember { mutableStateOf(initialState.libTorrentParallelDownloads) }
    var libTorrentRequireVpnConnection by remember { mutableStateOf(initialState.libTorrentRequireVpnConnection) }
    var rssSyncEnabled by remember { mutableStateOf(initialState.rssSyncEnabled) }
    var rssSyncIntervalHours by remember { mutableIntStateOf(initialState.rssSyncIntervalHours) }

    fun autoSave(
        provider: String = selectedProvider,
        token: String = realDebridApiToken,
        localParallel: String = localParallelDownloads,
        libParallel: String = libTorrentParallelDownloads,
        vpn: Boolean = libTorrentRequireVpnConnection,
        rssEnabled: Boolean = rssSyncEnabled,
        rssInterval: Int = rssSyncIntervalHours
    ) {
        viewModel.saveSettings(
            selectedProvider = provider,
            realDebridApiToken = token,
            localParallelDownloads = localParallel,
            libTorrentParallelDownloads = libParallel,
            libTorrentRequireVpnConnection = vpn,
            rssSyncEnabled = rssEnabled,
            rssSyncIntervalHours = rssInterval
        )
        onSave()
    }

    Scaffold(
        topBar = { SettingsTopBar(onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProviderConfigCard(
                providers = viewModel.availableProviders,
                selectedProvider = selectedProvider,
                onProviderSelected = { provider ->
                    selectedProvider = provider
                    autoSave(provider = provider)
                },
                realDebridApiToken = realDebridApiToken,
                onRealDebridApiTokenChange = { token ->
                    realDebridApiToken = token
                    autoSave(token = token)
                },
                localParallelDownloads = localParallelDownloads,
                onLocalParallelDownloadsChange = { localParallel ->
                    val filtered = localParallel.filter { it.isDigit() }
                    localParallelDownloads = filtered
                    autoSave(localParallel = filtered)
                },
                libTorrentParallelDownloads = libTorrentParallelDownloads,
                onLibTorrentParallelDownloadsChange = { libParallel ->
                    val filtered = libParallel.filter { it.isDigit() }
                    libTorrentParallelDownloads = filtered
                    autoSave(libParallel = filtered)
                },
                libTorrentRequireVpnConnection = libTorrentRequireVpnConnection,
                onLibTorrentRequireVpnChange = { vpn ->
                    libTorrentRequireVpnConnection = vpn
                    autoSave(vpn = vpn)
                }
            )

            RssSyncConfigCard(
                rssSyncEnabled = rssSyncEnabled,
                onRssSyncEnabledChange = { enabled ->
                    rssSyncEnabled = enabled
                    autoSave(rssEnabled = enabled)
                },
                rssSyncIntervalHours = rssSyncIntervalHours,
                onRssSyncIntervalHoursChange = { interval ->
                    rssSyncIntervalHours = interval
                    autoSave(rssInterval = interval)
                }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderConfigCard(
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit,
    realDebridApiToken: String,
    onRealDebridApiTokenChange: (String) -> Unit,
    localParallelDownloads: String,
    onLocalParallelDownloadsChange: (String) -> Unit,
    libTorrentParallelDownloads: String,
    onLibTorrentParallelDownloadsChange: (String) -> Unit,
    libTorrentRequireVpnConnection: Boolean,
    onLibTorrentRequireVpnChange: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.provider),
                style = MaterialTheme.typography.titleMedium
            )

            ProviderDropdown(
                providers = providers,
                selectedProvider = selectedProvider,
                onProviderSelected = onProviderSelected
            )

            if (selectedProvider == RealDebridProvider.NAME) {
                OutlinedTextField(
                    value = realDebridApiToken,
                    onValueChange = onRealDebridApiTokenChange,
                    label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = localParallelDownloads,
                    onValueChange = onLocalParallelDownloadsChange,
                    label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (selectedProvider == LibTorrentProvider.NAME) {
                OutlinedTextField(
                    value = libTorrentParallelDownloads,
                    onValueChange = onLibTorrentParallelDownloadsChange,
                    label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLibTorrentRequireVpnChange(!libTorrentRequireVpnConnection) }
                ) {
                    Checkbox(
                        checked = libTorrentRequireVpnConnection,
                        onCheckedChange = onLibTorrentRequireVpnChange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.require_vpn_connection))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    providers: List<String>,
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
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

@Composable
private fun RssSyncConfigCard(
    rssSyncEnabled: Boolean,
    onRssSyncEnabledChange: (Boolean) -> Unit,
    rssSyncIntervalHours: Int,
    onRssSyncIntervalHoursChange: (Int) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.rss_sync_settings),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRssSyncEnabledChange(!rssSyncEnabled) }
            ) {
                Text(
                    text = stringResource(id = R.string.enable_rss_sync),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = rssSyncEnabled,
                    onCheckedChange = onRssSyncEnabledChange
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.rss_sync_interval_hours, rssSyncIntervalHours),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rssSyncEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Slider(
                    value = rssSyncIntervalHours.toFloat(),
                    onValueChange = { onRssSyncIntervalHoursChange(it.roundToInt()) },
                    valueRange = 1f..24f,
                    steps = 22,
                    enabled = rssSyncEnabled
                )
            }
        }
    }
}
