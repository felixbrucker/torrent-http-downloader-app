package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.model.toNotificationConfig
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationConfigTest {

    @Test
    fun testEmptyTaskListToNotificationConfig() {
        val tasks = emptyList<DownloadTask>()

        val config = tasks.toNotificationConfig(providerSupportsPauseResume = false)

        assertEquals(0, config.taskCount)
        assertEquals(0L, config.totalSpeed)
        assertEquals(0, config.avgProgress)
        assertEquals(0L, config.totalDownloadedBytes)
        assertEquals(0L, config.totalBytes)
        assertEquals(0, config.runningLocalDownloads)
        assertEquals(0, config.totalLocalDownloads)
        assertEquals(0, config.completedLocalDownloads)
        assertFalse(config.hasRunningProviderTasks)
        assertFalse(config.anyLocalDownloading)
        assertFalse(config.anyLocalPaused)
        assertFalse(config.anyDownloadingOnProvider)
        assertFalse(config.anyPausedOnProvider)
        assertFalse(config.providerSupportsPauseResume)
    }

    @Test
    fun testTaskListToNotificationConfigAggregatesAndFlags() {
        val file1 = DownloadFile(
            link = "http://file1",
            state = LocalDownloadState.DOWNLOADING,
            speed = 1000L,
            totalBytes = 2000L,
            downloadedBytes = 1000L
        )
        val file2 = DownloadFile(
            link = "http://file2",
            state = LocalDownloadState.COMPLETED,
            speed = 0L,
            totalBytes = 1000L,
            downloadedBytes = 1000L
        )
        val task1 = DownloadTask(
            id = "task1",
            name = "Task 1",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:1"),
            state = TorrentState.DOWNLOADING_LOCALLY,
            files = listOf(file1, file2)
        )
        val task2 = DownloadTask(
            id = "task2",
            name = "Task 2",
            torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:2"),
            state = TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD,
            providerTorrentInfo = ProviderTorrentInfo(
                id = "p2",
                name = "Provider Task 2",
                state = ProviderTorrentState.DOWNLOADING,
                status = "downloading",
                downloadSpeed = 500L,
                uploadSpeed = 0L,
                totalSizeInBytes = 4000L,
                downloadedBytes = 2000L,
                progress = 50f,
                seeders = 10,
                leechers = 5,
                peers = 15,
                totalPeers = 20
            )
        )
        val tasks = listOf(task1, task2)

        val config = tasks.toNotificationConfig(providerSupportsPauseResume = true)

        assertEquals(2, config.taskCount)
        assertEquals(1500L, config.totalSpeed)
        assertEquals(58, config.avgProgress)
        assertEquals(4000L, config.totalDownloadedBytes)
        assertEquals(7000L, config.totalBytes)
        assertEquals(1, config.runningLocalDownloads)
        assertEquals(2, config.totalLocalDownloads)
        assertEquals(1, config.completedLocalDownloads)
        assertTrue(config.hasRunningProviderTasks)
        assertTrue(config.anyLocalDownloading)
        assertFalse(config.anyLocalPaused)
        assertTrue(config.anyDownloadingOnProvider)
        assertFalse(config.anyPausedOnProvider)
        assertTrue(config.providerSupportsPauseResume)
    }

    @Test
    fun testDistinctUntilChangedFiltersIdenticalConfigs() = runTest {
        val fileDownloading = DownloadFile(
            link = "http://file1",
            state = LocalDownloadState.DOWNLOADING,
            speed = 1000L,
            totalBytes = 2000L,
            downloadedBytes = 1000L
        )
        val fileCompleted = DownloadFile(
            link = "http://file1",
            state = LocalDownloadState.COMPLETED,
            speed = 0L,
            totalBytes = 2000L,
            downloadedBytes = 2000L
        )
        val tasks1 = listOf(
            DownloadTask(
                id = "task1",
                name = "Task 1",
                torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:1"),
                state = TorrentState.DOWNLOADING_LOCALLY,
                files = listOf(fileDownloading)
            )
        )
        val tasks2 = listOf(
            DownloadTask(
                id = "task1",
                name = "Task 1",
                torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:1"),
                state = TorrentState.DOWNLOADING_LOCALLY,
                files = listOf(fileDownloading)
            )
        )
        val tasks3 = listOf(
            DownloadTask(
                id = "task1",
                name = "Task 1",
                torrent = TorrentDescriptor(TorrentType.MAGNET, "magnet:?xt=urn:btih:1"),
                state = TorrentState.COMPLETED,
                files = listOf(fileCompleted)
            )
        )
        val flow = listOf(tasks1, tasks2, tasks3).asFlow()

        val emitted = flow
            .map { it.toNotificationConfig() }
            .distinctUntilChanged()
            .toList()

        assertEquals(2, emitted.size)
    }
}
