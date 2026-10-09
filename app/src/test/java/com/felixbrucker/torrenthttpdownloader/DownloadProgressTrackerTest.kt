package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadProgressTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressTrackerTest {
    private val progressTracker = DownloadProgressTracker()

    @Test
    fun testUpdateFileProgressSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"

        progressTracker.updateFileProgress(taskId, link, 50, 500L, 100L, 123456L, 400L)

        val taskInfo = progressTracker.progressInfo.value[taskId]
        val fileInfo = taskInfo?.fileProgressMap?.get(link)
        assertEquals(50, fileInfo?.progress)
        assertEquals(500L, fileInfo?.downloadedBytes)
        assertEquals(100L, fileInfo?.speed)
        assertEquals(123456L, fileInfo?.lastTimestamp)
        assertEquals(400L, fileInfo?.lastBytes)
    }

    @Test
    fun testUpdateFileDownloadedBytesSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"

        progressTracker.updateFileDownloadedBytes(taskId, link, 1000L)

        val fileInfo = progressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)
        assertEquals(1000L, fileInfo?.downloadedBytes)
    }

    @Test
    fun testUpdateFileSpeedSuccess() {
        val taskId = "task-1"
        val link = "http://example.com/file1"

        progressTracker.updateFileSpeed(taskId, link, 250L)

        val fileInfo = progressTracker.progressInfo.value[taskId]?.fileProgressMap?.get(link)
        assertEquals(250L, fileInfo?.speed)
    }

    @Test
    fun testUpdateProviderTorrentInfoSuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
        )

        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        val taskInfo = progressTracker.progressInfo.value[taskId]
        assertEquals(providerInfo, taskInfo?.providerTorrentInfo)
    }

    @Test
    fun testUpdateProviderTorrentStateSuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
        )
        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        progressTracker.updateProviderTorrentState(taskId, ProviderTorrentState.PAUSED)

        val taskInfo = progressTracker.progressInfo.value[taskId]
        assertEquals(ProviderTorrentState.PAUSED, taskInfo?.providerTorrentInfo?.state)
    }

    @Test
    fun testSetProviderFilePrioritySuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
            files = listOf(
                ProviderTorrentFile(
                    id = 1,
                    path = "f1",
                    size = 500,
                    isSelected = true,
                    progress = 0f,
                    downloadedBytes = 0,
                    priority = FilePriority.NORMAL
                )
            )
        )
        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        progressTracker.setProviderFilePriority(taskId, 1, FilePriority.HIGH)

        val updatedFile = progressTracker.progressInfo.value[taskId]?.providerTorrentInfo?.files?.first()
        assertEquals(FilePriority.HIGH, updatedFile?.priority)
        assertTrue(updatedFile?.isSelected == true)
    }

    @Test
    fun testToggleProviderFileSelectionLocallySuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
            files = listOf(
                ProviderTorrentFile(
                    id = 1,
                    path = "f1",
                    size = 500,
                    isSelected = true,
                    progress = 0f,
                    downloadedBytes = 0,
                    priority = FilePriority.NORMAL
                )
            )
        )
        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        progressTracker.toggleProviderFileSelectionLocally(taskId, 1)

        val updatedFile = progressTracker.progressInfo.value[taskId]?.providerTorrentInfo?.files?.first()
        assertFalse(updatedFile?.isSelected == true)
    }

    @Test
    fun testToggleAllProviderFilesSelectionLocallySuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
            files = listOf(
                ProviderTorrentFile(
                    id = 1,
                    path = "f1",
                    size = 500,
                    isSelected = true,
                    progress = 0f,
                    downloadedBytes = 0,
                    priority = FilePriority.NORMAL
                )
            )
        )
        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        progressTracker.toggleAllProviderFilesSelectionLocally(taskId, false)

        val updatedFile = progressTracker.progressInfo.value[taskId]?.providerTorrentInfo?.files?.first()
        assertFalse(updatedFile?.isSelected == true)
    }

    @Test
    fun testSetProviderTorrentInfoWaitingForFileSelectionSuccess() {
        val taskId = "task-1"
        val providerInfo = ProviderTorrentInfo(
            id = "p-1",
            name = "Test",
            state = ProviderTorrentState.DOWNLOADING,
            status = "downloading",
            progress = 75f,
            totalSizeInBytes = 1000L,
            downloadedBytes = 750L,
            downloadSpeed = 50L,
            uploadSpeed = 0L,
            seeders = 10,
            leechers = 2,
            peers = 12,
            totalPeers = 20,
        )
        progressTracker.updateProviderTorrentInfo(taskId, providerInfo)

        progressTracker.setProviderTorrentInfoWaitingForFileSelection(taskId)

        val updatedInfo = progressTracker.progressInfo.value[taskId]?.providerTorrentInfo
        assertEquals(ProviderTorrentState.WAITING_FOR_FILE_SELECTION, updatedInfo?.state)
        assertEquals("waiting_for_file_selection", updatedInfo?.status)
        assertEquals(0L, updatedInfo?.downloadSpeed)
        assertEquals(0L, updatedInfo?.uploadSpeed)
    }

    @Test
    fun testMarkAllTaskFilesCompletedLocallySuccess() {
        val taskId = "task-1"
        val files = listOf(DownloadFileEntity(link = "http://example.com/file1", totalBytes = 5000L))

        progressTracker.markAllTaskFilesCompletedLocally(taskId, files)

        val fileInfo = progressTracker.progressInfo.value[taskId]?.fileProgressMap?.get("http://example.com/file1")
        assertEquals(100, fileInfo?.progress)
        assertEquals(5000L, fileInfo?.downloadedBytes)
        assertEquals(0L, fileInfo?.speed)
    }

    @Test
    fun testClearTaskSuccess() {
        val taskId = "task-1"
        progressTracker.updateFileSpeed(taskId, "http://example.com/file1", 100L)

        progressTracker.clearTask(taskId)

        assertNull(progressTracker.progressInfo.value[taskId])
    }
}
