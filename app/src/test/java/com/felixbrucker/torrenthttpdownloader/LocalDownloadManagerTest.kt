package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadWork
import com.felixbrucker.torrenthttpdownloader.core.data.LocalDownloadManager
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDownloadManagerTest {
    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)
    private val downloadRepository = mockk<DownloadRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createManager(): LocalDownloadManager {
        return LocalDownloadManager(
            scope = testScope,
            appSettingsRepository = appSettingsRepository,
            downloadRepository = downloadRepository,
            onLinkExpired = { _, _ -> },
            onPostNotification = { _, _, _, _ -> },
        )
    }

    @Test
    fun testEnqueueDownloadUpdatesState() = runTest(testDispatcher) {
        val manager = createManager()
        val file = DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1")

        manager.enqueueDownload(DownloadWork("task-1", file))

        coVerify { downloadRepository.updateFileState("task-1", "http://example.com/file1", LocalDownloadState.PENDING, null) }
    }

    @Test
    fun testPauseFileUpdatesStateAndCancels() = runTest(testDispatcher) {
        val manager = createManager()

        manager.pauseFile("task-1", "http://example.com/file1")

        coVerify { downloadRepository.updateFileState("task-1", "http://example.com/file1", LocalDownloadState.PAUSED, null) }
    }

    @Test
    fun testResumeFileEnqueues() = runTest(testDispatcher) {
        val file = DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1")
        coEvery { downloadRepository.findTaskFileEntity("task-1", "http://example.com/file1") } returns file
        val manager = createManager()

        manager.resumeFile("task-1", "http://example.com/file1")

        coVerify { downloadRepository.updateFileState("task-1", "http://example.com/file1", LocalDownloadState.PENDING, null) }
    }

    @Test
    fun testPauseTaskAndResumeTask() = runTest(testDispatcher) {
        val file = DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1", state = LocalDownloadState.PAUSED)
        coEvery { downloadRepository.findTaskFileEntities("task-1") } returns listOf(file)
        coEvery { downloadRepository.findTaskFileEntity("task-1", "http://example.com/file1") } returns file
        val manager = createManager()

        manager.pauseTask("task-1")
        manager.resumeTask("task-1")

        coVerify { downloadRepository.updateFileState("task-1", "http://example.com/file1", LocalDownloadState.PENDING, null) }
    }

    @Test
    fun testPauseAllAndResumeAll() = runTest(testDispatcher) {
        coEvery { downloadRepository.getTaskIds() } returns listOf("task-1")
        val manager = createManager()

        manager.pauseAll()
        manager.resumeAll()

        coVerify { downloadRepository.getTaskIds() }
    }

    @Test
    fun testRemoveTaskFromQueuesAndStop() {
        val manager = createManager()
        val file = DownloadFileEntity(taskId = "task-1", link = "http://example.com/file1")

        manager.removeTaskFromQueues("task-1", listOf(file))
        manager.stop()
    }
}
