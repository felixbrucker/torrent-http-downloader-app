package com.felixbrucker.torrenthttpdownloader.worker

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import com.felixbrucker.torrenthttpdownloader.core.network.RssParser
import com.felixbrucker.torrenthttpdownloader.core.network.TorrentUriResolver
import timber.log.Timber

@HiltWorker
class RssSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rssRepository: RssRepository,
    private val torrentUriResolver: TorrentUriResolver,
) : CoroutineWorker(context, params) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private var rssParser: RssParser = RssParser(httpClient)

    override suspend fun doWork(): Result {
        val feedId = inputData.getString("feedId")
        Timber.i("Starting RSS sync worker feedId=%s", feedId)
        try {
            if (feedId == null) {
                syncRssFeeds()
            } else {
                val feed = rssRepository.findRssFeed(feedId)
                if (feed != null) {
                    syncFeed(feed)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in RssSyncWorker feedId=%s", feedId)

            return Result.retry()
        }

        Timber.i("Completed RSS sync worker feedId=%s", feedId)
        return Result.success()
    }

    private suspend fun syncRssFeeds() {
        rssRepository.setAllFeedsSyncing(true)
        try {
            rssRepository.rssFeeds.value.forEach { syncFeed(it) }
        } finally {
            rssRepository.setAllFeedsSyncing(false)
        }
    }

    private suspend fun syncFeed(feed: RssFeed) {
        rssRepository.setFeedSyncing(feed.id, true)
        try {
            val newItems = rssParser.fetchAndParse(feed.url)
            val existingItemIds = feed.items.map { it.id }.toSet()
            val newlyDiscoveredItems = newItems.filter { it.id !in existingItemIds }

            if (newlyDiscoveredItems.isNotEmpty()) {
                rssRepository.updateDiscoveredItems(
                    feedId = feed.id,
                    newlyDiscoveredItems = newlyDiscoveredItems,
                    totalNewItemsCount = newItems.size,
                )

                // Only auto download after initial fetch
                if (feed.autoDownload && feed.lastCheck > 0) {
                    newlyDiscoveredItems.forEach { item ->
                        addRssItemToDownloads(feed, item)
                    }
                }
            } else {
                rssRepository.updateLastCheck(feed.id)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync RSS feed id=%s", feed.id)
        } finally {
            rssRepository.setFeedSyncing(feed.id, false)
        }
    }

    private suspend fun addRssItemToDownloads(feed: RssFeed, item: RssItem) {
        val result = torrentUriResolver.resolve(item.link.toUri())
        val intent = Intent(applicationContext, DownloadService::class.java).apply {
            action = DownloadService.ACTION_ADD_TASK
            putExtra(DownloadService.EXTRA_TORRENT_ID, result.id)
            putExtra(DownloadService.EXTRA_TORRENT_URI, result.uri.toString())
            putExtra(DownloadService.EXTRA_TORRENT_NAME, result.name ?: item.title)
            putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, feed.destinationSubdirectory)
            putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, feed.createSubfolderByName)
            putExtra(DownloadService.EXTRA_NOTIFY_ON_COMPLETION, feed.notifyOnCompletion)
            putExtra(DownloadService.EXTRA_FILE_SELECTION_MODE, feed.fileSelectionMode.name)
            putExtra(DownloadService.EXTRA_TORRENT_TYPE, result.type.name)
        }
        applicationContext.startService(intent)

        rssRepository.markItemAsDownloadedAndRead(feed.id, item.id)
    }
}
