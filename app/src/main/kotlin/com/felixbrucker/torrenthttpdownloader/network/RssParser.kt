package com.felixbrucker.torrenthttpdownloader.network

import com.felixbrucker.torrenthttpdownloader.models.RssItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

class RssParser(private val client: OkHttpClient) {
    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    )

    suspend fun fetchAndParse(url: String): List<RssItem> {
        val request = Request.Builder().url(url).build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val xml = response.body.string()

                return@use parse(xml)
            }
        }
    }

    fun parse(xml: String): List<RssItem> {
        try {
            val items = mutableListOf<RssItem>()
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentItemTitle = ""
            var currentItemLink = ""
            var currentItemDescription = ""
            var currentItemPubDate: Long? = null
            var insideItem = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "item") {
                            insideItem = true
                        } else if (insideItem) {
                            when (tagName) {
                                "title" -> currentItemTitle = try { parser.nextText() } catch (e: Exception) { "" }
                                "link" -> currentItemLink = try { parser.nextText() } catch (e: Exception) { "" }
                                "description" -> currentItemDescription = try { stripHtml(parser.nextText()) } catch (e: Exception) { "" }
                                "pubDate" -> currentItemPubDate = try { parseDate(parser.nextText()) } catch (e: Exception) { null }
                                "enclosure" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (url != null && (url.endsWith(".torrent") || url.startsWith("magnet:"))) {
                                        currentItemLink = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "item") {
                            val id = currentItemLink.ifEmpty { currentItemTitle }
                            items.add(
                                RssItem(
                                    id = id,
                                    title = currentItemTitle,
                                    link = currentItemLink,
                                    description = currentItemDescription,
                                    pubDate = currentItemPubDate
                                )
                            )
                            currentItemTitle = ""
                            currentItemLink = ""
                            currentItemDescription = ""
                            currentItemPubDate = null
                            insideItem = false
                        }
                    }
                }
                eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
            }
            return items
        } catch (e: Exception) {
            throw RuntimeException("XML Parsing failed: ${e.message}", e)
        }
    }

    private fun parseDate(dateString: String): Long? {
        for (format in dateFormats) {
            try {
                return format.parse(dateString)?.time
            } catch (e: Exception) {
                // Ignore and try next format
            }
        }
        return null
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
    }
}
