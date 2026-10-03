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
    fun testLegacyFallbackKeys() = runTest {
        val prefs = preferencesOf(
            AppSettingsRepository.KEY_DEFAULT_SUB_DIR_LEGACY to "LegacyFolder",
            AppSettingsRepository.KEY_DEFAULT_CREATE_SUBFOLDER_LEGACY to false,
            AppSettingsRepository.KEY_DEFAULT_NOTIFY_ON_COMPLETION_LEGACY to true,
            AppSettingsRepository.KEY_DEFAULT_FILE_SELECTION_MODE_LEGACY to "BIGGEST"
        )
        coEvery { dataStore.data } returns flowOf(prefs)
        val repository = AppSettingsRepository(dataStore)

        val settings = repository.getSettings()

        assertEquals("LegacyFolder", settings.lastUsedSubDir)
        assertEquals(false, settings.lastUsedCreateSubfolder)
        assertEquals(true, settings.lastUsedNotifyOnCompletion)
        assertEquals(FileSelectionMode.BIGGEST, settings.lastUsedFileSelectionMode)
    }
}
