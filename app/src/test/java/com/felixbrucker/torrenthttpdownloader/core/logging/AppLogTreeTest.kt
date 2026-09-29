package com.felixbrucker.torrenthttpdownloader.core.logging

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class AppLogTreeTest {

    private val repository: LogRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.getStackTraceString(any()) } returns ""
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testIsLoggableReturnsTrue() {
        val appLogTree = AppLogTree(repository)

        val result = appLogTree.isLoggable("Tag", Log.VERBOSE)

        assertTrue(result)
    }

    @Test
    fun testLogCallsRepositoryAddLog() {
        val appLogTree = AppLogTree(repository)
        Timber.plant(appLogTree)
        val entrySlot = slot<LogEntry>()

        Timber.i("Test Message")

        verify { repository.addLog(capture(entrySlot)) }
        assertEquals("Test Message", entrySlot.captured.message)
        assertEquals(Log.INFO, entrySlot.captured.priority)
        Timber.uproot(appLogTree)
    }
}
