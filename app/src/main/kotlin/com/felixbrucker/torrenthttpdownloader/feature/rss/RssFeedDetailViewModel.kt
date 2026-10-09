package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RssFeedDetailViewModel @Inject constructor(
    private val rssRepository: RssRepository,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    val syncingFeedIds: StateFlow<Set<String>> = rssRepository.syncingFeedIds

    fun getFeedFlow(feedId: String): Flow<RssFeed?> {
        return rssRepository.getFeedFlow(feedId)
    }

    fun markItemAsRead(feedId: String, itemId: String) {
        viewModelScope.launch { rssRepository.markItemAsRead(feedId, itemId) }
    }

    fun markAllItemsAsRead(feedId: String) {
        viewModelScope.launch { rssRepository.markAllItemsAsRead(feedId) }
    }

    fun syncFeed(feed: RssFeed) {
        rssSyncLauncher.runRssSyncOnce(feed.id)
    }
}
