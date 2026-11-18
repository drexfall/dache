package dev.dache.core.boards

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardGridTest {
    private fun widget(
        id: String,
        span: Int = 1,
        height: Int = 1,
    ) = Widget(id, Datapoint.DEPLOYS, WidgetChart.STAT, span = span, height = height)

    @Test
    fun widgetsFillRowsInReadingOrder() {
        val placements = BoardGrid.place(listOf(widget("a", span = 2), widget("b"), widget("c"), widget("d")), 4)

        assertEquals(
            listOf(Placement(0, 0, 2, 1), Placement(2, 0, 1, 1), Placement(3, 0, 1, 1), Placement(0, 1, 1, 1)),
            placements,
        )
        assertEquals(2, BoardGrid.rows(placements))
    }

    @Test
    fun aShortWidgetFillsTheGapBesideATallOne() {
        val placements =
            BoardGrid.place(
                listOf(
                    widget("tall", span = 2, height = 2),
                    widget("wide", span = 2),
                    widget("small"),
                    widget("small2"),
                ),
                4,
            )

        assertEquals(Placement(0, 0, 2, 2), placements[0])
        assertEquals(Placement(2, 0, 2, 1), placements[1])
        assertEquals(Placement(2, 1, 1, 1), placements[2], "under the wide one, beside the tall one")
        assertEquals(Placement(3, 1, 1, 1), placements[3])
    }

    @Test
    fun aWidgetTooWideForTheGridIsNarrowed() {
        val placements = BoardGrid.place(listOf(widget("a", span = 4), widget("b", span = 2)), 2)

        assertEquals(listOf(Placement(0, 0, 2, 1), Placement(0, 1, 2, 1)), placements)
        assertEquals(listOf(0, 1), BoardGrid.place(listOf(widget("a", span = 3), widget("b")), 1).map { it.row })
    }

    @Test
    fun hitsAndDropsFollowTheLayout() {
        val placements = BoardGrid.place(listOf(widget("a", span = 2, height = 2), widget("b"), widget("c")), 4)

        assertEquals(0, BoardGrid.hit(placements, 1, 1), "inside the tall widget's second row")
        assertEquals(2, BoardGrid.hit(placements, 3, 0))
        assertNull(BoardGrid.hit(placements, 2, 1))
        assertEquals(3, BoardGrid.insertionIndex(placements, 2, 1), "in the gap: after everything above it")
        assertEquals(1, BoardGrid.insertionIndex(placements, 2, 0), "between a and c on the first row")
        assertEquals(0, BoardGrid.insertionIndex(emptyList(), 3, 5))
    }

    @Test
    fun heightDefaultsToTheChartsUntilChosen() {
        assertEquals(1, Widget("w", Datapoint.DEPLOYS, WidgetChart.STAT).effectiveHeight)
        assertEquals(2, Widget("w", Datapoint.DEPLOYS, WidgetChart.BARS).effectiveHeight)
        assertEquals(3, Widget("w", Datapoint.SITES, WidgetChart.LIST).effectiveHeight)
        assertEquals(5, Widget("w", Datapoint.SITES, WidgetChart.LIST, height = 5).effectiveHeight)
        assertEquals(Widget.MAX_HEIGHT, Widget("w", Datapoint.SITES, height = 40).normalized().height)
        assertEquals(1, Widget("w", Datapoint.SITES, height = 0).normalized().height)
    }

    @Test
    fun boardsSavedBeforeHeightsStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val board =
            json.decodeFromString<Board>(
                """{"id":"b","name":"Ops","profileId":"default","widgets":[{"id":"w","datapoint":"SITES","span":2}]}""",
            )

        val widget = board.widgets.single()
        assertNull(widget.height)
        assertEquals(WidgetChart.STAT, widget.chart)
        assertEquals(widget, json.decodeFromString<Board>(json.encodeToString(board)).widgets.single())
    }
}
