package com.felixbrucker.torrenthttpdownloader.worker

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.ACTION_ADD_TASK
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_CREATE_SUBFOLDER_BY_NAME
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_DESTINATION_SUBDIRECTORY
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_NOTIFY_ON_COMPLETION
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_FILE_SELECTION_MODE
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_ID
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_NAME
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_URI
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_TYPE
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.network.RssParser
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.max

@HiltWorker
class RssSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadTracker: DownloadTracker,
) : CoroutineWorker(context, params) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private var rssParser: RssParser = RssParser(httpClient)

    override suspend fun doWork(): Result {
        val feedId = inputData.getString("feedId")
        try {
            if (feedId == null) {
                syncRssFeeds()
            } else {
                val feed = downloadTracker.rssFeeds.value.find { it.id == feedId }
                if (feed != null) {
                    syncFeed(feed)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()

            return Result.retry()
        }

        return Result.success()
    }

    private suspend fun syncRssFeeds() {
        downloadTracker.setAllFeedsSyncing(true)
        try {
            downloadTracker.rssFeeds.value.forEach { syncFeed(it) }
        } finally {
            downloadTracker.setAllFeedsSyncing(false)
        }
    }

    private suspend fun syncFeed(feed: RssFeed) {
        downloadTracker.setFeedSyncing(feed.id, true)
        try {
            val newItems = rssParser.fetchAndParse(feed.url)
            val existingItemIds = feed.items.map { it.id }.toSet()
            val newlyDiscoveredItems = newItems.filter { it.id !in existingItemIds }

            if (newlyDiscoveredItems.isNotEmpty()) {
                downloadTracker.updateRssFeed(feed.id) { currentFeed ->
                    val updatedItems = (newlyDiscoveredItems + currentFeed.items)
                        .distinctBy { it.id }
                        .take(max(newItems.size, 25))
                    currentFeed.copy(
                        items = updatedItems,
                        lastCheck = System.currentTimeMillis()
                    )
                }

                // Only auto download after initial fetch
                if (feed.autoDownload && feed.lastCheck > 0) {
                    newlyDiscoveredItems.forEach { item ->
                        addRssItemToDownloads(feed, item)
                    }
                }
            } else {
                downloadTracker.updateRssFeed(feed.id) { it.copy(lastCheck = System.currentTimeMillis()) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            downloadTracker.setFeedSyncing(feed.id, false)
        }
    }

    private suspend fun addRssItemToDownloads(feed: RssFeed, item: RssItem) {
        val result = TorrentUriResolver(applicationContext.contentResolver).resolve(item.link.toUri())
        val intent = Intent(applicationContext, DownloadService::class.java).apply {
            action = ACTION_ADD_TASK
            putExtra(EXTRA_TORRENT_ID, result.id)
            putExtra(EXTRA_TORRENT_URI, result.uri.toString())
            putExtra(EXTRA_TORRENT_NAME, result.name ?: item.title)
            putExtra(EXTRA_DESTINATION_SUBDIRECTORY, feed.destinationSubdirectory)
            putExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, feed.createSubfolderByName)
            putExtra(EXTRA_NOTIFY_ON_COMPLETION, feed.notifyOnCompletion)
            putExtra(EXTRA_FILE_SELECTION_MODE, feed.fileSelectionMode.name)
            putExtra(EXTRA_TORRENT_TYPE, result.type.name)
        }
        applicationContext.startService(intent)

        downloadTracker.updateRssFeed(feed.id) { currentFeed ->
            currentFeed.copy(items = currentFeed.items.map {
                if (it.id == item.id) it.copy(isDownloaded = true, isRead = true) else it
            })
        }
    }
}
