package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadProgressTracker
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)
    private val downloadProgressTracker = DownloadProgressTracker()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val staticTasksFlow = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())

    private fun createRepository(): DownloadRepository {
        every { downloadTracker.tasks } returns staticTasksFlow
        return DownloadRepository(downloadTracker, downloadProgressTracker, testScope)
    }

    @Test
    fun testTasksFlowCombinesTrackerAndProgress() {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            files = listOf(DownloadFileEntity(link = "http://example.com/file1"))
        )
        staticTasksFlow.value = listOf(staticTask)
        val repository = createRepository()

        downloadProgressTracker.updateFileProgress("task-1", "http://example.com/file1", 80, 800L, 100L, 12345L, 700L)

        val combinedTasks = repository.tasks.value
        assertEquals(1, combinedTasks.size)
        assertEquals(80, combinedTasks[0].files[0].progress)
        assertEquals(800L, combinedTasks[0].files[0].downloadedBytes)
    }

    @Test
    fun testGetTasksReturnsCombinedTasks() {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        staticTasksFlow.value = listOf(staticTask)
        val repository = createRepository()

        val result = repository.getTasks()

        assertEquals(listOf(staticTask.toDownloadTask()), result)
    }

    @Test
    fun testHasTasksWhichNeedProcessingDelegatesToTracker() {
        every { downloadTracker.hasTasksWhichNeedProcessing() } returns true
        val repository = createRepository()

        val result = repository.hasTasksWhichNeedProcessing()

        assertTrue(result)
        verify { downloadTracker.hasTasksWhichNeedProcessing() }
    }

    @Test
    fun testAddTaskDelegatesToTrackerAndProgress() {
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 50f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 500L,
            downloadSpeed = 10L,
            uploadSpeed = 0L,
            seeders = 1,
            leechers = 1,
            peers = 2,
            totalPeers = 2,
        )
        val task = DownloadTask(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            providerTorrentInfo = providerInfo
        )
        val repository = createRepository()

        repository.addTask(task)

        verify { downloadTracker.addTask(match { it.id == "task-1" }) }
        assertEquals(providerInfo, downloadProgressTracker.progressInfo.value["task-1"]?.providerTorrentInfo)
    }

    @Test
    fun testMoveTaskDelegatesToTracker() {
        every { downloadTracker.moveTask(0, 1) } returns Unit
        val repository = createRepository()

        repository.moveTask(0, 1)

        verify { downloadTracker.moveTask(0, 1) }
    }

    @Test
    fun testFindTaskReturnsTaskWithProgress() {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        staticTasksFlow.value = listOf(staticTask)
        val repository = createRepository()

        val result = repository.findTask("task-1")

        assertEquals(staticTask.toDownloadTask(), result)
    }

    @Test
    fun testHasTaskReturnsTrueWhenFound() {
        every { downloadTracker.hasTask("task-1") } returns true
        val repository = createRepository()

        val result = repository.hasTask("task-1")

        assertTrue(result)
        verify { downloadTracker.hasTask("task-1") }
    }

    @Test
    fun testFindTaskFileReturnsFileWhenFound() {
        val fileEntity = DownloadFileEntity(link = "link-1")
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            files = listOf(fileEntity)
        )
        staticTasksFlow.value = listOf(staticTask)
        val repository = createRepository()

        val result = repository.findTaskFile("task-1", "link-1")

        assertEquals(fileEntity.toDownloadFile(), result)
    }

    @Test
    fun testRemoveTaskDelegatesToTrackerAndClearsProgress() {
        every { downloadTracker.removeTask("task-1") } returns Unit
        downloadProgressTracker.updateFileSpeed("task-1", "link-1", 100L)
        val repository = createRepository()

        repository.removeTask("task-1")

        verify { downloadTracker.removeTask("task-1") }
        assertTrue(downloadProgressTracker.progressInfo.value["task-1"] == null)
    }

    @Test
    fun testUpdateFileDownloadingStateSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val fileSlot = slot<(DownloadFileEntity) -> DownloadFileEntity>()
        every { downloadTracker.updateTaskFile(taskId, link, capture(fileSlot)) } returns Unit
        val repository = createRepository()

        repository.updateFileDownloadingState(taskId, link, 1000L, 500L)

        verify { downloadTracker.updateTaskFile(taskId, link, any()) }
        val initialFile = DownloadFileEntity(link = link)
        val updatedFile = fileSlot.captured(initialFile)
        assertEquals(LocalDownloadState.DOWNLOADING, updatedFile.state)
        assertEquals(1000L, updatedFile.totalBytes)
        assertEquals(500L, downloadProgressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)?.downloadedBytes)
    }

    @Test
    fun testUpdateFileProgressSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val repository = createRepository()

        repository.updateFileProgress(taskId, link, 50, 500L, 100L, 123456789L, 500L)

        val fileInfo = downloadProgressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)
        assertEquals(50, fileInfo?.progress)
        assertEquals(500L, fileInfo?.downloadedBytes)
        assertEquals(100L, fileInfo?.speed)
        assertEquals(123456789L, fileInfo?.lastTimestamp)
        assertEquals(500L, fileInfo?.lastBytes)
    }

    @Test
    fun testUpdateFileStateSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val fileSlot = slot<(DownloadFileEntity) -> DownloadFileEntity>()
        every { downloadTracker.updateTaskFile(taskId, link, capture(fileSlot)) } returns Unit
        val repository = createRepository()

        repository.updateFileState(taskId, link, LocalDownloadState.ERROR, "Network failure")

        verify { downloadTracker.updateTaskFile(taskId, link, any()) }
        val initialFile = DownloadFileEntity(link = link)
        val updatedFile = fileSlot.captured(initialFile)
        assertEquals(LocalDownloadState.ERROR, updatedFile.state)
        assertEquals("Network failure", updatedFile.stateDescription)
        assertEquals(0L, downloadProgressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)?.speed)
    }

    @Test
    fun testUpdateProviderTorrentStateSuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test Task",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 50f,
            totalSizeInBytes = 100,
            downloadedBytes = 50,
            downloadSpeed = 10,
            uploadSpeed = 0,
            seeders = 1,
            leechers = 1,
            peers = 2,
            totalPeers = 2,
            links = emptyList(),
            files = emptyList()
        )
        downloadProgressTracker.updateProviderTorrentInfo(taskId, providerInfo)
        val repository = createRepository()

        repository.updateProviderTorrentState(taskId, ProviderTorrentState.PAUSED)

        assertEquals(ProviderTorrentState.PAUSED, downloadProgressTracker.progressInfo.value[taskId]?.providerTorrentInfo?.state)
    }

    @Test
    fun testSetProviderFilePrioritySuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test Task",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 50f,
            totalSizeInBytes = 100,
            downloadedBytes = 50,
            downloadSpeed = 10,
            uploadSpeed = 0,
            seeders = 1,
            leechers = 1,
            peers = 2,
            totalPeers = 2,
            links = emptyList(),
            files = listOf(
                ProviderTorrentFile(
                    id = 1,
                    path = "f1",
                    size = 100,
                    isSelected = false,
                    progress = 0f,
                    downloadedBytes = 0,
                    priority = FilePriority.NORMAL
                )
            )
        )
        downloadProgressTracker.updateProviderTorrentInfo(taskId, providerInfo)
        val repository = createRepository()

        repository.setProviderFilePriority(taskId, 1, FilePriority.HIGH)

        val updatedFile = downloadProgressTracker.progressInfo.value[taskId]?.providerTorrentInfo?.files?.first()
        assertEquals(FilePriority.HIGH, updatedFile?.priority)
        assertTrue(updatedFile?.isSelected == true)
    }

    @Test
    fun testUpdateTaskStateSuccess() {
        val taskId = "task-1"
        val taskSlot = slot<(DownloadTaskEntity) -> DownloadTaskEntity>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit
        val repository = createRepository()

        repository.updateTaskState(taskId, TorrentState.COMPLETED)

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTaskEntity(
            id = taskId,
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            state = TorrentState.DOWNLOADING_LOCALLY
        )
        val updatedTask = taskSlot.captured(initialTask)
        assertEquals(TorrentState.COMPLETED, updatedTask.state)
    }

    @Test
    fun testUpdateTaskErrorSuccess() {
        val taskId = "task-1"
        val taskSlot = slot<(DownloadTaskEntity) -> DownloadTaskEntity>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit
        val repository = createRepository()

        repository.updateTaskError(taskId, "An error occurred")

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTaskEntity(
            id = taskId,
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            state = TorrentState.DOWNLOADING_LOCALLY
        )
        val updatedTask = taskSlot.captured(initialTask)
        assertEquals(TorrentState.ERROR, updatedTask.state)
        assertEquals("An error occurred", updatedTask.errorMessage)
    }
}
