package com.colorstars.shared.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimatedMoveTest {
    @Test fun rapidSelectionsReserveDifferentTargetsAndKeepBothFlights() {
        val initial = GameState(level = 1, columns = 1,
            queues = listOf(List(3) { StarColor.BLUE }),
            glass = List(3) { GlassPiece(it, 0, it, StarColor.BLUE) })
        val first = ColorStarsEngine.takeStar(initial, 0)
        val second = ColorStarsEngine.takeStar(first.state, 0)
        val flights = prepareMoveEffects(initial, first, 0) + prepareMoveEffects(first.state, second, 1)
        val targets = flights.map { (it.effect as GameEffect.Projectile).target.id }
        assertEquals(2, targets.toSet().size)
        assertEquals(1, second.state.queues.single().size)
        assertEquals(initial.glass.toSet(), visibleGlass(second.state, flights).toSet())
        assertTrue(flights.all { it.predecessor == null }, "Independent clicks must launch concurrently")
        // Finishing the second flight first removes only its visual target.
        val afterSecond = visibleGlass(second.state, flights.filterNot { it.id == 1L })
        assertFalse(afterSecond.any { it.id == targets[1] })
        assertTrue(afterSecond.any { it.id == targets[0] })
        assertEquals(3, flights[0].source.queues.single().size)
        assertEquals(2, flights[1].source.queues.single().size)
    }

    @Test fun storedChainUsesItsOwnPredecessorAndUpdatedSlotOrigins() {
        val initial = GameState(level = 1, columns = 1,
            queues = listOf(listOf(StarColor.GREEN)),
            glass = listOf(GlassPiece(0, 0, 0, StarColor.GREEN),
                GlassPiece(1, 0, 2, StarColor.BLUE), GlassPiece(2, 0, 4, StarColor.PINK)),
            storage = listOf(StarColor.BLUE, StarColor.PINK))
        val move = ColorStarsEngine.takeStar(initial, 0)
        val flights = prepareMoveEffects(initial, move, 10)
        assertEquals(3, flights.size)
        assertNull(flights[0].predecessor)
        assertEquals(10L, flights[1].predecessor)
        assertEquals(11L, flights[2].predecessor)
        assertEquals(listOf(StarColor.PINK), flights[2].source.storage)
        assertEquals(StarOrigin.Storage(0), (flights[2].effect as GameEffect.Projectile).origin)
        assertEquals(initial.glass.toSet(), visibleGlass(move.state, flights).toSet())
        assertTrue(visibleGlass(move.state, emptyList()).isEmpty())
    }
}
