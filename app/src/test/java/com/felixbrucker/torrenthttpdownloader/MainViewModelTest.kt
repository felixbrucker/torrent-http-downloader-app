package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MainViewModelTest {
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)

    @Test
    fun testCheckAndStartDownloadServiceStartsWhenTasksNeedProcessing() {
        val context = mockk<Context>(relaxed = true)
        every { downloadRepository.hasTasksWhichNeedProcessing() } returns true
        val viewModel = MainViewModel(downloadRepository)

        viewModel.checkAndStartDownloadService(context)

        verify { context.startService(any()) }
    }
}
