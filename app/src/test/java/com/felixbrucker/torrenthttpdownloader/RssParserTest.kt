package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.network.RssParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RssParserTest {

    private val client = mockk<OkHttpClient>()
    private val factory = mockk<XmlPullParserFactory>()
    private val parser = mockk<XmlPullParser>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(XmlPullParserFactory::class)
        every { XmlPullParserFactory.newInstance() } returns factory
        every { factory.newPullParser() } returns parser
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `parse iterates through xml tokens and strips html from description`() {
        val rssParser = RssParser(client)

        var state = 0
        every { parser.eventType } answers {
            when (state) {
                0 -> XmlPullParser.START_DOCUMENT
                1 -> XmlPullParser.START_TAG
                2 -> XmlPullParser.START_TAG
                3 -> XmlPullParser.END_TAG
                4 -> XmlPullParser.END_TAG
                else -> XmlPullParser.END_DOCUMENT
            }
        }
        every { parser.name } answers {
            when (state) {
                1 -> "item"
                2 -> "description"
                3 -> "description"
                4 -> "item"
                else -> ""
            }
        }
        every { parser.nextText() } returns "<b>HTML Description</b>"
        every { parser.next() } answers {
            state++
            if (state > 5) XmlPullParser.END_DOCUMENT else parser.eventType
        }

        val items = rssParser.parse("<xml></xml>")

        assertEquals(1, items.size)
        assertEquals("HTML Description", items[0].description)
    }

    @Test
    fun `parse extracts item title and link correctly`() {
        val rssParser = RssParser(client)

        var state = 0
        every { parser.eventType } answers {
            when (state) {
                0 -> XmlPullParser.START_DOCUMENT
                1 -> XmlPullParser.START_TAG
                2 -> XmlPullParser.START_TAG
                3 -> XmlPullParser.END_TAG
                4 -> XmlPullParser.START_TAG
                5 -> XmlPullParser.END_TAG
                6 -> XmlPullParser.END_TAG
                else -> XmlPullParser.END_DOCUMENT
            }
        }
        every { parser.name } answers {
            when (state) {
                1 -> "item"
                2 -> "title"
                3 -> "title"
                4 -> "link"
                5 -> "link"
                6 -> "item"
                else -> ""
            }
        }
        every { parser.nextText() } answers {
            when (state) {
                2 -> "Sample Title"
                4 -> "https://example.com/rss"
                else -> ""
            }
        }
        every { parser.next() } answers {
            state++
            if (state > 7) XmlPullParser.END_DOCUMENT else parser.eventType
        }

        val items = rssParser.parse("<xml></xml>")

        assertEquals(1, items.size)
        assertEquals("Sample Title", items[0].title)
        assertEquals("https://example.com/rss", items[0].link)
    }
}
