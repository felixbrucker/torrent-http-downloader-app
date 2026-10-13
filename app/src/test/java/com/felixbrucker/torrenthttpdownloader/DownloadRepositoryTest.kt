package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadProgressTracker
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.database.dao.DownloadDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val downloadProgressTracker = DownloadProgressTracker()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val tasksWithFilesFlow = MutableStateFlow<Map<DownloadTaskEntity, List<DownloadFileEntity>>>(emptyMap())

    private fun createRepository(): DownloadRepository {
        coEvery { downloadDao.getTasksWithFilesFlow() } returns tasksWithFilesFlow
        return DownloadRepository(
            downloadDao = downloadDao,
            downloadProgressTracker = downloadProgressTracker,
            scope = testScope,
        )
    }

    @Test
    fun testTasksFlowCombinesDaoAndProgress() {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri"),
        )
        val staticFile = DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1")
        tasksWithFilesFlow.value = mapOf(staticTask to listOf(staticFile))
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
        tasksWithFilesFlow.value = mapOf(staticTask to emptyList())
        val repository = createRepository()

        val result = repository.getTasks()

        assertEquals(1, result.size)
        assertEquals("task-1", result[0].id)
    }

    @Test
    fun testGetTaskIdsReturnsIds() = runTest(testDispatcher) {
        coEvery { downloadDao.getTaskIds() } returns listOf("task-1")
        val repository = createRepository()

        val ids = repository.getTaskIds()

        assertEquals(listOf("task-1"), ids)
    }

    @Test
    fun testHasTasksWhichNeedProcessingReturnsTrue() = runTest(testDispatcher) {
        coEvery { downloadDao.hasTasksWhichNeedProcessing() } returns true
        val repository = createRepository()

        val result = repository.hasTasksWhichNeedProcessing()

        assertTrue(result)
        coVerify { downloadDao.hasTasksWhichNeedProcessing() }
    }

    @Test
    fun testAddTaskDelegatesToDao() = runTest(testDispatcher) {
        val task = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        val repository = createRepository()

        repository.addTask(task)

        coVerify { downloadDao.insertTask(match { it.id == "task-1" }) }
    }

    @Test
    fun testAddTaskFilesDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()
        val files = listOf(DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1"))

        repository.addTaskFiles(files)

        coVerify { downloadDao.insertFiles(files) }
    }

    @Test
    fun testMoveTaskDelegatesToDao() = runTest(testDispatcher) {
        val task1 = DownloadTaskEntity(id = "1", name = "1", torrent = TorrentDescriptor(TorrentType.MAGNET, "1"))
        val task2 = DownloadTaskEntity(id = "2", name = "2", torrent = TorrentDescriptor(TorrentType.MAGNET, "2"))
        coEvery { downloadDao.getTasksWithFiles() } returns mapOf(
            task1 to emptyList(),
            task2 to emptyList()
        )
        val repository = createRepository()

        repository.moveTask(0, 1)

        coVerify { downloadDao.updateTaskDisplayOrder(any(), any()) }
    }

    @Test
    fun testFindTaskReturnsTaskWithProgress() {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        tasksWithFilesFlow.value = mapOf(staticTask to emptyList())
        val repository = createRepository()

        val result = repository.findTask("task-1")

        assertEquals("task-1", result?.id)
    }

    @Test
    fun testFindTaskEntityReturnsEntity() = runTest(testDispatcher) {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "Test Task",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        coEvery { downloadDao.getTask("task-1") } returns staticTask
        val repository = createRepository()

        val result = repository.findTaskEntity("task-1")

        assertEquals("task-1", result?.id)
    }

    @Test
    fun testHasTaskReturnsTrueWhenFound() = runTest(testDispatcher) {
        coEvery { downloadDao.hasTask("task-1") } returns true
        val repository = createRepository()

        val result = repository.hasTask("task-1")

        assertTrue(result)
        coVerify { downloadDao.hasTask("task-1") }
    }

    @Test
    fun testFindTaskFileEntityAndEntitiesReturnFiles() = runTest(testDispatcher) {
        val fileEntity = DownloadFileEntity(taskId = "task-1", link = "link-1")
        coEvery { downloadDao.getFile("task-1", "link-1") } returns fileEntity
        coEvery { downloadDao.getFilesForTask("task-1") } returns listOf(fileEntity)
        val repository = createRepository()

        val file = repository.findTaskFileEntity("task-1", "link-1")
        val files = repository.findTaskFileEntities("task-1")

        assertNotNull(file)
        assertEquals(1, files.size)
    }

    @Test
    fun testRemoveTaskDelegatesToDaoAndClearsProgress() = runTest(testDispatcher) {
        downloadProgressTracker.updateFileSpeed("task-1", "link-1", 100L)
        val repository = createRepository()

        repository.removeTask("task-1")

        coVerify { downloadDao.deleteTask("task-1") }
        assertTrue(downloadProgressTracker.progressInfo.value["task-1"] == null)
    }

    @Test
    fun testUpdateFileDownloadingStateSuccess() = runTest(testDispatcher) {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val repository = createRepository()

        repository.updateFileDownloadingState(taskId, link, 1000L, 500L)

        coVerify { downloadDao.updateFileDownloadingState(taskId, link, LocalDownloadState.DOWNLOADING, 1000L) }
        assertEquals(500L, downloadProgressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)?.downloadedBytes)
    }

    @Test
    fun testUpdateFileStateSuccess() = runTest(testDispatcher) {
        val taskId = "task-1"
        val link = "http://example.com/file1"
        val repository = createRepository()

        repository.updateFileState(taskId, link, LocalDownloadState.ERROR, "Network failure")

        coVerify { downloadDao.updateFileState(taskId, link, LocalDownloadState.ERROR, "Network failure") }
        assertEquals(0L, downloadProgressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)?.speed)
    }

    @Test
    fun testUpdateTaskStateSuccess() = runTest(testDispatcher) {
        val taskId = "task-1"
        val repository = createRepository()

        repository.updateTaskState(taskId, TorrentState.COMPLETED)

        coVerify { downloadDao.updateTaskState(taskId, TorrentState.COMPLETED) }
    }

    @Test
    fun testUpdateTaskErrorSuccess() = runTest(testDispatcher) {
        val taskId = "task-1"
        val repository = createRepository()

        repository.updateTaskError(taskId, "An error occurred")

        coVerify { downloadDao.updateTaskError(taskId, "An error occurred", TorrentState.ERROR) }
    }

    @Test
    fun testUpdateTaskFileInfoSuccess() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.updateTaskFileInfo("task-1", "link-1", 1000L, "/path", "file.mp4", "http://dl.com")

        coVerify { downloadDao.updateTaskFileInfo("task-1", "link-1", 1000L, "/path", "file.mp4", "http://dl.com") }
    }

    @Test
    fun testUpdateTaskProviderIdAndStateSuccess() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.updateTaskProviderIdAndState("task-1", "p-1", TorrentState.DOWNLOADING_LOCALLY)

        coVerify { downloadDao.updateTaskProviderIdAndState("task-1", "p-1", TorrentState.DOWNLOADING_LOCALLY) }
    }

    @Test
    fun testMarkAllTaskFilesCompletedLocallySuccess() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.markAllTaskFilesCompletedLocally("task-1")

        coVerify { downloadDao.markAllTaskFilesCompletedLocally("task-1", LocalDownloadState.COMPLETED) }
    }

    @Test
    fun testResetTaskForRetrySuccess() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.resetTaskForRetry("task-1")

        coVerify { downloadDao.resetTaskForRetry("task-1", TorrentState.ADDING_TO_PROVIDER) }
    }

    @Test
    fun testUpdateTaskWithTorrentInfoSuccess() = runTest(testDispatcher) {
        val staticTask = DownloadTaskEntity(
            id = "task-1",
            name = "uri",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "uri")
        )
        coEvery { downloadDao.getTask("task-1") } returns staticTask
        val repository = createRepository()
        val providerInfo = ProviderTorrentInfo(
            id = "p-1", name = "New Name", state = ProviderTorrentState.DOWNLOADING,
            status = "dl", progress = 0f, totalSizeInBytes = 0, downloadedBytes = 0,
            downloadSpeed = 0, uploadSpeed = 0, seeders = 0, leechers = 0, peers = 0, totalPeers = 0,
            links = emptyList(), files = emptyList()
        )

        repository.updateTaskWithTorrentInfo("task-1", providerInfo)

        coVerify { downloadDao.updateTaskNameIfDefault("task-1", "New Name") }
    }
}
