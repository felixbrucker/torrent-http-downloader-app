package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.data.database.DownloadDao
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadTaskWithDetails
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentFileEntity
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentInfoEntity
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentInfoWithDetails
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentLinkEntity
import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedDao
import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedWithItems
import com.felixbrucker.torrenthttpdownloader.data.database.RssItemEntity
import com.felixbrucker.torrenthttpdownloader.data.database.TorrentDescriptorEntity
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepositoryTest {

    private lateinit var downloadDao: DownloadDao
    private lateinit var rssFeedDao: RssFeedDao
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var rssRepository: RssRepository

    @Before
    fun setUp() {
        downloadDao = mockk(relaxed = true)
        rssFeedDao = mockk(relaxed = true)
        downloadRepository = DownloadRepository(downloadDao)
        rssRepository = RssRepository(rssFeedDao)
    }

    @Test
    fun testDownloadRepositoryGetTasksFlow() = runTest {
        val taskEntity = DownloadTaskEntity(
            id = "t1",
            providerId = "p1",
            name = "Task 1",
            torrent = TorrentDescriptorEntity(TorrentType.MAGNET, "magnet:?xt=123"),
            state = TorrentState.DOWNLOADING_LOCALLY,
            errorMessage = null,
            destinationSubdirectory = null,
            createSubfolderByName = true,
            notifyOnCompletion = true,
            fileSelectionMode = FileSelectionMode.ALL,
            onCompletionIntentUri = null
        )
        val fileEntity = DownloadFileEntity(
            autoId = 1L,
            taskId = "t1",
            link = "http://link.com",
            unrestrictedLink = null,
            state = LocalDownloadState.DOWNLOADING,
            stateDescription = null,
            progress = 50,
            filePath = "/path",
            speed = 100,
            totalBytes = 200,
            downloadedBytes = 100
        )
        val providerInfoEntity = ProviderTorrentInfoEntity(
            id = "p1",
            taskId = "t1",
            name = "Task 1",
            state = ProviderTorrentState.DOWNLOADING,
            status = "ok",
            progress = 50.0f,
            totalSizeInBytes = 200,
            downloadedBytes = 100,
            downloadSpeed = 50,
            uploadSpeed = 0,
            seeders = 5,
            leechers = 1,
            peers = 6,
            totalPeers = 10
        )
        val providerFileEntity = ProviderTorrentFileEntity(
            autoId = 1L,
            providerInfoId = "p1",
            fileId = 1,
            path = "file.mkv",
            size = 200,
            isSelected = true,
            progress = 50.0f,
            downloadedBytes = 100,
            priority = FilePriority.NORMAL
        )
        val providerLinkEntity = ProviderTorrentLinkEntity(
            autoId = 1L,
            providerInfoId = "p1",
            link = "http://link.com"
        )

        val providerInfoWithDetails = ProviderTorrentInfoWithDetails(
            info = providerInfoEntity,
            files = listOf(providerFileEntity),
            links = listOf(providerLinkEntity)
        )

        val taskWithDetails = DownloadTaskWithDetails(
            task = taskEntity,
            providerInfo = providerInfoWithDetails,
            files = listOf(fileEntity)
        )

        every { downloadDao.getTasksFlow() } returns flowOf(listOf(taskWithDetails))

        val result = downloadRepository.tasksFlow.first()

        assertEquals(1, result.size)
        assertEquals("t1", result.first().id)
        assertEquals("Task 1", result.first().name)
        assertEquals(1, result.first().files.size)
        assertNotNull(result.first().providerTorrentInfo)
    }

    @Test
    fun testDownloadRepositoryGetTaskByIdNotFound() = runTest {
        coEvery { downloadDao.getTaskById("nonexistent") } returns null

        val result = downloadRepository.getTaskById("nonexistent")

        assertNull(result)
    }

    @Test
    fun testDownloadRepositorySaveAndDeleteTask() = runTest {
        val task = DownloadTask(
            id = "t2",
            name = "Task 2",
            torrent = TorrentDescriptor(type = TorrentType.MAGNET, uri = "magnet:?xt=456")
        )

        downloadRepository.saveTask(task)
        downloadRepository.deleteTask("t2")

        coVerify { downloadDao.deleteTaskById("t2") }
    }

    @Test
    fun testDownloadRepositoryTargetedUpdates() = runTest {
        downloadRepository.updateTaskState("t1", TorrentState.COMPLETED, null)
        downloadRepository.updateFileProgress("t1", "link1", LocalDownloadState.COMPLETED, 100, 0, 1000)

        coVerify { downloadDao.updateTaskState("t1", TorrentState.COMPLETED, null) }
        coVerify { downloadDao.updateFileProgress("t1", "link1", LocalDownloadState.COMPLETED, 100, 0, 1000) }
    }

    @Test
    fun testRssRepositoryGetFeedsFlow() = runTest {
        val feedEntity = RssFeedEntity(
            id = "f1",
            name = "Feed 1",
            url = "http://feed.com",
            destinationSubdirectory = null,
            createSubfolderByName = true,
            notifyOnCompletion = true,
            fileSelectionMode = FileSelectionMode.ALL,
            autoDownload = false,
            lastCheck = 0L
        )
        val itemEntity = RssItemEntity(
            id = "i1",
            feedId = "f1",
            title = "Item 1",
            link = "http://item.com",
            description = "Desc",
            pubDate = 100L,
            isRead = false,
            isDownloaded = false
        )
        val feedWithItems = RssFeedWithItems(
            feed = feedEntity,
            items = listOf(itemEntity)
        )

        every { rssFeedDao.getFeedsFlow() } returns flowOf(listOf(feedWithItems))

        val result = rssRepository.feedsFlow.first()

        assertEquals(1, result.size)
        assertEquals("f1", result.first().id)
        assertEquals("Feed 1", result.first().name)
        assertEquals(1, result.first().items.size)
    }

    @Test
    fun testRssRepositoryGetFeedByIdNotFound() = runTest {
        coEvery { rssFeedDao.getFeedById("nonexistent") } returns null

        val result = rssRepository.getFeedById("nonexistent")

        assertNull(result)
    }

    @Test
    fun testRssRepositorySaveAndDeleteFeed() = runTest {
        val feed = RssFeed(id = "f2", name = "Feed 2", url = "http://f2.com")

        rssRepository.saveFeed(feed)
        rssRepository.deleteFeed("f2")

        coVerify { rssFeedDao.deleteFeedById("f2") }
    }

    @Test
    fun testRssRepositoryTargetedUpdateItemState() = runTest {
        rssRepository.updateItemState("item1", isRead = true, isDownloaded = true)

        coVerify { rssFeedDao.updateItemState("item1", isRead = true, isDownloaded = true) }
    }
}
