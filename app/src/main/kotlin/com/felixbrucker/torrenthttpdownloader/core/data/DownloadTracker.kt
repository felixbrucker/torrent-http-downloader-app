package com.felixbrucker.torrenthttpdownloader.core.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState

@Singleton
class DownloadTracker @Inject constructor(
    @param:Named("downloads") private val downloadsSharedPreferences: SharedPreferences,
    @param:Named("settings") private val settingsSharedPreferences: SharedPreferences,
    private val gson: Gson,
) {
    private val _tasks = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())

    @get:JvmName("getTasksFlow")
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
            val type = object : TypeToken<List<DownloadTaskEntity>>() {}.type
            _tasks.value = gson.fromJson(json, type) ?: emptyList()
        }

        val rssJsonLegacy = downloadsSharedPreferences.getString("rss_feeds", null)
        if (rssJsonLegacy != null) {
            // Migrate legacy
            val type = object : TypeToken<List<RssFeed>>() {}.type
            _rssFeeds.value = gson.fromJson(rssJsonLegacy, type) ?: emptyList()
            downloadsSharedPreferences.edit { remove("rss_feeds") }
            saveRssFeeds()
        }

        val rssJson = settingsSharedPreferences.getString("rss_feeds", null)
        if (rssJson != null) {
            val type = object : TypeToken<List<RssFeed>>() {}.type
            _rssFeeds.value = gson.fromJson(rssJson, type) ?: emptyList()
        }
    }

    private fun saveTasks() {
        val json = gson.toJson(_tasks.value)
        downloadsSharedPreferences.edit { putString("tasks", json) }
    }

    private fun saveRssFeeds() {
        val json = gson.toJson(_rssFeeds.value)
        settingsSharedPreferences.edit { putString("rss_feeds", json) }
    }

    fun getTasks(): List<DownloadTaskEntity> {
        return _tasks.value
    }

    fun hasTasksWhichNeedProcessing(): Boolean {
        // Early exit on first active task instead of iterating all tasks
        return _tasks.value.any { task ->
            task.state != TorrentState.DOWNLOADING_LOCALLY || task.files.any {
                it.state == LocalDownloadState.PENDING || it.state == LocalDownloadState.DOWNLOADING
            }
        }
    }

    fun hasTask(id: String): Boolean {
        return _tasks.value.any { it.id == id }
    }

    fun addTask(task: DownloadTaskEntity) {
        _tasks.update { it + task }
        saveTasks()
    }

    fun addTaskFiles(taskId: String, files: List<DownloadFileEntity>) {
        updateTask(taskId) { task ->
            task.copy(files = task.files + files)
        }
    }

    fun moveTask(fromIndex: Int, toIndex: Int) {
        _tasks.update { tasks ->
            tasks.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
        saveTasks()
    }

    fun findTaskEntity(id: String): DownloadTaskEntity? {
        return _tasks.value.find { it.id == id }
    }

    fun findTaskFileEntity(taskId: String, fileLink: String): DownloadFileEntity? {
        return findTaskEntity(taskId)?.files?.find { it.link == fileLink }
    }

    fun findTaskFileEntities(taskId: String): List<DownloadFileEntity> {
        return findTaskEntity(taskId)?.files ?: emptyList()
    }

    fun markAllTaskFilesCompletedLocally(taskId: String) {
        updateTaskFiles(taskId) { file ->
            file.copy(
                unrestrictedLink = null,
                state = LocalDownloadState.COMPLETED,
            )
        }
    }

    fun removeTask(id: String) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
        saveTasks()
    }

    fun updateTask(id: String, update: (DownloadTaskEntity) -> DownloadTaskEntity) {
        _tasks.update { tasks ->
            tasks.map { if (it.id == id) update(it) else it }
        }
        saveTasks()
    }

    fun updateTaskFile(taskId: String, fileLink: String, update: (DownloadFileEntity) -> DownloadFileEntity) {
        updateTaskFiles(taskId) {
            if (it.link == fileLink) {
                update(it)
            } else it
        }
    }

    fun updateTaskFiles(taskId: String, update: (DownloadFileEntity) -> DownloadFileEntity) {
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
