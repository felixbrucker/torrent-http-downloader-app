package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MainViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)

    @Test
    fun testCheckAndStartDownloadServiceStartsWhenTasksNeedProcessing() {
        val context = mockk<Context>(relaxed = true)
        every { downloadTracker.hasTasksWhichNeedProcessing() } returns true
        val viewModel = MainViewModel(downloadTracker, rssSyncLauncher)

        viewModel.checkAndStartDownloadService(context)

        verify { context.startService(any()) }
    }

    @Test
    fun testEnsureRssSyncIsScheduledDelegatesToLauncher() {
        val viewModel = MainViewModel(downloadTracker, rssSyncLauncher)

        viewModel.ensureRssSyncIsScheduled()

        verify { rssSyncLauncher.ensureRssSyncIsScheduled() }
    }
}
