package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.data.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.data.logging.LogRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.TorrentProviderType
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.AddTorrentViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.DownloadsViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.LogViewerViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.RssFeedDetailViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.RssFeedsViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val testDispatcher = StandardTestDispatcher()

    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val rssRepository = mockk<RssRepository>(relaxed = true)
    private val logRepository = mockk<LogRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSettingsViewModel() = runTest {
        coEvery { appSettingsRepository.preferencesFlow } returns flowOf(AppSettingsPreferences())

        val viewModel = SettingsViewModel(appSettingsRepository)
        viewModel.setSelectedProvider(TorrentProviderType.LIBTORRENT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepository.setSelectedProvider(TorrentProviderType.LIBTORRENT) }
    }

    @Test
    fun testDownloadsViewModel() = runTest {
        val task = DownloadTask(id = "1", name = "Test", torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"))
        coEvery { downloadRepository.tasksFlow } returns flowOf(listOf(task))

        val viewModel = DownloadsViewModel(downloadRepository)
        backgroundScope.launch { viewModel.tasks.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.tasks.value.size)
        assertEquals("Test", viewModel.tasks.value[0].name)
    }

    @Test
    fun testRssFeedsViewModel() = runTest {
        val feed = RssFeed(id = "f1", name = "Feed 1", url = "http://feed.com")
        coEvery { rssRepository.feedsFlow } returns flowOf(listOf(feed))

        val viewModel = RssFeedsViewModel(rssRepository)
        backgroundScope.launch { viewModel.feeds.collect {} }
        viewModel.addFeed(feed)
        viewModel.deleteFeed("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { rssRepository.insertFeed(feed) }
        coVerify { rssRepository.deleteFeed("f1") }
    }

    @Test
    fun testRssFeedDetailViewModel() = runTest {
        val feed = RssFeed(id = "f1", name = "Feed 1", url = "http://feed.com")
        coEvery { rssRepository.getFeedById("f1") } returns feed

        val viewModel = RssFeedDetailViewModel(rssRepository)
        viewModel.loadFeed("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Feed 1", viewModel.feed.value?.name)
    }

    @Test
    fun testLogViewerViewModel() = runTest {
        val log = LogEntry(message = "Log 1")
        coEvery { logRepository.logsFlow } returns MutableStateFlow(listOf(log))

        val viewModel = LogViewerViewModel(logRepository)
        viewModel.clearLogs()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { logRepository.clearLogs() }
    }

    @Test
    fun testAddTorrentViewModel() = runTest {
        coEvery { appSettingsRepository.preferencesFlow } returns flowOf(AppSettingsPreferences())

        val viewModel = AddTorrentViewModel(appSettingsRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TorrentProviderType.REAL_DEBRID, viewModel.preferences.value.selectedProvider)
    }
}
