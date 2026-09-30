package com.felixbrucker.torrenthttpdownloader.core.logging

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryTest {

    @Test
    fun testPriorityLabelReturnsCorrectStrings() {
        val verboseEntry = LogEntry(priority = Log.VERBOSE)
        val debugEntry = LogEntry(priority = Log.DEBUG)
        val infoEntry = LogEntry(priority = Log.INFO)
        val warnEntry = LogEntry(priority = Log.WARN)
        val errorEntry = LogEntry(priority = Log.ERROR)
        val otherEntry = LogEntry(priority = 99)

        assertEquals("VERBOSE", verboseEntry.priorityLabel())
        assertEquals("DEBUG", debugEntry.priorityLabel())
        assertEquals("INFO", infoEntry.priorityLabel())
        assertEquals("WARN", warnEntry.priorityLabel())
        assertEquals("ERROR", errorEntry.priorityLabel())
        assertEquals("LOG", otherEntry.priorityLabel())
    }
}
