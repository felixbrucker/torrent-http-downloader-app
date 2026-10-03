package com.felixbrucker.torrenthttpdownloader.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsRepositoryTest {
    private val dataStore = mockk<DataStore<Preferences>>()

    @Test
    fun testDefaultValuesWhenEmpty() = runTest {
        coEvery { dataStore.data } returns flowOf(preferencesOf())
        val repository = AppSettingsRepository(dataStore)

        val settings = repository.getSettings()

        assertEquals("libtorrent", settings.selectedProvider)
        assertEquals("", settings.realDebridApiToken)
        assertEquals(2, settings.localParallelDownloads)
        assertEquals(3, settings.libTorrentParallelDownloads)
        assertEquals(false, settings.libTorrentRequireVpnConnection)
        assertEquals(true, settings.rssSyncEnabled)
        assertEquals(3, settings.rssSyncIntervalHours)
        assertEquals(null, settings.lastUsedSubDir)
        assertEquals(true, settings.lastUsedCreateSubfolder)
        assertEquals(false, settings.lastUsedNotifyOnCompletion)
        assertEquals(FileSelectionMode.ALL, settings.lastUsedFileSelectionMode)
    }

    @Test
    fun testPreferencesReadFromDataStore() = runTest {
        val prefs = preferencesOf(
            AppSettingsRepository.KEY_PROVIDER to "Real-Debrid",
            AppSettingsRepository.KEY_LAST_USED_SUB_DIR to "Movies",
            AppSettingsRepository.KEY_LAST_USED_CREATE_SUBFOLDER to false,
            AppSettingsRepository.KEY_LAST_USED_NOTIFY_ON_COMPLETION to true,
            AppSettingsRepository.KEY_LAST_USED_FILE_SELECTION_MODE to "BIGGEST"
        )
        coEvery { dataStore.data } returns flowOf(prefs)
        val repository = AppSettingsRepository(dataStore)

        val settings = repository.getSettings()

        assertEquals("Real-Debrid", settings.selectedProvider)
        assertEquals("Movies", settings.lastUsedSubDir)
        assertEquals(false, settings.lastUsedCreateSubfolder)
        assertEquals(true, settings.lastUsedNotifyOnCompletion)
        assertEquals(FileSelectionMode.BIGGEST, settings.lastUsedFileSelectionMode)
    }
}
