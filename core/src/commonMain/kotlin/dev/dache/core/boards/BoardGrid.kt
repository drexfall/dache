package dev.dache.core.boards

/** Where one widget sits on the grid: its top-left cell and how many columns and rows it covers. */
data class Placement(
    val column: Int,
    val row: Int,
    val span: Int,
    val height: Int,
) {
    fun contains(
        column: Int,
        row: Int,
    ): Boolean = column in this.column until this.column + span && row in this.row until this.row + height
}

/**
 * Places widgets on a grid of a given number of columns. Each widget, in reading order, takes the first free spot
 * from the top-left where it fits, so a short widget can fill a gap beside a tall one. The board and its editor
 * share this, so the editor shows exactly what the board will.
 */
object BoardGrid {
    /** One placement per widget, in the same order. A span wider than [columns] is narrowed to fit. */
    fun place(
        widgets: List<Widget>,
        columns: Int,
    ): List<Placement> {
        require(columns > 0) { "a grid needs at least one column" }
        val taken = mutableListOf<BooleanArray>()

        fun free(
            column: Int,
            row: Int,
            span: Int,
            height: Int,
        ): Boolean =
            (row until row + height).all { r ->
                val cells = taken.getOrNull(r) ?: return@all true
                (column until column + span).none { cells[it] }
            }

        return widgets.map { widget ->
            val span = widget.span.coerceIn(1, columns)
            val height = widget.effectiveHeight
            var row = 0
            var placed: Placement? = null
            while (placed == null) {
                val column = (0..columns - span).firstOrNull { free(it, row, span, height) }
                if (column != null) placed = Placement(column, row, span, height) else row++
            }
            while (taken.size < placed.row + height) taken += BooleanArray(columns)
            for (r in placed.row until placed.row + height) {
                for (c in placed.column until placed.column + span) taken[r][c] = true
            }
            placed
        }
    }

    /** How many rows [placements] cover. */
    fun rows(placements: List<Placement>): Int = placements.maxOfOrNull { it.row + it.height } ?: 0

    /** The index of the placement covering a cell, or null when the cell is empty. */
    fun hit(
        placements: List<Placement>,
        column: Int,
        row: Int,
    ): Int? = placements.indexOfFirst { it.contains(column, row) }.takeIf { it >= 0 }

    /**
     * Where a widget dropped on an empty cell goes in reading order: after every widget that starts before the
     * cell, so dropping below the last widget adds at the end and dropping in a gap lands between its neighbours.
     */
    fun insertionIndex(
        placements: List<Placement>,
        column: Int,
        row: Int,
    ): Int = placements.count { it.row < row || (it.row == row && it.column < column) }
}
