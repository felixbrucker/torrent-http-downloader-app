package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.Intent
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadsViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>()
    private val providerFactory = mockk<ProviderFactory>()

    @Before
    fun setUp() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setAction(any()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns mockk(relaxed = true)
    }

    @Test
    fun testGetProviderReturnsProvider() {
        val provider = mockk<TorrentProvider>()
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
        every { providerFactory.getProvider() } returns provider
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory)

        val result = viewModel.getProvider()

        assertEquals(provider, result)
    }

    @Test
    fun testMoveTaskDelegatesToDownloadTracker() {
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
        every { downloadTracker.moveTask(0, 1) } returns Unit
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory)

        viewModel.moveTask(0, 1)

        verify { downloadTracker.moveTask(0, 1) }
    }

    @Test
    fun testRemoveTaskStartsServiceWithIntent() {
        val context = mockk<Context>(relaxed = true)
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
        every { context.startService(any()) } returns null
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory)

        viewModel.removeTask(context, "task-1", deleteFiles = true, deleteTorrentFile = false)

        verify { context.startService(any()) }
    }

    @Test
    fun testResumeAllStartsServiceIntents() {
        val context = mockk<Context>(relaxed = true)
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
        every { context.startService(any()) } returns null
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory)

        viewModel.resumeAll(context)

        verify(exactly = 2) { context.startService(any()) }
    }

    @Test
    fun testPauseAllStartsServiceIntents() {
        val context = mockk<Context>(relaxed = true)
        every { downloadTracker.tasks } returns MutableStateFlow(emptyList())
        every { downloadTracker.totalUnreadRssCount } returns MutableStateFlow(0)
        every { context.startService(any()) } returns null
        val viewModel = DownloadsViewModel(downloadTracker, providerFactory)

        viewModel.pauseAll(context)

        verify(exactly = 2) { context.startService(any()) }
    }
}
