package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

object DownloadTracker {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _rssFeeds = MutableStateFlow<List<RssFeed>>(emptyList())
    val rssFeeds = _rssFeeds.asStateFlow()

    private val _syncingFeedIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingFeedIds = _syncingFeedIds.asStateFlow()

    private val _isSyncingAll = MutableStateFlow(false)
    val isSyncingAll = _isSyncingAll.asStateFlow()

    val totalUnreadRssCount = rssFeeds.map { feeds -> feeds.sumOf { it.unreadCount } }

    private val gson = Gson()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        val sharedPreferences = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("tasks", null)
        if (json != null) {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            _tasks.value = gson.fromJson(json, type)
        }

        val rssJson = sharedPreferences.getString("rss_feeds", null)
        if (rssJson != null) {
            val type = object : TypeToken<List<RssFeed>>() {}.type
            _rssFeeds.value = gson.fromJson(rssJson, type)
        }
    }

    fun saveTasks() {
        val sharedPreferences = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val json = gson.toJson(_tasks.value)
        sharedPreferences.edit { putString("tasks", json) }
    }

    fun saveRssFeeds() {
        val sharedPreferences = appContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val json = gson.toJson(_rssFeeds.value)
        sharedPreferences.edit { putString("rss_feeds", json) }
    }

    fun getTasks(): List<DownloadTask> {
        return _tasks.value
    }

    fun hasTasksWhichNeedProcessing(): Boolean {
        if (_tasks.value.isEmpty()) {
            return false
        }

        return _tasks.value.all { task ->
            if (task.state != TorrentState.DOWNLOADING_LOCALLY) {
                return true
            }

            return task.files.any {
                it.state == LocalDownloadState.PENDING
                || it.state == LocalDownloadState.DOWNLOADING
            }
        }
    }

    fun addTask(task: DownloadTask) {
        _tasks.update { it + task }
        saveTasks()
    }

    fun findTask(id: String): DownloadTask? {
        return _tasks.value.find { it.id == id }
    }

    fun removeTask(id: String) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
        saveTasks()
    }

    fun replaceTask(oldId: String, newTask: DownloadTask) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == oldId) newTask else it }
        }
        saveTasks()
    }

    fun updateTask(id: String, update: (DownloadTask) -> DownloadTask) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) update(it) else it }
        }
        saveTasks()
    }

    fun updateTaskFile(taskId: String, fileLink: String, update: (DownloadFile) -> DownloadFile) {
        updateTaskFiles(taskId) {
            if (it.link == fileLink) {
                update(it)
            } else it
        }
    }

    fun updateTaskFiles(taskId: String, update: (DownloadFile) -> DownloadFile) {
        updateTask(taskId) { task ->
            task.copy(files = task.files.map {
                update(it)
            })
        }
    }

    fun addRssFeed(feed: RssFeed) {
        _rssFeeds.update { it + feed }
        saveRssFeeds()
    }

    fun updateRssFeed(id: String, update: (RssFeed) -> RssFeed) {
        _rssFeeds.update { feeds ->
            feeds.map { if (it.id == id) update(it) else it }
        }
        saveRssFeeds()
    }

    fun removeRssFeed(id: String) {
        _rssFeeds.update { feeds -> feeds.filterNot { it.id == id } }
        saveRssFeeds()
    }

    fun setFeedSyncing(feedId: String, syncing: Boolean) {
        _syncingFeedIds.update { if (syncing) it + feedId else it - feedId }
    }

    fun setAllFeedsSyncing(syncing: Boolean) {
        _isSyncingAll.value = syncing
    }
}
