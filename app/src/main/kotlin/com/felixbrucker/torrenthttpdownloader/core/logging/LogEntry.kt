package com.felixbrucker.torrenthttpdownloader.core.logging

import android.util.Log
import java.util.UUID

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = Log.DEBUG,
    val tag: String? = null,
    val message: String = "",
    val throwableStackTrace: String? = null,
    val id: String = UUID.randomUUID().toString()
) {
    fun priorityLabel(): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        else -> "LOG"
    }
}
