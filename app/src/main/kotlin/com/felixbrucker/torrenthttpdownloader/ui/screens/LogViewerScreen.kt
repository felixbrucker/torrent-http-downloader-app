package com.felixbrucker.torrenthttpdownloader.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.felixbrucker.torrenthttpdownloader.data.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.LogViewerViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val allLogs by viewModel.allLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableIntStateOf(-1) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredLogs by remember(allLogs, searchQuery, selectedPriority) {
        derivedStateOf {
            allLogs.filter { entry ->
                val matchesPriority = selectedPriority == -1 || entry.priority == selectedPriority
                val matchesQuery = searchQuery.isBlank() ||
                        (entry.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                        entry.message.contains(searchQuery, ignoreCase = true)
                matchesPriority && matchesQuery
            }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var isControlsVisible by remember { mutableStateOf(true) }
    var previousScrollIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex > previousScrollIndex) {
                    isControlsVisible = false
                } else if (currentIndex < previousScrollIndex) {
                    isControlsVisible = true
                } else {
                    if (currentOffset > previousScrollOffset + 10) {
                        isControlsVisible = false
                    } else if (currentOffset < previousScrollOffset - 10) {
                        isControlsVisible = true
                    }
                }
                previousScrollIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (allLogs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            LogViewerTopControls(
                isVisible = isControlsVisible,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedPriority = selectedPriority,
                onSelectPriority = { selectedPriority = it },
                filteredLogsCount = filteredLogs.size,
                totalLogsCount = allLogs.size,
                onJumpToBottom = {
                    if (filteredLogs.isNotEmpty()) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(filteredLogs.size - 1)
                        }
                    }
                }
            )

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (allLogs.isEmpty()) "No log entries recorded yet" else "No matching log entries found",
                            color = Color(0xFFCAC4D0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredLogs) { entry ->
                        LogEntryCard(entry = entry)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Logs", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all stored diagnostic logs? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogViewerTopControls(
    isVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedPriority: Int,
    onSelectPriority: (Int) -> Unit,
    filteredLogsCount: Int,
    totalLogsCount: Int,
    onJumpToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Filter logs by tag or text...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFCAC4D0)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFFCAC4D0))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD0BCFF),
                    unfocusedBorderColor = Color(0xFF49454F),
                    focusedContainerColor = Color(0xFF2B2930),
                    unfocusedContainerColor = Color(0xFF2B2930),
                    focusedTextColor = Color(0xFFE6E1E5),
                    unfocusedTextColor = Color(0xFFE6E1E5)
                )
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val priorities = listOf(
                    -1 to "All",
                    Log.VERBOSE to "Verbose",
                    Log.DEBUG to "Debug",
                    Log.INFO to "Info",
                    Log.WARN to "Warn",
                    Log.ERROR to "Error"
                )

                priorities.forEach { (priorityVal, label) ->
                    val isSelected = selectedPriority == priorityVal
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPriority(priorityVal) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD0BCFF),
                            selectedLabelColor = Color(0xFF381E72),
                            containerColor = Color(0xFF2B2930),
                            labelColor = Color(0xFFCAC4D0)
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing $filteredLogsCount of $totalLogsCount log entries",
                    color = Color(0xFFCAC4D0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (filteredLogsCount > 0) {
                    TextButton(
                        onClick = onJumpToBottom,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Jump to Bottom", fontSize = 11.sp, color = Color(0xFFD0BCFF))
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(entry: LogEntry) {
    val (badgeBg, badgeFg) = when (entry.priority) {
        Log.ERROR -> Color(0xFF601410) to Color(0xFFF2B8B5)
        Log.WARN -> Color(0xFF4A3800) to Color(0xFFFFDDB3)
        Log.INFO -> Color(0xFF00382B) to Color(0xFF7CE49F)
        Log.DEBUG -> Color(0xFF1D192B) to Color(0xFFD0BCFF)
        else -> Color(0xFF313033) to Color(0xFFCAC4D0)
    }

    val formattedTime = remember(entry.timestamp) {
        try {
            logTimeFormatter.format(Instant.ofEpochMilli(entry.timestamp))
        } catch (_: Exception) {
            entry.timestamp.toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2B2930),
        border = BorderStroke(1.dp, Color(0xFF3B383E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = entry.priorityLabel(),
                            color = badgeFg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = entry.tag ?: "TorrentHttpDownloader",
                        color = Color(0xFFE6E1E5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = formattedTime,
                    color = Color(0xFF938F99),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.message,
                color = Color(0xFFE6E1E5),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )

            if (!entry.throwableStackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = entry.throwableStackTrace,
                        color = Color(0xFFF2B8B5),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
