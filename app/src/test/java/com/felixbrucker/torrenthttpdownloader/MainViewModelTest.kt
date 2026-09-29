package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MainViewModelTest {
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)

    @Test
    fun testCheckAndStartDownloadServiceStartsWhenTasksNeedProcessing() {
        val context = mockk<Context>(relaxed = true)
        every { downloadRepository.hasTasksWhichNeedProcessing() } returns true
        val viewModel = MainViewModel(downloadRepository, rssSyncLauncher)

        viewModel.checkAndStartDownloadService(context)

        verify { context.startService(any()) }
    }

    @Test
    fun testEnsureRssSyncIsScheduledDelegatesToLauncher() {
        val viewModel = MainViewModel(downloadRepository, rssSyncLauncher)

        viewModel.ensureRssSyncIsScheduled()

        verify { rssSyncLauncher.ensureRssSyncIsScheduled() }
    }
}
