package com.felixbrucker.torrenthttpdownloader.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felixbrucker.torrenthttpdownloader.data.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.LogViewerViewModel
import com.felixbrucker.torrenthttpdownloader.util.Formatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LogViewerTopBar(
                onNavigateBack = onNavigateBack,
                onClearLogs = { viewModel.clearLogs() }
            )
        }
    ) { innerPadding ->
        LogContent(
            logs = logs,
            padding = innerPadding
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerTopBar(
    onNavigateBack: () -> Unit,
    onClearLogs: () -> Unit
) {
    TopAppBar(
        title = { Text("Log Viewer") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onClearLogs) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
            }
        }
    )
}

@Composable
private fun LogContent(
    logs: List<LogEntry>,
    padding: PaddingValues
) {
    if (logs.isEmpty()) {
        EmptyLogView(padding)
    } else {
        LogList(logs = logs, padding = padding)
    }
}

@Composable
private fun EmptyLogView(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text("No logs recorded", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LogList(
    logs: List<LogEntry>,
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(logs) { entry ->
            LogCard(entry)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LogHeader(entry)
            Spacer(modifier = Modifier.height(4.dp))
            LogMessageText(entry.message)
            entry.throwableStackTrace?.let { LogThrowableText(it) }
        }
    }
}

@Composable
private fun LogHeader(entry: LogEntry) {
    val label = entry.priorityLabel()
    val levelColor = when (label) {
        "ERROR" -> Color.Red
        "WARN" -> Color(0xFFFFA000)
        "INFO" -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "[${Formatter.formatTimestamp(entry.timestamp)}] [$label] ${entry.tag ?: ""}",
        color = levelColor,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun LogMessageText(message: String) {
    Text(
        text = message,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun LogThrowableText(throwable: String) {
    Text(
        text = throwable,
        fontSize = 12.sp,
        color = Color.Red,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 4.dp)
    )
}
