package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed

@HiltViewModel
class RssFeedDetailViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val rssRepository: RssRepository
) : ViewModel() {

    val feeds: StateFlow<List<RssFeed>> = downloadTracker.rssFeeds
    val syncingFeedIds: StateFlow<Set<String>> = downloadTracker.syncingFeedIds

    fun markItemAsRead(feedId: String, itemId: String) {
        rssRepository.markItemAsRead(feedId, itemId)
    }

    fun markAllItemsAsRead(feedId: String) {
        rssRepository.markAllItemsAsRead(feedId)
    }
}
