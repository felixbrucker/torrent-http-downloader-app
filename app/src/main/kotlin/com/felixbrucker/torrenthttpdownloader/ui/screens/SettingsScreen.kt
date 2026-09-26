package com.felixbrucker.torrenthttpdownloader.ui.screens

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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.data.preferences.TorrentProviderType
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    onViewLogs: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SettingsTopBar(onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SettingsFormContent(
                prefs = prefs,
                viewModel = viewModel,
                onSave = {
                    onSave()
                    onBack()
                },
                modifier = Modifier.weight(1f)
            )
            ViewLogsButton(onViewLogs)
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
private fun SettingsFormContent(
    prefs: com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences,
    viewModel: SettingsViewModel,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        ProviderSelectionSection(prefs.selectedProvider) { viewModel.setSelectedProvider(it) }
        Spacer(modifier = Modifier.height(16.dp))
        ProviderSpecificSettingsSection(prefs, viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}

@Composable
private fun ProviderSelectionSection(
    selectedProvider: TorrentProviderType,
    onSelect: (TorrentProviderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(text = stringResource(id = R.string.provider), modifier = Modifier.padding(bottom = 8.dp))
    ProviderDropdownMenu(
        selectedProvider = selectedProvider,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onSelect = {
            onSelect(it)
            expanded = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdownMenu(
    selectedProvider: TorrentProviderType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (TorrentProviderType) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedProvider.name,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            TorrentProviderType.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.name) },
                    onClick = { onSelect(provider) }
                )
            }
        }
    }
}

@Composable
private fun ProviderSpecificSettingsSection(
    prefs: com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences,
    viewModel: SettingsViewModel
) {
    when (prefs.selectedProvider) {
        TorrentProviderType.REAL_DEBRID -> RealDebridSettings(prefs, viewModel)
        TorrentProviderType.LIBTORRENT -> LibTorrentSettings(prefs, viewModel)
    }
}

@Composable
private fun RealDebridSettings(
    prefs: com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences,
    viewModel: SettingsViewModel
) {
    Column {
        TextField(
            value = prefs.realDebridApiToken,
            onValueChange = { viewModel.setRealDebridApiToken(it) },
            label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = prefs.localParallelDownloads.toString(),
            onValueChange = { viewModel.setLocalParallelDownloads(it.toIntOrNull() ?: 2) },
            label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LibTorrentSettings(
    prefs: com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences,
    viewModel: SettingsViewModel
) {
    Column {
        TextField(
            value = prefs.libTorrentParallelDownloads.toString(),
            onValueChange = { viewModel.setLibTorrentParallelDownloads(it.toIntOrNull() ?: 3) },
            label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        VpnCheckboxRow(
            checked = prefs.libTorrentRequireVpnConnection,
            onCheckedChange = { viewModel.setLibTorrentRequireVpnConnection(it) }
        )
    }
}

@Composable
private fun VpnCheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = stringResource(id = R.string.require_vpn_connection))
    }
}

@Composable
private fun ViewLogsButton(onViewLogs: () -> Unit) {
    OutlinedButton(
        onClick = onViewLogs,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("View Logs")
    }
}
