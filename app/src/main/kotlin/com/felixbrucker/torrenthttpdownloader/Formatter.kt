package com.felixbrucker.torrenthttpdownloader

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class Formatter {
    companion object {
        const val SECONDS_PER_DAY = 3600 * 24
        fun formatSpeed(bytesPerSecond: Long): String {
            return "${formatBytes(bytesPerSecond)}/s"
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
            val decimals = if (digitGroups >= 2) 2 else 0

            return String.format(
                Locale.US,
                "%.${decimals}f %s",
                bytes / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }

        fun formatTime(seconds: Long): String {
            val days = seconds / SECONDS_PER_DAY
            val hours = (seconds % SECONDS_PER_DAY) / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return when {
                days > 0 -> String.format(Locale.US, "%dd %dh %dm %ds", days, hours, minutes, secs)
                hours > 0 -> String.format(Locale.US, "%dh %dm %ds", hours, minutes, secs)
                minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, secs)
                else -> String.format(Locale.US, "%ds", secs)
            }
        }
    }
}