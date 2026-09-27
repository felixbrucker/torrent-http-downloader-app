package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MainViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)

    @Test
    fun testCheckAndStartDownloadServiceStartsWhenTasksNeedProcessing() {
        val context = mockk<Context>(relaxed = true)
        every { downloadTracker.hasTasksWhichNeedProcessing() } returns true
        val viewModel = MainViewModel(downloadTracker)

        viewModel.checkAndStartDownloadService(context)

        verify { context.startService(any()) }
    }
}
