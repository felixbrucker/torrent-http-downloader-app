package com.felixbrucker.torrenthttpdownloader.core.logging

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val maxEntries = 3000
    private val pruneThreshold = 3500
    private val maxAgeMs = 7L * 24 * 60 * 60 * 1000 // 7 days

    private val gson = Gson()

    private val mutex = Mutex()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logFile: File? = null
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    var initJob: Job? = null

    init {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        val file = File(logDir, "app_logs.jsonl")
        logFile = file

        initJob = CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                loadInitialLogsLocked()
            }
        }
    }

    internal fun loadInitialLogsLocked() {
        val file = logFile ?: return
        val loadedLogs = mutableListOf<LogEntry>()
        if (file.exists()) {
            try {
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.isNotBlank()) {
                            try {
                                val entry = gson.fromJson(line, LogEntry::class.java)
                                if (entry != null) {
                                    loadedLogs.add(entry)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val now = System.currentTimeMillis()
        val pruned = pruneEntries(loadedLogs, now, maxAgeMs, maxEntries)
        _logsFlow.value = pruned
        saveAllLocked(pruned)
    }

    fun addLog(entry: LogEntry) {
        CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                val current = _logsFlow.value.toMutableList()
                current.add(entry)

                val file = logFile
                if (file != null) {
                    try {
                        val jsonLine = gson.toJson(entry)
                        file.appendText(jsonLine + "\n")
                    } catch (_: Exception) {
                    }
                }

                if (current.size > pruneThreshold) {
                    val now = System.currentTimeMillis()
                    val pruned = pruneEntries(current, now, maxAgeMs, maxEntries)
                    _logsFlow.value = pruned
                    saveAllLocked(pruned)
                } else {
                    _logsFlow.value = current
                }
            }
        }
    }

    fun clearLogs() {
        CoroutineScope(ioDispatcher).launch {
            mutex.withLock {
                _logsFlow.value = emptyList()
                val file = logFile
                if (file != null && file.exists()) {
                    try {
                        file.writeText("")
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    internal fun pruneEntries(
        entries: List<LogEntry>,
        nowMs: Long,
        maxAgeMs: Long,
        maxEntries: Int
    ): List<LogEntry> {
        val cutoff = nowMs - maxAgeMs
        var startIndex = entries.indexOfFirst { it.timestamp >= cutoff }
        if (startIndex == -1) return emptyList()
        if (entries.size - startIndex > maxEntries) {
            startIndex = entries.size - maxEntries
        }
        return entries.subList(startIndex, entries.size).toList()
    }

    private fun saveAllLocked(entries: List<LogEntry>) {
        val file = logFile ?: return
        try {
            file.bufferedWriter().use { writer ->
                for (entry in entries) {
                    writer.write(gson.toJson(entry))
                    writer.newLine()
                }
            }
        } catch (_: Exception) {
        }
    }
}
