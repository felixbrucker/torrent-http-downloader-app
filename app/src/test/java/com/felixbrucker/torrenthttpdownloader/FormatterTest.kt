package com.felixbrucker.torrenthttpdownloader

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatterTest {

    @Test
    fun `formatBytes returns 0 B when bytes is zero or negative`() {
        val bytesZero = 0L
        val bytesNegative = -500L

        val resultZero = Formatter.formatBytes(bytesZero)
        val resultNegative = Formatter.formatBytes(bytesNegative)

        assertEquals("0 B", resultZero)
        assertEquals("0 B", resultNegative)
    }

    @Test
    fun `formatBytes formats bytes in B range`() {
        val bytes = 500L

        val result = Formatter.formatBytes(bytes)

        assertEquals("500 B", result)
    }

    @Test
    fun `formatBytes formats bytes in KB range`() {
        val bytes = 2048L

        val result = Formatter.formatBytes(bytes)

        assertEquals("2 KB", result)
    }

    @Test
    fun `formatBytes formats bytes in MB range`() {
        val bytes = 1572864L

        val result = Formatter.formatBytes(bytes)

        assertEquals("1.50 MB", result)
    }

    @Test
    fun `formatBytes formats bytes in GB range`() {
        val bytes = 10737418240L

        val result = Formatter.formatBytes(bytes)

        assertEquals("10.00 GB", result)
    }

    @Test
    fun `formatBytes formats bytes in TB range`() {
        val bytes = 2199023255552L

        val result = Formatter.formatBytes(bytes)

        assertEquals("2.00 TB", result)
    }

    @Test
    fun `formatBytes handles max long value without overflow`() {
        val bytes = Long.MAX_VALUE

        val result = Formatter.formatBytes(bytes)

        assertEquals("8388608.00 TB", result)
    }

    @Test
    fun `formatSpeed appends per second suffix`() {
        val speed = 10485760L

        val result = Formatter.formatSpeed(speed)

        assertEquals("10.00 MB/s", result)
    }

    @Test
    fun `formatTime formats seconds into days hours minutes and seconds`() {
        val secondsDays = 90061L
        val secondsHours = 3661L
        val secondsMinutes = 125L
        val secondsOnly = 45L

        val resultDays = Formatter.formatTime(secondsDays)
        val resultHours = Formatter.formatTime(secondsHours)
        val resultMinutes = Formatter.formatTime(secondsMinutes)
        val resultOnly = Formatter.formatTime(secondsOnly)

        assertEquals("1d 1h 1m 1s", resultDays)
        assertEquals("1h 1m 1s", resultHours)
        assertEquals("2m 5s", resultMinutes)
        assertEquals("45s", resultOnly)
    }
}
