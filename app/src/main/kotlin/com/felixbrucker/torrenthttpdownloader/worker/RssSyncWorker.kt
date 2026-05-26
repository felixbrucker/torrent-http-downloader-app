package com.felixbrucker.torrenthttpdownloader.worker

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.ACTION_ADD_TASK
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_CREATE_SUBFOLDER_BY_NAME
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_DESTINATION_SUBDIRECTORY
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_NOTIFY_ON_COMPLETION
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_NAME
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_PATH
import com.felixbrucker.torrenthttpdownloader.DownloadService.Companion.EXTRA_TORRENT_TYPE
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.network.RssParser
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RssSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
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
                val feed = DownloadTracker.rssFeeds.value.find { it.id == feedId }
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
        DownloadTracker.setAllFeedsSyncing(true)
        try {
            DownloadTracker.rssFeeds.value.forEach { syncFeed(it) }
        } finally {
            DownloadTracker.setAllFeedsSyncing(false)
        }
    }

    private suspend fun syncFeed(feed: RssFeed) {
        DownloadTracker.setFeedSyncing(feed.id, true)
        try {
            val newItems = rssParser.fetchAndParse(feed.url)
            val existingItemIds = feed.items.map { it.id }.toSet()
            val newlyDiscoveredItems = newItems.filter { it.id !in existingItemIds }

            if (newlyDiscoveredItems.isNotEmpty()) {
                DownloadTracker.updateRssFeed(feed.id) { currentFeed ->
                    val updatedItems = (newlyDiscoveredItems + currentFeed.items).distinctBy { it.id }
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
                DownloadTracker.updateRssFeed(feed.id) { it.copy(lastCheck = System.currentTimeMillis()) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            DownloadTracker.setFeedSyncing(feed.id, false)
        }
    }

    private suspend fun addRssItemToDownloads(feed: RssFeed, item: RssItem) {
        val result = TorrentUriResolver(applicationContext.contentResolver).resolve(item.link.toUri())
        val intent = Intent(applicationContext, DownloadService::class.java).apply {
            action = ACTION_ADD_TASK
            putExtra(EXTRA_TORRENT_PATH, result.uri.toString())
            putExtra(EXTRA_TORRENT_NAME, result.name ?: item.title)
            putExtra(EXTRA_DESTINATION_SUBDIRECTORY, feed.destinationSubdirectory)
            putExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, feed.createSubfolderByName)
            putExtra(EXTRA_NOTIFY_ON_COMPLETION, feed.notifyOnCompletion)
            putExtra(EXTRA_TORRENT_TYPE, result.type.name)
        }
        applicationContext.startService(intent)

        DownloadTracker.updateRssFeed(feed.id) { currentFeed ->
            currentFeed.copy(items = currentFeed.items.map {
                if (it.id == item.id) it.copy(isDownloaded = true, isRead = true) else it
            })
        }
    }
}