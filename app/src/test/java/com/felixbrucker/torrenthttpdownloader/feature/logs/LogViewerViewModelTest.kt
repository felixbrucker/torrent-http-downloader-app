package com.felixbrucker.torrenthttpdownloader.feature.logs

import android.util.Log
import com.felixbrucker.torrenthttpdownloader.core.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.core.logging.LogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private val repository: LogRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LogViewerViewModel.defaultDispatcher = testDispatcher
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        LogViewerViewModel.defaultDispatcher = Dispatchers.Default
    }

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
    fun testFilteringBySearchQueryAndPriority() {
        val entry1 = LogEntry(priority = Log.INFO, tag = "Network", message = "Request started")
        val entry2 = LogEntry(priority = Log.ERROR, tag = "Database", message = "Failed to query")
        val logsFlow = MutableStateFlow(listOf(entry1, entry2))
        every { repository.logsFlow } returns logsFlow

        val viewModel = LogViewerViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.filteredLogs.value.size)

        viewModel.setSearchQuery("Network")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(entry1), viewModel.filteredLogs.value)

        viewModel.setSearchQuery("")
        viewModel.setSelectedPriority(Log.ERROR)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(entry2), viewModel.filteredLogs.value)
    }

    @Test
    fun testClearLogsCallsRepositoryClearLogs() {
        val viewModel = LogViewerViewModel(repository)

        viewModel.clearLogs()

        verify { repository.clearLogs() }
    }
}
