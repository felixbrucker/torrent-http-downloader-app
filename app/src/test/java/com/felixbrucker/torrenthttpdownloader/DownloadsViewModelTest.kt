package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
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
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val rssRepository = mockk<RssRepository>(relaxed = true)
    private val providerFactory = mockk<ProviderFactory>(relaxed = true)
    private val serviceLauncher = mockk<DownloadServiceLauncher>(relaxed = true)

    @Before
    fun setUp() {
        every { downloadRepository.tasks } returns MutableStateFlow(emptyList())
        every { rssRepository.totalUnreadRssCount } returns MutableStateFlow(0)
    }

    @Test
    fun testGetProviderReturnsProvider() {
        val provider = mockk<TorrentProvider>()
        every { providerFactory.getProvider() } returns provider
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        val result = viewModel.getProvider()

        assertEquals(provider, result)
    }

    @Test
    fun testMoveTaskDelegatesToDownloadRepository() {
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        viewModel.moveTask(0, 1)

        verify { downloadRepository.moveTask(0, 1) }
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
