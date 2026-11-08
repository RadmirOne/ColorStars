package com.colorstars.shared.game

import kotlin.math.hypot
import kotlin.math.min

/** Collision-aware flight in board coordinates. The halo is decorative; the core has clearance. */
object ProjectileFlight {
    private const val CLEARANCE = .18f

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun bounds(piece: GlassPiece, boardRows: Int, boardColumns: Int): Bounds {
        val corners = listOf(glassCorner(piece.column, piece.row, boardColumns, boardRows),
            glassCorner(piece.column + 1, piece.row, boardColumns, boardRows),
            glassCorner(piece.column + 1, piece.row + 1, boardColumns, boardRows),
            glassCorner(piece.column, piece.row + 1, boardColumns, boardRows))
        return Bounds(corners.minOf { it.x } - CLEARANCE, corners.minOf { it.y } - CLEARANCE,
            corners.maxOf { it.x } + CLEARANCE, corners.maxOf { it.y } + CLEARANCE)
    }

    private fun mix(a: GlassVertex, b: GlassVertex, t: Float) =
        GlassVertex(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    private fun distance(a: GlassVertex, b: GlassVertex) = hypot(b.x - a.x, b.y - a.y)

    // Slab intersection checks the entire segment, including between animation samples.
    private fun clear(a: GlassVertex, b: GlassVertex, obstacles: List<Bounds>): Boolean = obstacles.none { box ->
        var enter = 0f
        var leave = 1f
        fun axis(start: Float, delta: Float, low: Float, high: Float): Boolean {
            if (kotlin.math.abs(delta) < .00001f) return start > low && start < high
            val t1 = (low - start) / delta
            val t2 = (high - start) / delta
            enter = maxOf(enter, minOf(t1, t2))
            leave = minOf(leave, maxOf(t1, t2))
            return enter < leave
        }
        axis(a.x, b.x - a.x, box.left, box.right) &&
            axis(a.y, b.y - a.y, box.top, box.bottom) && enter < leave
    }

    fun route(
        glass: List<GlassPiece>, target: GlassPiece, start: GlassVertex,
        boardRows: Int = ColorStarsEngine.BOARD_ROWS,
        boardColumns: Int = ColorStarsEngine.BOARD_COLUMNS,
    ): List<GlassVertex> {
        val cells = ProjectileRouter.route(glass, target, start.x.toInt(), boardRows = boardRows, boardColumns = boardColumns)
        if (cells.isEmpty()) return emptyList()
        val obstacles = glass.map { bounds(it, boardRows, boardColumns) }
        val shortcutObstacles = obstacles.map { Bounds(it.left - .06f, it.top - .06f, it.right + .06f, it.bottom + .06f) }
        val edge = cells.last()
        val targetBounds = bounds(target, boardRows, boardColumns)
        // Stop just outside the target's expanded boundary: the core touches the glass.
        val impact = when {
            edge.column < target.column -> GlassVertex(targetBounds.left - .001f, target.row + .5f)
            edge.column > target.column -> GlassVertex(targetBounds.right + .001f, target.row + .5f)
            edge.row < target.row -> GlassVertex(target.column + .5f, targetBounds.top - .001f)
            else -> GlassVertex(target.column + .5f, targetBounds.bottom + .001f)
        }
        val raw = listOf(start) + cells.map { GlassVertex(it.column + .5f, it.row + .5f) } + impact
        if (raw.zipWithNext().any { (a, b) -> !clear(a, b, obstacles) }) return emptyList()

        // Remove unnecessary grid steps only when the shortcut has full clearance.
        val simple = mutableListOf(raw.first())
        var index = 0
        while (index < raw.lastIndex) {
            var next = raw.lastIndex
            while (next > index + 1 && !clear(raw[index], raw[next], shortcutObstacles)) next--
            simple += raw[next]
            index = next
        }

        val result = mutableListOf(simple.first())
        for (i in 1 until simple.lastIndex) {
            val previous = simple[i - 1]
            val corner = simple[i]
            val next = simple[i + 1]
            val incoming = distance(previous, corner)
            val outgoing = distance(corner, next)
            var radius = min(.8f, min(incoming, outgoing) * .45f)
            var rounded: List<GlassVertex>? = null
            repeat(16) {
                if (rounded == null) {
                    val entry = mix(corner, previous, radius / incoming)
                    val exit = mix(corner, next, radius / outgoing)
                    val candidate = List(25) { step ->
                        val t = step / 24f
                        mix(mix(entry, corner, t), mix(corner, exit, t), t)
                    }
                    if (candidate.zipWithNext().all { (a, b) -> clear(a, b, obstacles) }) rounded = candidate
                    else radius *= .5f
                }
            }
            result.addAll(rounded ?: listOf(corner))
        }
        result += simple.last()
        return result
    }
}
