package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.TorrentProviderType
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
    }

    @Test
    fun testAppSettingsPreferencesDefaultValues() {
        val prefs = AppSettingsPreferences()

        assertEquals(TorrentProviderType.REAL_DEBRID, prefs.selectedProvider)
        assertEquals("", prefs.realDebridApiToken)
        assertEquals("", prefs.defaultDestinationSubdirectory)
        assertEquals(1, prefs.rssCheckIntervalHours)
        assertTrue(prefs.notifyOnCompletion)
        assertEquals(FileSelectionMode.ALL, prefs.fileSelectionMode)
        assertTrue(prefs.autoExtractArchives)
        assertFalse(prefs.deleteArchivesAfterExtraction)
    }

    @Test
    fun testAppSettingsPreferencesCustomValues() = runTest {
        val prefsFlow = flowOf(
            AppSettingsPreferences(
                selectedProvider = TorrentProviderType.LIBTORRENT,
                realDebridApiToken = "my_api_token",
                defaultDestinationSubdirectory = "Downloads/Sub",
                rssCheckIntervalHours = 4,
                notifyOnCompletion = false,
                fileSelectionMode = FileSelectionMode.BIGGEST,
                autoExtractArchives = false,
                deleteArchivesAfterExtraction = true
            )
        )

        val result = prefsFlow.first()

        assertEquals(TorrentProviderType.LIBTORRENT, result.selectedProvider)
        assertEquals("my_api_token", result.realDebridApiToken)
        assertEquals("Downloads/Sub", result.defaultDestinationSubdirectory)
        assertEquals(4, result.rssCheckIntervalHours)
        assertFalse(result.notifyOnCompletion)
        assertEquals(FileSelectionMode.BIGGEST, result.fileSelectionMode)
        assertFalse(result.autoExtractArchives)
        assertTrue(result.deleteArchivesAfterExtraction)
    }
}
