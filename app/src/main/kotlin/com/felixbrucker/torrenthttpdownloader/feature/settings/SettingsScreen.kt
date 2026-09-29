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
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import kotlin.math.roundToInt

class SettingsState(initialState: SettingsUiState) {
    var selectedProvider by mutableStateOf(initialState.selectedProvider)
    var realDebridApiToken by mutableStateOf(initialState.realDebridApiToken)
    var localParallelDownloads by mutableStateOf(initialState.localParallelDownloads)
    var libTorrentParallelDownloads by mutableStateOf(initialState.libTorrentParallelDownloads)
    var libTorrentRequireVpnConnection by mutableStateOf(initialState.libTorrentRequireVpnConnection)
    var rssSyncEnabled by mutableStateOf(initialState.rssSyncEnabled)
    var rssSyncIntervalHours by mutableIntStateOf(initialState.rssSyncIntervalHours)
}

@Composable
fun rememberSettingsState(initialState: SettingsUiState): SettingsState {
    return remember { SettingsState(initialState) }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state = rememberSettingsState(remember { viewModel.loadSettings() })
    val onSaveAction = {
        saveSettingsState(viewModel, state)
        onSave()
        onBack()
    }
    SettingsContent(state = state, providers = viewModel.availableProviders, onBackClick = onBack, onSaveClick = onSaveAction)
}

private fun saveSettingsState(viewModel: SettingsViewModel, state: SettingsState) {
    viewModel.saveSettings(
        selectedProvider = state.selectedProvider,
        realDebridApiToken = state.realDebridApiToken,
        localParallelDownloads = state.localParallelDownloads,
        libTorrentParallelDownloads = state.libTorrentParallelDownloads,
        libTorrentRequireVpnConnection = state.libTorrentRequireVpnConnection,
        rssSyncEnabled = state.rssSyncEnabled,
        rssSyncIntervalHours = state.rssSyncIntervalHours
    )
}

@Composable
fun SettingsContent(
    state: SettingsState,
    providers: List<String>,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Scaffold(
        topBar = { SettingsTopBar(onBackClick = onBackClick) }
    ) { padding ->
        SettingsScrollableBody(modifier = Modifier.padding(padding), state = state, providers = providers, onSaveClick = onSaveClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(id = R.string.settings)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun SettingsScrollableBody(
    modifier: Modifier = Modifier,
    state: SettingsState,
    providers: List<String>,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProviderConfigCard(state = state, providers = providers)
        RssSyncConfigCard(state = state)
        SaveButton(onSaveClick = onSaveClick)
    }
}

@Composable
private fun ProviderConfigCard(
    state: SettingsState,
    providers: List<String>
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.provider), style = MaterialTheme.typography.titleMedium)
            ProviderSelectionDropdown(
                providers = providers,
                selectedProvider = state.selectedProvider,
                onProviderSelected = { state.selectedProvider = it }
            )
            ProviderSpecificSettings(state = state)
        }
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
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        ProviderTextField(selectedProvider = selectedProvider, expanded = expanded, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            providers.forEach { provider ->
                DropdownMenuItem(text = { Text(provider) }, onClick = { onProviderSelected(provider); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTextField(selectedProvider: String, expanded: Boolean, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = selectedProvider, onValueChange = {}, readOnly = true,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = modifier
    )
}

@Composable
private fun ProviderSpecificSettings(state: SettingsState) {
    if (state.selectedProvider == RealDebridProvider.NAME) {
        RealDebridSettingsSection(state = state)
    } else if (state.selectedProvider == LibTorrentProvider.NAME) {
        LibTorrentSettingsSection(state = state)
    }
}

@Composable
private fun RealDebridSettingsSection(state: SettingsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RealDebridTokenField(token = state.realDebridApiToken, onTokenChange = { state.realDebridApiToken = it })
        ParallelDownloadsField(value = state.localParallelDownloads, onValueChange = { state.localParallelDownloads = it })
    }
}

@Composable
private fun RealDebridTokenField(token: String, onTokenChange: (String) -> Unit) {
    OutlinedTextField(
        value = token, onValueChange = onTokenChange,
        label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ParallelDownloadsField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LibTorrentSettingsSection(state: SettingsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ParallelDownloadsField(value = state.libTorrentParallelDownloads, onValueChange = { state.libTorrentParallelDownloads = it })
        RequireVpnRow(checked = state.libTorrentRequireVpnConnection, onCheckedChange = { state.libTorrentRequireVpnConnection = it })
    }
}

@Composable
private fun RequireVpnRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(id = R.string.require_vpn_connection))
    }
}

@Composable
private fun RssSyncConfigCard(state: SettingsState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.rss_sync_settings), style = MaterialTheme.typography.titleMedium)
            RssSyncEnableRow(enabled = state.rssSyncEnabled, onEnabledChange = { state.rssSyncEnabled = it })
            RssSyncIntervalSection(enabled = state.rssSyncEnabled, intervalHours = state.rssSyncIntervalHours, onIntervalChange = { state.rssSyncIntervalHours = it })
        }
    }
}

@Composable
private fun RssSyncEnableRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onEnabledChange(!enabled) }
    ) {
        Text(text = stringResource(id = R.string.enable_rss_sync), modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun RssSyncIntervalSection(enabled: Boolean, intervalHours: Int, onIntervalChange: (Int) -> Unit) {
    if (enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(id = R.string.rss_sync_interval_hours, intervalHours), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = intervalHours.toFloat(),
                onValueChange = { onIntervalChange(it.roundToInt()) },
                valueRange = 1f..24f,
                steps = 22
            )
        }
    }
}

@Composable
private fun SaveButton(onSaveClick: () -> Unit) {
    Button(
        onClick = onSaveClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(id = R.string.save))
    }
}
