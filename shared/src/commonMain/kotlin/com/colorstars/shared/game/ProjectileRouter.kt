package com.colorstars.shared.game

/** A route over empty board cells, including a one-cell margin around the mosaic.
 * No occupied tile is crossed. The renderer only draws the moving projectile, never this path.
 */
data class GridPoint(val column: Int, val row: Int)

object ProjectileRouter {
    fun route(
        glass: List<GlassPiece>, target: GlassPiece, entryColumn: Int,
        fromTop: Boolean = false,
        boardRows: Int = ColorStarsEngine.BOARD_ROWS,
        boardColumns: Int = ColorStarsEngine.BOARD_COLUMNS,
    ): List<GridPoint> {
        val occupied = glass.map { GridPoint(it.column, it.row) }.toSet()
        val start = GridPoint(entryColumn.coerceIn(0, boardColumns - 1), if (fromTop) -1 else boardRows)
        val openNeighbors = neighbors(GridPoint(target.column, target.row)).filter { it !in occupied }
        val explored = ArrayDeque<GridPoint>()
        val previous = mutableMapOf<GridPoint, GridPoint?>()
        explored.add(start)
        previous[start] = null
        var reached: GridPoint? = null
        while (explored.isNotEmpty()) {
            val cell = explored.removeFirst()
            if (cell in openNeighbors) {
                reached = cell
                break
            }
            for (candidate in neighbors(cell)) {
                if (candidate.column !in -1..boardColumns ||
                    candidate.row !in -1..boardRows ||
                    candidate in occupied || candidate in previous) continue
                previous[candidate] = cell
                explored.add(candidate)
            }
        }
        if (reached == null) return emptyList()
        val path = mutableListOf<GridPoint>()
        var current: GridPoint? = reached
        while (current != null) {
            path += current
            current = previous[current]
        }
        path.reverse()
        return path
    }

    private fun neighbors(point: GridPoint): List<GridPoint> = listOf(
        GridPoint(point.column, point.row + 1),
        GridPoint(point.column - 1, point.row),
        GridPoint(point.column + 1, point.row),
        GridPoint(point.column, point.row - 1),
    )
}
