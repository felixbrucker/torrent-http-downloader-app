package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.util.PathFactory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Test

class PathFactoryTest {
    @Test
    fun testCacheDirectories() {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"))
        val factory = PathFactory(context)

        assertNotNull(factory.getResumeDataDirectory())
        assertNotNull(factory.getResumeDataPath("p-1"))
        assertNotNull(factory.getTemporaryTorrentFileDirectory())
    }
}
