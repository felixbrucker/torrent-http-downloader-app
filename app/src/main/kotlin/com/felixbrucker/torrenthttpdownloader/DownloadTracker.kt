package com.felixbrucker.torrenthttpdownloader

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.felixbrucker.torrenthttpdownloader.TorrentHttpDownloaderApp.Companion.getContext
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class DownloadTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("downloads") private val downloadsSharedPreferences: SharedPreferences,
    @Named("settings") private val settingsSharedPreferences: SharedPreferences,
    private val gson: Gson,
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val _rssFeeds = MutableStateFlow<List<RssFeed>>(emptyList())
    val rssFeeds = _rssFeeds.asStateFlow()

    private val _syncingFeedIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingFeedIds = _syncingFeedIds.asStateFlow()

    private val _isSyncingAll = MutableStateFlow(false)
    val isSyncingAll = _isSyncingAll.asStateFlow()

    val totalUnreadRssCount = rssFeeds.map { feeds -> feeds.sumOf { it.unreadCount } }

    init {
        val json = downloadsSharedPreferences.getString("tasks", null)
        if (json != null) {
            val type = object : TypeToken<List<DownloadTask>>() {}.type
            _tasks.value = gson.fromJson(json, type)
        }

        val rssJsonLegacy = downloadsSharedPreferences.getString("rss_feeds", null)
        if (rssJsonLegacy != null) {
            // Migrate legacy
            val type = object : TypeToken<List<RssFeed>>() {}.type
            _rssFeeds.value = gson.fromJson(rssJsonLegacy, type)
            downloadsSharedPreferences.edit { remove("rss_feeds") }
            saveRssFeeds()
        }

        val rssJson = settingsSharedPreferences.getString("rss_feeds", null)
        if (rssJson != null) {
            val type = object : TypeToken<List<RssFeed>>() {}.type
            _rssFeeds.value = gson.fromJson(rssJson, type)
        }
    }

    fun saveTasks() {
        val json = gson.toJson(_tasks.value)
        downloadsSharedPreferences.edit { putString("tasks", json) }
    }

    fun saveRssFeeds() {
        val json = gson.toJson(_rssFeeds.value)
        settingsSharedPreferences.edit { putString("rss_feeds", json) }
        BackupManager.dataChanged(context.packageName)
    }

    fun getTasks(): List<DownloadTask> {
        return _tasks.value
    }

    fun hasTasksWhichNeedProcessing(): Boolean {
        // Early exit on first active task instead of iterating all tasks
        return _tasks.value.any { task ->
            if (task.state != TorrentState.DOWNLOADING_LOCALLY) {
                true
            } else {
                task.files.any {
                    it.state == LocalDownloadState.PENDING
                    || it.state == LocalDownloadState.DOWNLOADING
                }
            }
        }
    }

    fun addTask(task: DownloadTask) {
        _tasks.update { it + task }
        saveTasks()
    }

    fun moveTask(fromIndex: Int, toIndex: Int) {
        _tasks.update { tasks ->
            tasks.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
        saveTasks()
    }

    fun findTask(id: String): DownloadTask? {
        return _tasks.value.find { it.id == id }
    }

    fun removeTask(id: String) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
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

    companion object {
        private var instance: DownloadTracker? = null

        fun getInstance(context: Context = getContext()): DownloadTracker {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val settingsSp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val downloadsSp = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
                    val gson = Gson()
                    DownloadTracker(context, downloadsSp, settingsSp, gson).also { instance = it }
                }
            }
        }

        val tasks get() = getInstance().tasks
        val rssFeeds get() = getInstance().rssFeeds
        val syncingFeedIds get() = getInstance().syncingFeedIds
        val isSyncingAll get() = getInstance().isSyncingAll
        val totalUnreadRssCount get() = getInstance().totalUnreadRssCount

        fun getTasks() = getInstance().getTasks()
        fun hasTasksWhichNeedProcessing() = getInstance().hasTasksWhichNeedProcessing()
        fun addTask(task: DownloadTask) = getInstance().addTask(task)
        fun moveTask(fromIndex: Int, toIndex: Int) = getInstance().moveTask(fromIndex, toIndex)
        fun findTask(id: String) = getInstance().findTask(id)
        fun removeTask(id: String) = getInstance().removeTask(id)
        fun updateTask(id: String, update: (DownloadTask) -> DownloadTask) = getInstance().updateTask(id, update)
        fun updateTaskFile(taskId: String, fileLink: String, update: (DownloadFile) -> DownloadFile) = getInstance().updateTaskFile(taskId, fileLink, update)
        fun updateTaskFiles(taskId: String, update: (DownloadFile) -> DownloadFile) = getInstance().updateTaskFiles(taskId, update)
        fun addRssFeed(feed: RssFeed) = getInstance().addRssFeed(feed)
        fun updateRssFeed(id: String, update: (RssFeed) -> RssFeed) = getInstance().updateRssFeed(id, update)
        fun removeRssFeed(id: String) = getInstance().removeRssFeed(id)
        fun setFeedSyncing(feedId: String, syncing: Boolean) = getInstance().setFeedSyncing(feedId, syncing)
        fun setAllFeedsSyncing(syncing: Boolean) = getInstance().setAllFeedsSyncing(syncing)
    }
}
