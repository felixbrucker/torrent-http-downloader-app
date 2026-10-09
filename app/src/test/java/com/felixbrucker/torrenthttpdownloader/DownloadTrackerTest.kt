package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTrackerTest {
    private val context = mockk<Context>(relaxed = true)
    private val downloadsPrefs = mockk<SharedPreferences>(relaxed = true)
    private val settingsPrefs = mockk<SharedPreferences>(relaxed = true)
    private val gson = Gson()

    @Test
    fun testMoveTaskSuccess() {
        every { downloadsPrefs.getString("tasks", null) } returns null
        every { downloadsPrefs.getString("rss_feeds", null) } returns null
        every { settingsPrefs.getString("rss_feeds", null) } returns null
        val downloadTracker = DownloadTracker(downloadsPrefs, settingsPrefs, gson)
        val task1 = DownloadTaskEntity(id = "1", name = "Task 1", torrent = TorrentDescriptor(TorrentType.MAGNET, "uri1"))
        val task2 = DownloadTaskEntity(id = "2", name = "Task 2", torrent = TorrentDescriptor(TorrentType.MAGNET, "uri2"))
        val task3 = DownloadTaskEntity(id = "3", name = "Task 3", torrent = TorrentDescriptor(TorrentType.MAGNET, "uri3"))
        downloadTracker.addTask(task1)
        downloadTracker.addTask(task2)
        downloadTracker.addTask(task3)

        downloadTracker.moveTask(2, 0)

        val tasks = downloadTracker.getTasks()
        assertEquals(3, tasks.size)
        assertEquals("3", tasks[0].id)
        assertEquals("1", tasks[1].id)
        assertEquals("2", tasks[2].id)
    }
}
