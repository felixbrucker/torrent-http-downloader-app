package com.felixbrucker.torrenthttpdownloader.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                selectedProvider = uiState.selectedProvider,
                onProviderSelected = { provider ->
                    viewModel.updateSelectedProvider(provider)
                },
                realDebridApiToken = uiState.realDebridApiToken,
                onRealDebridApiTokenChange = { token ->
                    viewModel.updateRealDebridApiToken(token)
                },
                localParallelDownloads = uiState.localParallelDownloads,
                onLocalParallelDownloadsChange = { localParallel ->
                    viewModel.updateLocalParallelDownloads(localParallel)
                },
                libTorrentParallelDownloads = uiState.libTorrentParallelDownloads,
                onLibTorrentParallelDownloadsChange = { libParallel ->
                    viewModel.updateLibTorrentParallelDownloads(libParallel)
                },
                libTorrentRequireVpnConnection = uiState.libTorrentRequireVpnConnection,
                onLibTorrentRequireVpnChange = { vpn ->
                    viewModel.updateLibTorrentRequireVpnConnection(vpn)
                }
            )

            RssSyncConfigCard(
                rssSyncEnabled = uiState.rssSyncEnabled,
                onRssSyncEnabledChange = { enabled ->
                    viewModel.updateRssSyncEnabled(enabled)
                },
                rssSyncIntervalHours = uiState.rssSyncIntervalHours,
                onRssSyncIntervalHoursChange = { interval ->
                    viewModel.updateRssSyncIntervalHours(interval)
                }
            )

            LogsConfigCard(onNavigateToLogs = onNavigateToLogs)
        }
    }
}

@Composable
private fun LogsConfigCard(
    onNavigateToLogs: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.logs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = onNavigateToLogs,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A4458),
                    contentColor = Color(0xFFEADDFF)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("view_logs_button")
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.view_logs), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

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
                text = stringResource(id = R.string.provider_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            ProviderDropdown(
                providers = providers,
                selectedProvider = selectedProvider,
                onProviderSelected = onProviderSelected
            )

            if (selectedProvider == RealDebridProvider.NAME) {
                RealDebridSettings(
                    realDebridApiToken = realDebridApiToken,
                    onRealDebridApiTokenChange = onRealDebridApiTokenChange,
                    localParallelDownloads = localParallelDownloads,
                    onLocalParallelDownloadsChange = onLocalParallelDownloadsChange
                )
            } else if (selectedProvider == LibTorrentProvider.NAME) {
                LibTorrentSettings(
                    libTorrentParallelDownloads = libTorrentParallelDownloads,
                    onLibTorrentParallelDownloadsChange = onLibTorrentParallelDownloadsChange,
                    libTorrentRequireVpnConnection = libTorrentRequireVpnConnection,
                    onLibTorrentRequireVpnChange = onLibTorrentRequireVpnChange
                )
            }
        }
    }
}

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
            label = { Text(stringResource(id = R.string.provider)) },
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
    val sliderState = rememberSliderState(
        value = rssSyncIntervalHours.toFloat(),
        steps = 22,
        trackRange = 1f..24f
    )

    LaunchedEffect(rssSyncIntervalHours) {
        if (sliderState.value != rssSyncIntervalHours.toFloat()) {
            sliderState.value = rssSyncIntervalHours.toFloat()
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.rss_sync_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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
                    text = stringResource(id = R.string.rss_sync_interval_hours, sliderState.value.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rssSyncEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Slider(
                    state = sliderState,
                    onValueChange = { newValue ->
                        sliderState.value = newValue
                    },
                    onValueChangeFinished = {
                        onRssSyncIntervalHoursChange(sliderState.value.roundToInt())
                    },
                    enabled = rssSyncEnabled
                )
            }
        }
    }
}

@Composable
private fun RealDebridSettings(
    realDebridApiToken: String,
    onRealDebridApiTokenChange: (String) -> Unit,
    localParallelDownloads: String,
    onLocalParallelDownloadsChange: (String) -> Unit
) {
    RealDebridApiTokenField(
        realDebridApiToken = realDebridApiToken,
        onRealDebridApiTokenChange = onRealDebridApiTokenChange
    )
    ParallelDownloadsField(
        parallelDownloads = localParallelDownloads,
        onParallelDownloadsChange = onLocalParallelDownloadsChange
    )
}

@Composable
private fun LibTorrentSettings(
    libTorrentParallelDownloads: String,
    onLibTorrentParallelDownloadsChange: (String) -> Unit,
    libTorrentRequireVpnConnection: Boolean,
    onLibTorrentRequireVpnChange: (Boolean) -> Unit
) {
    ParallelDownloadsField(
        parallelDownloads = libTorrentParallelDownloads,
        onParallelDownloadsChange = onLibTorrentParallelDownloadsChange
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

@Composable
private fun RealDebridApiTokenField(
    realDebridApiToken: String,
    onRealDebridApiTokenChange: (String) -> Unit
) {
    val apiTokenState = rememberTextFieldState(realDebridApiToken)
    var passwordVisible by remember { mutableStateOf(false) }
    val currentOnTokenChange by rememberUpdatedState(onRealDebridApiTokenChange)
    val currentToken by rememberUpdatedState(realDebridApiToken)

    LaunchedEffect(apiTokenState) {
        snapshotFlow { apiTokenState.text.toString() }
            .collect { token ->
                if (token != currentToken) {
                    currentOnTokenChange(token)
                }
            }
    }
    OutlinedSecureTextField(
        state = apiTokenState,
        label = { Text(stringResource(id = R.string.real_debrid_api_token)) },
        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ParallelDownloadsField(
    parallelDownloads: String,
    onParallelDownloadsChange: (String) -> Unit
) {
    val state = rememberTextFieldState(parallelDownloads)
    val currentChange by rememberUpdatedState(onParallelDownloadsChange)
    val currentLimit by rememberUpdatedState(parallelDownloads)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collect { limit ->
                if (limit != currentLimit) {
                    currentChange(limit)
                }
            }
    }
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(id = R.string.parallel_downloads_limit)) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

