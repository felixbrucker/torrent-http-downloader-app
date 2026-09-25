package com.felixbrucker.torrenthttpdownloader.data.logging

import android.util.Log

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = Log.DEBUG,
    val tag: String? = null,
    val message: String = "",
    val throwableStackTrace: String? = null
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
