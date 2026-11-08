package com.colorstars.shared.game

import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectileFlightTest {
    private fun assertClear(path: List<GlassVertex>, glass: List<GlassPiece>) {
        assertTrue(path.size >= 2, "Reachable fragments must have a flight path")
        for (i in 1 until path.lastIndex) {
            val dx1 = path[i].x - path[i - 1].x
            val dy1 = path[i].y - path[i - 1].y
            val dx2 = path[i + 1].x - path[i].x
            val dy2 = path[i + 1].y - path[i].y
            val lengths = kotlin.math.hypot(dx1, dy1) * kotlin.math.hypot(dx2, dy2)
            if (lengths > .000001f) assertTrue((dx1 * dx2 + dy1 * dy2) / lengths > .95f,
                "Flight must not change direction abruptly")
        }
        for ((a, b) in path.zipWithNext()) {
            for (step in 0..40) {
                val t = step / 40f
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                for (piece in glass) {
                    val vertices = listOf(glassCorner(piece.column, piece.row), glassCorner(piece.column + 1, piece.row),
                        glassCorner(piece.column + 1, piece.row + 1), glassCorner(piece.column, piece.row + 1))
                    assertTrue(x <= vertices.minOf { it.x } - .179f || x >= vertices.maxOf { it.x } + .179f ||
                        y <= vertices.minOf { it.y } - .179f || y >= vertices.maxOf { it.y } + .179f,
                        "Flight core intersects piece ${piece.id} at $x, $y")
                }
            }
        }
    }

    @Test fun flightAvoidsGlassForAllExposedTargetsAndLaunchColumns() {
        val glass = ColorStarsEngine.newGame().glass
        for (target in glass.filter { ColorStarsEngine.isExposed(it, glass) }) {
            for (column in 0..5) {
                assertClear(ProjectileFlight.route(glass, target, GlassVertex(column + .5f, 7f)), glass)
            }
        }
    }

    @Test fun flightFollowsNarrowBentPassage() {
        val corridor = setOf(2 to 4, 2 to 3, 1 to 3, 1 to 2, 1 to 1, 2 to 1, 3 to 1)
        val glass = buildList {
            for (row in 0..4) for (column in 0..5) {
                if (column to row !in corridor) add(GlassPiece(row * 6 + column, row, column, StarColor.BLUE))
            }
        }
        val target = glass.first { it.column == 4 && it.row == 1 }
        val path = ProjectileFlight.route(glass, target, GlassVertex(2.5f, 7f))
        assertClear(path, glass)
        assertTrue(path.any { it.x < 2f && it.y in 2f..4f }, "Must use the bend in the passage")
        assertTrue(path.last().x in 3f..4f && path.last().y == 1.5f)
    }

    @Test fun enclosedTargetDoesNotProduceAPathThroughGlass() {
        val glass = buildList {
            for (row in 0..4) for (column in 0..5) add(GlassPiece(row * 6 + column, row, column, StarColor.BLUE))
        }
        assertTrue(ProjectileFlight.route(glass, glass.first { it.row == 2 && it.column == 2 }, GlassVertex(3f, 7f)).isEmpty())
    }
}
