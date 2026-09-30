package com.felixbrucker.torrenthttpdownloader.feature.logs

import com.felixbrucker.torrenthttpdownloader.core.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.core.logging.LogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class LogViewerViewModelTest {

    private val repository: LogRepository = mockk(relaxed = true)

    @Test
    fun testAllLogsExposesRepositoryLogsFlow() {
        val testLogs = listOf(LogEntry(message = "Sample log"))
        val logsFlow = MutableStateFlow(testLogs)
        every { repository.logsFlow } returns logsFlow

        val viewModel = LogViewerViewModel(repository)
        val result = viewModel.allLogs.value

        assertEquals(testLogs, result)
    }

    @Test
    fun testClearLogsCallsRepositoryClearLogs() {
        val viewModel = LogViewerViewModel(repository)

        viewModel.clearLogs()

        verify { repository.clearLogs() }
    }
}
