package com.colorstars.shared.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorStarsEngineTest {
    private fun shotAt(glass: List<GlassPiece>, queue: Int = 0): GlassPiece {
        val state = GameState(
            level = 1, columns = 4,
            queues = List(4) { listOf(StarColor.BLUE) }, glass = glass,
        )
        return ColorStarsEngine.takeStar(state, queue).effects
            .filterIsInstance<GameEffect.Projectile>().first().target
    }

    @Test fun targetPriorityPrefersHighestVertexOverLowerNearbyFragment() {
        val upper = GlassPiece(1, 0, 0, StarColor.BLUE)
        val lower = GlassPiece(2, 4, 3, StarColor.BLUE)
        for (queue in 0..3) assertEquals(upper, shotAt(listOf(lower, upper), queue))
    }

    @Test fun targetPriorityUsesActualVertexHeightWithinTheSameRow() {
        val upperVertex = GlassPiece(1, 1, 0, StarColor.BLUE)
        val nearerCenter = GlassPiece(2, 1, 2, StarColor.BLUE)
        assertTrue(upperVertex.topY < nearerCenter.topY)
        assertEquals(upperVertex, shotAt(listOf(nearerCenter, upperVertex)))
    }

    @Test fun equalHeightPrefersCenterThenRightRegardlessOfListOrder() {
        val left = GlassPiece(1, 0, 2, StarColor.BLUE)
        val right = GlassPiece(2, 0, 3, StarColor.BLUE)
        val outer = GlassPiece(3, 0, 5, StarColor.BLUE)
        assertEquals(left, shotAt(listOf(outer, left)))
        assertEquals(right, shotAt(listOf(left, outer, right)))
        assertEquals(right, shotAt(listOf(right, outer, left)))
    }

    @Test fun highestFragmentMustBeReachableAndMatchTheStarColor() {
        val blocked = GlassPiece(1, 1, 2, StarColor.BLUE)
        val reachable = GlassPiece(2, 3, 4, StarColor.BLUE)
        val glass = listOf(blocked, reachable,
            GlassPiece(3, 0, 2, StarColor.GOLD), GlassPiece(4, 2, 2, StarColor.GOLD),
            GlassPiece(5, 1, 1, StarColor.GOLD), GlassPiece(6, 1, 3, StarColor.GOLD))
        assertEquals(reachable, shotAt(glass))
    }

    @Test fun storedStarsUseTheSameTargetPriority() {
        val upper = GlassPiece(1, 0, 0, StarColor.BLUE)
        val lower = GlassPiece(2, 4, 3, StarColor.BLUE)
        val trigger = GlassPiece(3, 4, 0, StarColor.GREEN)
        val state = GameState(level = 1, columns = 1,
            queues = listOf(listOf(StarColor.GREEN)), glass = listOf(lower, trigger, upper),
            storage = listOf(StarColor.BLUE))
        val effects = ColorStarsEngine.takeStar(state, 0).effects.filterIsInstance<GameEffect.Projectile>()
        assertEquals(upper, effects.last().target)
        assertTrue(effects.last().origin is StarOrigin.Storage)
    }

    @Test fun levelLayoutSupportsFourAndFiveQueues() {
        assertEquals(4, ColorStarsEngine.newGame(1).columns)
        assertEquals(5, ColorStarsEngine.newGame(2).columns)
        assertTrue(ColorStarsEngine.newGame(2).queues.all { it.size >= 4 })
    }

    @Test fun generatedLevelsHaveAWinningSequence() {
        for (level in 1..5) {
            var state = ColorStarsEngine.newGame(level)
            var moves = 0
            while (state.status == GameStatus.PLAYING && moves < 100) {
                state = ColorStarsEngine.takeStar(state, moves % state.columns).state
                moves++
            }
            assertEquals(GameStatus.WON, state.status, "Level $level must be solvable")
            assertTrue(state.glass.isEmpty())
        }
    }

    @Test fun extraStorageStopsAtSixSlots() {
        val initial = ColorStarsEngine.newGame()
        val first = ColorStarsEngine.addSlot(initial).state
        val second = ColorStarsEngine.addSlot(first).state
        assertEquals(6, second.slotCapacity)
        assertEquals(2, second.extraSlotsUsed)
        assertEquals(0, second.boosters.extraSlot)
        assertEquals(second, ColorStarsEngine.addSlot(second).state)
    }

    @Test fun pickBoosterOnlyChargesForConfirmedSelection() {
        val initial = ColorStarsEngine.newGame(2)
        assertFalse(ColorStarsEngine.isSelectable(initial, 0, 2))
        assertEquals(initial, ColorStarsEngine.takeStar(initial, 0, 2).state)
        val selecting = ColorStarsEngine.toggleMode(initial, SelectionMode.PICK_STAR)
        assertTrue(ColorStarsEngine.isSelectable(selecting, 0, 2))
        val result = ColorStarsEngine.takeStar(selecting, 0, 2)
        assertEquals(initial.boosters.pickStar - 1, result.state.boosters.pickStar)
        assertEquals(initial.queues[0].size - 1, result.state.queues[0].size)
        assertEquals(SelectionMode.NONE, result.state.mode)
    }

    @Test fun shatterCanTargetAnyTile() {
        val initial = ColorStarsEngine.newGame(2)
        val selecting = ColorStarsEngine.toggleMode(initial, SelectionMode.SHATTER_GLASS)
        val tile = initial.glass.first { !ColorStarsEngine.isExposed(it, initial.glass) }
        val result = ColorStarsEngine.shatter(selecting, tile.id)
        assertTrue(result.state.glass.none { it.id == tile.id })
        assertEquals(initial.boosters.shatter - 1, result.state.boosters.shatter)
    }

    @Test fun storageAutoLaunchesWhenTargetBecomesAvailable() {
        val blue = GlassPiece(1, 2, 2, StarColor.BLUE)
        val green = GlassPiece(2, 2, 3, StarColor.GREEN)
        val state = GameState(
            level = 1, columns = 4, queues = listOf(listOf(StarColor.BLUE), emptyList(), emptyList(), emptyList()),
            glass = listOf(blue, green), storage = listOf(StarColor.GREEN),
        )
        val result = ColorStarsEngine.takeStar(state, 0)
        assertEquals(GameStatus.WON, result.state.status)
        assertTrue(result.state.storage.isEmpty())
        assertEquals(2, result.effects.filterIsInstance<GameEffect.Projectile>().size)
    }

    @Test fun fullStorageTriggersLossAndContinueClearsTwo() {
        val state = GameState(
            level = 1, columns = 4,
            queues = listOf(listOf(StarColor.PINK), emptyList(), emptyList(), emptyList()),
            glass = listOf(GlassPiece(1, 2, 2, StarColor.BLUE)),
            storage = List(4) { StarColor.PINK },
        )
        val lost = ColorStarsEngine.takeStar(state, 0).state
        assertEquals(GameStatus.LOST, lost.status)
        val resumed = ColorStarsEngine.continueLevel(lost).state
        assertEquals(GameStatus.PLAYING, resumed.status)
        assertEquals(2, resumed.storage.size)
        assertEquals(state.coins - ColorStarsEngine.CONTINUE_COST, resumed.coins)
    }

    @Test fun topApproachCanReachEveryExposedFragmentWithoutCrossingOccupiedCells() {
        val level = ColorStarsEngine.newGame(2)
        val occupied = level.glass.map { GridPoint(it.column, it.row) }.toSet()
        for (target in level.glass.filter { ColorStarsEngine.isExposed(it, level.glass) }) {
            for (entry in 2..3) {
                val route = ProjectileRouter.route(level.glass, target, entry, fromTop = true)
                assertEquals(GridPoint(entry, -1), route.first())
                assertTrue(route.none { it in occupied })
                val last = route.last()
                assertEquals(1, kotlin.math.abs(last.row - target.row) + kotlin.math.abs(last.column - target.column))
            }
        }
    }

    @Test fun projectilesMoveOnlyThroughEmptyCells() {
        val level = ColorStarsEngine.newGame(2)
        val target = level.glass.first { ColorStarsEngine.isExposed(it, level.glass) }
        val route = ProjectileRouter.route(level.glass, target, 3)
        assertTrue(route.isNotEmpty())
        val occupied = level.glass.map { GridPoint(it.column, it.row) }.toSet()
        assertTrue(route.none { it in occupied })
        val last = route.last()
        assertEquals(1, kotlin.math.abs(last.row - target.row) + kotlin.math.abs(last.column - target.column))
    }
}
