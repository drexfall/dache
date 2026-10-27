package dev.dache.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ValueFormatsTest {
    private val formats =
        ValueFormats(
            msTemplate = "%1\$s ms",
            secondsTemplate = "%1\$ss",
            minutesSecondsTemplate = "%1\$sm %2\$ss",
        )

    @Test
    fun moneyPutsTheSignBeforeTheSymbol() {
        assertEquals("$43.17", formats.money(43.17, "USD"))
        assertEquals("-$43.17", formats.money(-43.17, "USD"))
        assertEquals("-12.50 EUR", formats.money(-12.5, "EUR"))
    }

    @Test
    fun durationsSwitchToMinutesAtSixty() {
        assertEquals("59s", formats.duration(59.0))
        assertEquals("1m 14s", formats.duration(74.0))
    }

    @Test
    fun millisecondsRoundToWholeNumbers() {
        assertEquals("161 ms", formats.milliseconds(160.7))
        assertEquals("1.2K ms", formats.milliseconds(1_240.0))
    }
}
