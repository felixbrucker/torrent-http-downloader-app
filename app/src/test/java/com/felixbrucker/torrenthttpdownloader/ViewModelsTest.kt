package com.felixbrucker.torrenthttpdownloader

import android.net.Uri
import com.felixbrucker.torrenthttpdownloader.data.logging.LogRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.models.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.network.ResolvedTorrent
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.AddTorrentViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.DownloadsViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.LogViewerViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.RssFeedDetailViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.RssFeedsViewModel
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

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
    fun testDownloadsViewModelTasksAndRemove() = runTest {
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val task = DownloadTask(
            id = "t1",
            name = "Task 1",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=1")
        )
        every { downloadRepository.tasksFlow } returns flowOf(listOf(task))

        val viewModel = DownloadsViewModel(downloadRepository)
        val initialTasks = viewModel.tasks.first()
        viewModel.removeTask("t1")

        assertEquals(1, initialTasks.size)
        assertEquals("t1", initialTasks.first().id)
        coVerify { downloadRepository.deleteTask("t1") }
    }

    @Test
    fun testRssFeedsViewModelFeedsAddAndRemove() = runTest {
        val rssRepository = mockk<RssRepository>(relaxed = true)
        val feed = RssFeed(id = "f1", name = "Feed 1", url = "http://feed1.com")
        every { rssRepository.feedsFlow } returns flowOf(listOf(feed))

        val viewModel = RssFeedsViewModel(rssRepository)
        val initialFeeds = viewModel.feeds.first()
        viewModel.addFeed(feed)
        viewModel.removeFeed("f1")

        assertEquals(1, initialFeeds.size)
        assertEquals("f1", initialFeeds.first().id)
        coVerify { rssRepository.insertFeed(feed) }
        coVerify { rssRepository.deleteFeed("f1") }
    }

    @Test
    fun testRssFeedDetailViewModelLoadAndMarkRead() = runTest {
        val rssRepository = mockk<RssRepository>(relaxed = true)
        val item = RssItem(id = "i1", title = "Item 1", link = "http://link.com", isRead = false)
        val feed = RssFeed(id = "f1", name = "Feed 1", url = "http://feed1.com", items = listOf(item))
        coEvery { rssRepository.getFeedById("f1") } returns feed

        val viewModel = RssFeedDetailViewModel(rssRepository)
        viewModel.loadFeed("f1")
        val loadedFeed = viewModel.feed.first()
        viewModel.markItemRead("i1", true)

        assertEquals("f1", loadedFeed?.id)
        coVerify { rssRepository.updateItemState("i1", isRead = true, isDownloaded = false) }
    }

    @Test
    fun testAddTorrentViewModelResolveAndAdd() = runTest {
        val uriResolver = mockk<TorrentUriResolver>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val mockUri = mockk<Uri>(relaxed = true)
        val resolved = ResolvedTorrent(
            type = TorrentType.MAGNET,
            uri = mockUri,
            id = "t1",
            name = "Resolved Torrent"
        )
        coEvery { uriResolver.resolve(mockUri) } returns resolved

        val viewModel = AddTorrentViewModel(uriResolver, downloadRepository)
        viewModel.resolveUri(mockUri)
        val resolvedState = viewModel.resolvedTorrent.first()
        viewModel.clearResolvedTorrent()
        val clearedState = viewModel.resolvedTorrent.first()

        assertEquals("t1", resolvedState?.id)
        assertEquals("Resolved Torrent", resolvedState?.name)
        assertNull(clearedState)
    }

    @Test
    fun testSettingsViewModel() = runTest {
        val appSettingsRepo = mockk<AppSettingsRepository>(relaxed = true)
        every { appSettingsRepo.preferencesFlow } returns flowOf(AppSettingsPreferences())

        val viewModel = SettingsViewModel(appSettingsRepo)
        val prefs = viewModel.settings.first()

        viewModel.setSelectedProvider("libtorrent")
        viewModel.setRealDebridApiKey("key123")
        viewModel.setDefaultDestinationSubdirectory("Sub")
        viewModel.setRssCheckIntervalHours(2)
        viewModel.setNotifyOnCompletion(false)
        viewModel.setFileSelectionMode(FileSelectionMode.BIGGEST)
        viewModel.setAutoExtractArchives(false)
        viewModel.setDeleteArchivesAfterExtraction(true)

        assertEquals("real_debrid", prefs.selectedProvider)
        coVerify { appSettingsRepo.setSelectedProvider("libtorrent") }
        coVerify { appSettingsRepo.setRealDebridApiKey("key123") }
        coVerify { appSettingsRepo.setDefaultDestinationSubdirectory("Sub") }
        coVerify { appSettingsRepo.setRssCheckIntervalHours(2) }
        coVerify { appSettingsRepo.setNotifyOnCompletion(false) }
        coVerify { appSettingsRepo.setFileSelectionMode(FileSelectionMode.BIGGEST) }
        coVerify { appSettingsRepo.setAutoExtractArchives(false) }
        coVerify { appSettingsRepo.setDeleteArchivesAfterExtraction(true) }
    }

    @Test
    fun testLogViewerViewModel() = runTest {
        LogRepository.clearLogs()
        val viewModel = LogViewerViewModel()
        viewModel.clearLogs()
        val logs = viewModel.allLogs.value

        assertEquals(0, logs.size)
    }
}
