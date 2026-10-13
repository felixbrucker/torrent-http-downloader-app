package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCheckAndStartDownloadServiceStartsWhenTasksNeedProcessing() {
        val context = mockk<Context>(relaxed = true)
        coEvery { downloadRepository.hasTasksWhichNeedProcessing() } returns true
        val viewModel = MainViewModel(downloadRepository)

        viewModel.checkAndStartDownloadService(context)

        verify { context.startService(any()) }
    }
}
