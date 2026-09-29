package com.felixbrucker.torrenthttpdownloader

import android.content.ContentResolver
import io.mockk.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.LocalDownloadManager
import com.felixbrucker.torrenthttpdownloader.core.data.TorrentStateMachine
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.designsystem.icons.downloading
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.util.PathFactory

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentStateMachineStressTest {
    private val provider = mockk<TorrentProvider>()
    private val localDownloadManager = mockk<LocalDownloadManager>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>()
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val pathFactory = mockk<PathFactory>()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var stateMachine: TorrentStateMachine

    @Before
    fun setup() {
        stateMachine = TorrentStateMachine(
            scope = testScope,
            provider = provider,
            localDownloadManager = localDownloadManager,
            contentResolver = contentResolver,
            downloadRepository = downloadRepository,
            pathFactory = pathFactory,
            onTaskCompleted = {},
            onPostNotification = { _, _, _, _ -> }
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `processTaskSafely should prevent concurrent execution for the same taskId`() = runTest {
        val taskId = "test-task-id"
        val task = DownloadTask(
            id = taskId,
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:123"),
            state = TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD,
            providerId = "provider-id"
        )

        every { downloadRepository.findTask(taskId) } returns task
        every { provider.isLocalProvider } returns false

        val callCount = AtomicInteger(0)

        coEvery { provider.getTorrentInfo(any()) } coAnswers {
            callCount.incrementAndGet()
            delay(100.milliseconds) // Simulate work
            ProviderTorrentInfo(
                id = "provider-id",
                name = "Test Task",
                state = ProviderTorrentState.DOWNLOADING,
                status = "downloading",
                progress = 50f,
                totalSizeInBytes = 1000,
                downloadedBytes = 500,
                downloadSpeed = 100,
                uploadSpeed = 0,
                seeders = 5,
                leechers = 1,
                peers = 6,
                totalPeers = 10,
                links = emptyList(),
                files = emptyList()
            )
        }

        // Launch multiple coroutines trying to process the same task simultaneously
        val jobs = List(10) {
            async(Dispatchers.Default) {
                stateMachine.processTaskSafely(taskId)
            }
        }

        jobs.awaitAll()

        assertEquals("Expected only 1 execution due to locking", 1, callCount.get())
    }

    @Test
    fun `processTaskSafely should allow concurrent execution for different taskIds`() = runTest {
        val taskId1 = "task-1"
        val taskId2 = "task-2"
        val task1 = DownloadTask(
            id = taskId1,
            name = "Task 1",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:1"),
            state = TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD,
            providerId = "provider-id-1"
        )
        val task2 = DownloadTask(
            id = taskId2,
            name = "Task 2",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:2"),
            state = TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD,
            providerId = "provider-id-2"
        )

        every { downloadRepository.findTask(taskId1) } returns task1
        every { downloadRepository.findTask(taskId2) } returns task2
        every { provider.isLocalProvider } returns false

        val callCount = AtomicInteger(0)

        coEvery { provider.getTorrentInfo(any()) } coAnswers {
            callCount.incrementAndGet()
            delay(100.milliseconds)
            ProviderTorrentInfo(
                id = "provider-id",
                name = "Test Task",
                state = ProviderTorrentState.DOWNLOADING,
                status = "downloading",
                progress = 50f,
                totalSizeInBytes = 1000,
                downloadedBytes = 500,
                downloadSpeed = 100,
                uploadSpeed = 0,
                seeders = 5,
                leechers = 1,
                peers = 6,
                totalPeers = 10,
                links = emptyList(),
                files = emptyList()
            )
        }

        val job1 = async(Dispatchers.Default) { stateMachine.processTaskSafely(taskId1) }
        val job2 = async(Dispatchers.Default) { stateMachine.processTaskSafely(taskId2) }

        awaitAll(job1, job2)

        assertEquals("Expected 2 executions for 2 different tasks", 2, callCount.get())
    }
}
