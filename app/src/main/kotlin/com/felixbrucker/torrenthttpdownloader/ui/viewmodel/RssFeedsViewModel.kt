package com.felixbrucker.torrenthttpdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RssFeedsViewModel @Inject constructor(
    private val rssRepository: RssRepository
) : ViewModel() {

    val feeds: StateFlow<List<RssFeed>> = rssRepository.feedsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssRepository.insertFeed(feed)
        }
    }

    fun removeFeed(feedId: String) {
        viewModelScope.launch {
            rssRepository.deleteFeed(feedId)
        }
    }
}
