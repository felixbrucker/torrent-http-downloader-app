package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadsViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)
    private val providerFactory = mockk<ProviderFactory>(relaxed = true)
    private val serviceLauncher = mockk<DownloadServiceLauncher>(relaxed = true)

    @Before
    fun setUp() {
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
    }

    @Test
    fun testGetProviderReturnsProvider() {
        val provider = mockk<TorrentProvider>()
        every { providerFactory.getProvider() } returns provider
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory, serviceLauncher)

        val result = viewModel.getProvider()

        assertEquals(provider, result)
    }

    @Test
    fun testMoveTaskDelegatesToDownloadTracker() {
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory, serviceLauncher)

        viewModel.moveTask(0, 1)

        verify { downloadTracker.moveTask(0, 1) }
    }

    @Test
    fun testRemoveTaskDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory, serviceLauncher)

        viewModel.removeTask("task-1", deleteFiles = true, deleteTorrentFile = false)

        verify { serviceLauncher.removeTask("task-1", true, false) }
    }

    @Test
    fun testResumeAllLocalDownloadsDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory, serviceLauncher)

        viewModel.resumeAllLocalDownloads()

        verify { serviceLauncher.resumeAllLocalDownloads() }
    }

    @Test
    fun testPauseAllLocalDownloadsDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory, serviceLauncher)

        viewModel.pauseAllLocalDownloads()

        verify { serviceLauncher.pauseAllLocalDownloads() }
    }
}
