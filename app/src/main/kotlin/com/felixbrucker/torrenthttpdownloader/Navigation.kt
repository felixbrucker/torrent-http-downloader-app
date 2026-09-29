package com.felixbrucker.torrenthttpdownloader

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRoute : NavKey {
    @Serializable
    data object Downloads : NavRoute

    @Serializable
    data object RssFeeds : NavRoute

    @Serializable
    data class RssFeedDetail(val feedId: String) : NavRoute

    @Serializable
    data object Settings : NavRoute

    @Serializable
    data object LogViewer : NavRoute
}
