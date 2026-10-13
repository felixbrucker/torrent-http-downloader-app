package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val rssRepository = mockk<RssRepository>(relaxed = true)
    private val providerFactory = mockk<ProviderFactory>(relaxed = true)
    private val serviceLauncher = mockk<DownloadServiceLauncher>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { downloadRepository.tasks } returns MutableStateFlow(emptyList())
        every { rssRepository.totalUnreadRssCount } returns MutableStateFlow(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        coEvery { downloadRepository.moveTask(0, 1) } returns Unit
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        viewModel.moveTask(0, 1)

        coVerify { downloadRepository.moveTask(0, 1) }
    }

    @Test
    fun testRemoveTaskDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        viewModel.removeTask("task-1", deleteFiles = true, deleteTorrentFile = false)

        verify { serviceLauncher.removeTask("task-1", true, false) }
    }

    @Test
    fun testResumeAllLocalDownloadsDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        viewModel.resumeAllLocalDownloads()

        verify { serviceLauncher.resumeAllLocalDownloads() }
    }

    @Test
    fun testPauseAllLocalDownloadsDelegatesToServiceLauncher() {
        val viewModel = DownloadsViewModel(downloadRepository, rssRepository, providerFactory, serviceLauncher)

        viewModel.pauseAllLocalDownloads()

        verify { serviceLauncher.pauseAllLocalDownloads() }
    }
}
