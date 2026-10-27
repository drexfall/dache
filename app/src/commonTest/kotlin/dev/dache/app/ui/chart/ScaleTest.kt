package dev.dache.app.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleTest {
    @Test
    fun ticksUseCleanSteps() {
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0), niceScale(7.0).ticks)
        assertEquals(listOf(0.0, 200.0, 400.0, 600.0, 800.0), niceScale(730.0).ticks)
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5, 2.0), niceScale(1.9).ticks)
    }

    @Test
    fun maxCoversTheData() {
        listOf(1.0, 3.0, 9.0, 11.0, 99.0, 101.0, 2_345.0).forEach { value ->
            val scale = niceScale(value)
            assertTrue(scale.max >= value, "scale for $value tops out at ${scale.max}")
            assertEquals(0.0, scale.ticks.first())
            assertEquals(scale.max, scale.ticks.last())
        }
    }

    @Test
    fun emptyDataStillHasAnAxis() {
        assertEquals(AxisScale(1.0, listOf(0.0, 1.0)), niceScale(0.0))
    }

    @Test
    fun formatsDecimalsWithoutPlatformFormatters() {
        assertEquals("1.50", formatDecimal(1.5, 2))
        assertEquals("43.17", formatDecimal(43.17, 2))
        assertEquals("-43.17", formatDecimal(-43.17, 2))
        assertEquals("100", formatDecimal(99.996, 0))
        assertEquals("0.05", formatDecimal(0.049, 2))
    }

    @Test
    fun compactsLargeNumbers() {
        assertEquals("0", compactNumber(0.0))
        assertEquals("250", compactNumber(250.0))
        assertEquals("1.5", compactNumber(1.5))
        assertEquals("1.2K", compactNumber(1_240.0))
        assertEquals("2K", compactNumber(2_000.0))
        assertEquals("3M", compactNumber(3_000_000.0))
    }
}
