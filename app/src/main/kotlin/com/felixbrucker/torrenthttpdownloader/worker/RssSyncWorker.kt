package com.felixbrucker.torrenthttpdownloader.worker

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.felixbrucker.torrenthttpdownloader.download.DownloadService
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.network.RssParser
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import kotlin.math.max

@HiltWorker
class RssSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rssRepository: RssRepository,
    private val torrentUriResolver: TorrentUriResolver,
    private val httpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    private val rssParser: RssParser = RssParser(httpClient)

    override suspend fun doWork(): Result {
        val feedId = inputData.getString("feedId")
        try {
            if (feedId == null) {
                syncRssFeeds()
            } else {
                val feed = rssRepository.getFeedById(feedId)
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
        val feeds = rssRepository.feedsFlow.first()
        feeds.forEach { syncFeed(it) }
    }

    private suspend fun syncFeed(feed: RssFeed) {
        try {
            val newItems = rssParser.fetchAndParse(feed.url)
            val existingItemIds = feed.items.map { it.id }.toSet()
            val newlyDiscoveredItems = newItems.filter { it.id !in existingItemIds }

            if (newlyDiscoveredItems.isNotEmpty()) {
                rssRepository.insertItems(feed.id, newlyDiscoveredItems)
                rssRepository.updateFeedLastCheck(feed.id, System.currentTimeMillis())

                if (feed.autoDownload && feed.lastCheck > 0) {
                    newlyDiscoveredItems.forEach { item ->
                        addRssItemToDownloads(feed, item)
                    }
                }
            } else {
                rssRepository.updateFeedLastCheck(feed.id, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        rssRepository.updateItemState(item.id, isRead = true, isDownloaded = true)
    }
}
