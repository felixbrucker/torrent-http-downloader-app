package com.felixbrucker.torrenthttpdownloader.core.logging

import android.util.Log
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogTree @Inject constructor(
    private val logRepository: LogRepository
) : Timber.DebugTree() {

    public override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Do not restrict log levels for production
        return true
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val stackTrace = t?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag ?: "TorrentHttpDownloader",
            message = message,
            throwableStackTrace = stackTrace
        )
        logRepository.addLog(entry)

        super.log(priority, tag, message, t)
    }
}
