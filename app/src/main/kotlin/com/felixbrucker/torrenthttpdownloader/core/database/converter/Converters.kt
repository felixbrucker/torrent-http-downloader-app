package com.felixbrucker.torrenthttpdownloader.core.database.converter

import androidx.room.TypeConverter
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType

class Converters {
    @TypeConverter
    fun fromTorrentState(value: TorrentState): String = value.name

    @TypeConverter
    fun toTorrentState(value: String): TorrentState = TorrentState.valueOf(value)

    @TypeConverter
    fun fromLocalDownloadState(value: LocalDownloadState): String = value.name

    @TypeConverter
    fun toLocalDownloadState(value: String): LocalDownloadState = LocalDownloadState.valueOf(value)

    @TypeConverter
    fun fromTorrentType(value: TorrentType): String = value.name

    @TypeConverter
    fun toTorrentType(value: String): TorrentType = TorrentType.valueOf(value)

    @TypeConverter
    fun fromFileSelectionMode(value: FileSelectionMode): String = value.name

    @TypeConverter
    fun toFileSelectionMode(value: String): FileSelectionMode = FileSelectionMode.valueOf(value)
}
