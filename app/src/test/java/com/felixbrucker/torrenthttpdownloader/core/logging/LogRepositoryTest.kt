package com.felixbrucker.torrenthttpdownloader.core.logging

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LogRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk()

    @Before
    fun setUp() {
        every { context.filesDir } returns tempFolder.root
    }

    @Test
    fun testInitializationLoadsAndPrunesLogs() = runTest(testDispatcher) {
        val logDir = File(tempFolder.root, "logs").apply { mkdirs() }
        val file = File(logDir, "app_logs.jsonl")
        val now = System.currentTimeMillis()
        file.writeText("{\"timestamp\":$now,\"priority\":3,\"tag\":\"Test\",\"message\":\"Msg 1\"}\n")

        val repository = LogRepository(context)
        val logs = repository.logsFlow.value

        assertTrue(logs.isNotEmpty())
        assertEquals("Msg 1", logs.first().message)
    }

    @Test
    fun testAddLogAppendsEntryAndUpdatesFlow() = runTest(testDispatcher) {
        val repository = LogRepository(context)
        repository.scope = this
        val entry = LogEntry(timestamp = System.currentTimeMillis(), message = "Test Log")

        repository.addLog(entry)
        testScheduler.advanceUntilIdle()
        val logs = repository.logsFlow.value

        assertEquals(1, logs.size)
        assertEquals("Test Log", logs.first().message)
    }

    @Test
    fun testClearLogsEmptiesFlowAndFile() = runTest(testDispatcher) {
        val repository = LogRepository(context)
        repository.scope = this
        repository.addLog(LogEntry(message = "Log to clear"))
        testScheduler.advanceUntilIdle()

        repository.clearLogs()
        testScheduler.advanceUntilIdle()
        val logs = repository.logsFlow.value

        assertTrue(logs.isEmpty())
    }

    @Test
    fun testPruneEntriesFiltersByAgeAndMaxEntries() {
        val repository = LogRepository(context)
        val now = 100000L
        val oldEntry = LogEntry(timestamp = 1000L, message = "Old")
        val newEntry1 = LogEntry(timestamp = 90000L, message = "New 1")
        val newEntry2 = LogEntry(timestamp = 95000L, message = "New 2")
        val entries = listOf(oldEntry, newEntry1, newEntry2)

        val pruned = repository.pruneEntries(entries, nowMs = now, maxAgeMs = 50000L, maxEntries = 1)

        assertEquals(1, pruned.size)
        assertEquals("New 2", pruned.first().message)
    }
}
