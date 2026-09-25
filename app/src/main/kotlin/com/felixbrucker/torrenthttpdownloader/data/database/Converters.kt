package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.TypeConverter
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState

class Converters {
    @TypeConverter
    fun fromTorrentType(type: TorrentType): String = type.name

    @TypeConverter
    fun toTorrentType(type: String): TorrentType = try {
        TorrentType.valueOf(type)
    } catch (_: Exception) {
        TorrentType.MAGNET
    }

    @TypeConverter
    fun fromTorrentState(state: TorrentState): String = state.name

    @TypeConverter
    fun toTorrentState(state: String): TorrentState = try {
        TorrentState.valueOf(state)
    } catch (_: Exception) {
        TorrentState.ERROR
    }

    @TypeConverter
    fun fromLocalDownloadState(state: LocalDownloadState): String = state.name

    @TypeConverter
    fun toLocalDownloadState(state: String): LocalDownloadState = try {
        LocalDownloadState.valueOf(state)
    } catch (_: Exception) {
        LocalDownloadState.PENDING
    }

    @TypeConverter
    fun fromFileSelectionMode(mode: FileSelectionMode): String = mode.name

    @TypeConverter
    fun toFileSelectionMode(mode: String): FileSelectionMode = try {
        FileSelectionMode.valueOf(mode)
    } catch (_: Exception) {
        FileSelectionMode.ALL
    }

    @TypeConverter
    fun fromProviderTorrentState(state: ProviderTorrentState): String = state.name

    @TypeConverter
    fun toProviderTorrentState(state: String): ProviderTorrentState = try {
        ProviderTorrentState.valueOf(state)
    } catch (_: Exception) {
        ProviderTorrentState.UNKNOWN
    }

    @TypeConverter
    fun fromFilePriority(priority: FilePriority): String = priority.name

    @TypeConverter
    fun toFilePriority(priority: String): FilePriority = try {
        FilePriority.valueOf(priority)
    } catch (_: Exception) {
        FilePriority.NORMAL
    }
}
