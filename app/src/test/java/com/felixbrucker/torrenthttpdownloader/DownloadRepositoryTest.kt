package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRepositoryTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)
    private val repository = DownloadRepository(downloadTracker)

    @Test
    fun testTasksFlowDelegatesToTracker() {
        val flow = MutableStateFlow<List<DownloadTask>>(emptyList())
        every { downloadTracker.tasks } returns flow

        val result = repository.tasks

        assertEquals(flow, result)
        verify { downloadTracker.tasks }
    }

    @Test
    fun testGetTasksDelegatesToTracker() {
        val tasks = listOf(
            DownloadTask(
                id = "task-1",
                name = "Test Task",
                torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
            )
        )
        every { downloadTracker.getTasks() } returns tasks

        val result = repository.getTasks()

        assertEquals(tasks, result)
        verify { downloadTracker.getTasks() }
    }

    @Test
    fun testHasTasksWhichNeedProcessingDelegatesToTracker() {
        every { downloadTracker.hasTasksWhichNeedProcessing() } returns true

        val result = repository.hasTasksWhichNeedProcessing()

        assertTrue(result)
        verify { downloadTracker.hasTasksWhichNeedProcessing() }
    }

    @Test
    fun testAddTaskDelegatesToTracker() {
        val task = DownloadTask(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        every { downloadTracker.addTask(task) } returns Unit

        repository.addTask(task)

        verify { downloadTracker.addTask(task) }
    }

    @Test
    fun testMoveTaskDelegatesToTracker() {
        every { downloadTracker.moveTask(0, 1) } returns Unit

        repository.moveTask(0, 1)

        verify { downloadTracker.moveTask(0, 1) }
    }

    @Test
    fun testFindTaskDelegatesToTracker() {
        val task = DownloadTask(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        every { downloadTracker.findTask("task-1") } returns task

        val result = repository.findTask("task-1")

        assertEquals(task, result)
        verify { downloadTracker.findTask("task-1") }
    }

    @Test
    fun testHasTaskReturnsTrueWhenFound() {
        val task = DownloadTask(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        every { downloadTracker.getTasks() } returns listOf(task)

        val result = repository.hasTask("task-1")

        assertTrue(result)
    }

    @Test
    fun testFindTaskFileReturnsFileWhenFound() {
        val file = DownloadFile(link = "link-1")
        val task = DownloadTask(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            files = listOf(file)
        )
        every { downloadTracker.findTask("task-1") } returns task

        val result = repository.findTaskFile("task-1", "link-1")

        assertEquals(file, result)
    }

    @Test
    fun testRemoveTaskDelegatesToTracker() {
        every { downloadTracker.removeTask("task-1") } returns Unit

        repository.removeTask("task-1")

        verify { downloadTracker.removeTask("task-1") }
    }

    @Test
    fun testUpdateFileDownloadingStateSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val fileSlot = slot<(DownloadFile) -> DownloadFile>()
        every { downloadTracker.updateTaskFile(taskId, link, capture(fileSlot)) } returns Unit

        repository.updateFileDownloadingState(taskId, link, 1000L, 500L)

        verify { downloadTracker.updateTaskFile(taskId, link, any()) }
        val initialFile = DownloadFile(link = link)
        val updatedFile = fileSlot.captured(initialFile)
        assertEquals(LocalDownloadState.DOWNLOADING, updatedFile.state)
        assertEquals(1000L, updatedFile.totalBytes)
        assertEquals(500L, updatedFile.downloadedBytes)
    }

    @Test
    fun testUpdateFileProgressSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val fileSlot = slot<(DownloadFile) -> DownloadFile>()
        every { downloadTracker.updateTaskFile(taskId, link, capture(fileSlot)) } returns Unit

        repository.updateFileProgress(taskId, link, 50, 500L, 100L, 123456789L, 500L)

        verify { downloadTracker.updateTaskFile(taskId, link, any()) }
        val initialFile = DownloadFile(link = link)
        val updatedFile = fileSlot.captured(initialFile)
        assertEquals(50, updatedFile.progress)
        assertEquals(500L, updatedFile.downloadedBytes)
        assertEquals(100L, updatedFile.speed)
        assertEquals(123456789L, updatedFile.lastTimestamp)
        assertEquals(500L, updatedFile.lastBytes)
    }

    @Test
    fun testUpdateFileStateSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val fileSlot = slot<(DownloadFile) -> DownloadFile>()
        every { downloadTracker.updateTaskFile(taskId, link, capture(fileSlot)) } returns Unit

        repository.updateFileState(taskId, link, LocalDownloadState.ERROR, "Network failure")

        verify { downloadTracker.updateTaskFile(taskId, link, any()) }
        val initialFile = DownloadFile(link = link, speed = 100L)
        val updatedFile = fileSlot.captured(initialFile)
        assertEquals(LocalDownloadState.ERROR, updatedFile.state)
        assertEquals("Network failure", updatedFile.stateDescription)
        assertEquals(0L, updatedFile.speed)
    }

    @Test
    fun testUpdateProviderTorrentStateSuccess() {
        val taskId = "task-1"
        val taskSlot = slot<(DownloadTask) -> DownloadTask>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit

        repository.updateProviderTorrentState(taskId, ProviderTorrentState.PAUSED)

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTask(
            id = taskId,
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            providerTorrentInfo = ProviderTorrentInfo(
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
        )
        val updatedTask = taskSlot.captured(initialTask)
        assertEquals(ProviderTorrentState.PAUSED, updatedTask.providerTorrentInfo?.state)
    }

    @Test
    fun testSetProviderFilePrioritySuccess() {
        val taskId = "task-1"
        val taskSlot = slot<(DownloadTask) -> DownloadTask>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit

        repository.setProviderFilePriority(taskId, 1, FilePriority.HIGH)

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTask(
            id = taskId,
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
            providerTorrentInfo = ProviderTorrentInfo(
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
        )
        val updatedTask = taskSlot.captured(initialTask)
        assertEquals(FilePriority.HIGH, updatedTask.providerTorrentInfo?.files?.first()?.priority)
        assertTrue(updatedTask.providerTorrentInfo?.files?.first()?.isSelected == true)
    }

    @Test
    fun testUpdateTaskStateSuccess() {
        val taskId = "task-1"
        val taskSlot = slot<(DownloadTask) -> DownloadTask>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit

        repository.updateTaskState(taskId, TorrentState.COMPLETED)

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTask(
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
        val taskSlot = slot<(DownloadTask) -> DownloadTask>()
        every { downloadTracker.updateTask(taskId, capture(taskSlot)) } returns Unit

        repository.updateTaskError(taskId, "An error occurred")

        verify { downloadTracker.updateTask(taskId, any()) }
        val initialTask = DownloadTask(
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
