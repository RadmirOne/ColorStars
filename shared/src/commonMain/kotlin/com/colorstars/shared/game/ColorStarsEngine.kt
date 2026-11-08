package com.colorstars.shared.game

import kotlin.math.abs

/** The engine deliberately has no Compose or Android dependency. */
enum class StarColor { BLUE, GOLD, GREEN, PINK, PURPLE }

data class GlassPiece(val id: Int, val row: Int, val column: Int, val color: StarColor)

enum class GameStatus { PLAYING, LOST, WON }
enum class SelectionMode { NONE, PICK_STAR, SHATTER_GLASS }

data class Boosters(val extraSlot: Int = 2, val shatter: Int = 1, val pickStar: Int = 3)

data class GameState(
    val level: Int,
    val columns: Int,
    val queues: List<List<StarColor>>,
    val glass: List<GlassPiece>,
    val storage: List<StarColor> = emptyList(),
    val slotCapacity: Int = 4,
    val extraSlotsUsed: Int = 0,
    val boosters: Boosters = Boosters(),
    val coins: Int = 850,
    val status: GameStatus = GameStatus.PLAYING,
    val mode: SelectionMode = SelectionMode.NONE,
)

sealed interface StarOrigin {
    data class Queue(val column: Int, val row: Int) : StarOrigin
    data class Storage(val slot: Int) : StarOrigin
    data object Booster : StarOrigin
}

sealed interface GameEffect {
    data class Projectile(val color: StarColor, val origin: StarOrigin, val target: GlassPiece) : GameEffect
    data class Stored(val color: StarColor, val origin: StarOrigin.Queue) : GameEffect
    data class Break(val target: GlassPiece) : GameEffect
}

data class MoveResult(val state: GameState, val effects: List<GameEffect> = emptyList())

object ColorStarsEngine {
    const val BOARD_ROWS = 5
    const val BOARD_COLUMNS = 6
    const val MAX_VISIBLE_STARS = 4
    const val CONTINUE_COST = 100
    const val BOOSTER_COST = 120

    // A symmetric silhouette with transparent corridors around it.
    private val silhouette = listOf("011110", "111111", "111111", "011110", "001100")

    fun newGame(level: Int = 1, coins: Int = 850, boosters: Boosters = Boosters()): GameState {
        val queueCount = if (level == 1) 4 else 5
        val glass = buildList {
            var id = 0
            silhouette.forEachIndexed { row, mask ->
                mask.forEachIndexed { column, ch ->
                    if (ch == '1') {
                        val color = StarColor.entries[(row * 3 + column * 2 + level + row * column) % StarColor.entries.size]
                        add(GlassPiece(id++, row, column, color))
                    }
                }
            }
        }
        // Generate an achievable destruction order. Splitting it across the queues
        // ensures that playing the heads in round-robin order is a valid solution.
        val remaining = glass.toMutableList()
        val destructionOrder = mutableListOf<StarColor>()
        while (remaining.isNotEmpty()) {
            val exposed = remaining.filter { isExposed(it, remaining) }
            val next = exposed.sortedWith(
                compareByDescending<GlassPiece> { it.row }
                    .thenBy { abs(it.column - ((destructionOrder.size + level) % BOARD_COLUMNS)) }
                    .thenBy { it.id }
            ).first()
            destructionOrder += next.color
            remaining.remove(next)
        }
        val queues = List(queueCount) { index ->
            destructionOrder.filterIndexed { position, _ -> position % queueCount == index }
        }
        return GameState(level = level, columns = queueCount, glass = glass, queues = queues, coins = coins, boosters = boosters)
    }

    fun isExposed(piece: GlassPiece, glass: List<GlassPiece>): Boolean {
        val occupied = glass.map { it.row to it.column }.toSet()
        val hasEmptyNeighbor = listOf(
            piece.row - 1 to piece.column,
            piece.row + 1 to piece.column,
            piece.row to piece.column - 1,
            piece.row to piece.column + 1,
        ).any { it !in occupied }
        return hasEmptyNeighbor && ProjectileRouter.route(glass, piece, BOARD_COLUMNS / 2).isNotEmpty()
    }

    fun isSelectable(state: GameState, queue: Int, row: Int): Boolean =
        state.status == GameStatus.PLAYING && queue in state.queues.indices &&
            row in 0 until minOf(MAX_VISIBLE_STARS, state.queues[queue].size) &&
            (row == 0 || state.mode == SelectionMode.PICK_STAR)

    fun toggleMode(state: GameState, mode: SelectionMode): GameState {
        if (state.status != GameStatus.PLAYING) return state
        val available = when (mode) {
            SelectionMode.PICK_STAR -> state.boosters.pickStar > 0
            SelectionMode.SHATTER_GLASS -> state.boosters.shatter > 0
            SelectionMode.NONE -> true
        }
        return if (!available || state.mode == mode) state.copy(mode = SelectionMode.NONE) else state.copy(mode = mode)
    }

    fun addSlot(state: GameState): MoveResult {
        if (state.status != GameStatus.PLAYING || state.extraSlotsUsed >= 2 || state.boosters.extraSlot <= 0) {
            return MoveResult(state)
        }
        return MoveResult(state.copy(
            slotCapacity = state.slotCapacity + 1,
            extraSlotsUsed = state.extraSlotsUsed + 1,
            boosters = state.boosters.copy(extraSlot = state.boosters.extraSlot - 1),
            mode = SelectionMode.NONE,
        ))
    }

    fun takeStar(state: GameState, queue: Int, row: Int = 0): MoveResult {
        if (!isSelectable(state, queue, row)) return MoveResult(state)
        val color = state.queues[queue][row]
        val newQueues = state.queues.mapIndexed { index, stars ->
            if (index == queue) stars.filterIndexed { i, _ -> i != row } else stars
        }
        val usePick = state.mode == SelectionMode.PICK_STAR
        var next = state.copy(
            queues = newQueues,
            boosters = if (usePick) state.boosters.copy(pickStar = state.boosters.pickStar - 1) else state.boosters,
            mode = SelectionMode.NONE,
        )
        val effects = mutableListOf<GameEffect>()
        val target = findExposedColor(next.glass, color, queue, state.columns)
        if (target != null) {
            next = next.copy(glass = next.glass - target)
            effects += GameEffect.Projectile(color, StarOrigin.Queue(queue, row), target)
            next = releaseStored(next, effects)
        } else if (next.storage.size < next.slotCapacity) {
            next = next.copy(storage = next.storage + color)
            effects += GameEffect.Stored(color, StarOrigin.Queue(queue, row))
        } else {
            // No legal target and no room: the chosen star is put back. The level is lost.
            next = next.copy(queues = state.queues, status = GameStatus.LOST)
        }
        return MoveResult(finalize(next), effects)
    }

    fun shatter(state: GameState, pieceId: Int): MoveResult {
        if (state.status != GameStatus.PLAYING || state.mode != SelectionMode.SHATTER_GLASS || state.boosters.shatter <= 0) {
            return MoveResult(state)
        }
        val piece = state.glass.firstOrNull { it.id == pieceId } ?: return MoveResult(state)
        val effects = mutableListOf<GameEffect>()
        var queues = state.queues
        val matchingHead = queues.indexOfFirst { it.firstOrNull() == piece.color }
        if (matchingHead >= 0) {
            queues = queues.mapIndexed { index, stars -> if (index == matchingHead) stars.drop(1) else stars }
            effects += GameEffect.Projectile(piece.color, StarOrigin.Queue(matchingHead, 0), piece)
        } else {
            effects += GameEffect.Break(piece)
        }
        var next = state.copy(
            glass = state.glass - piece,
            queues = queues,
            mode = SelectionMode.NONE,
            boosters = state.boosters.copy(shatter = state.boosters.shatter - 1),
        )
        next = releaseStored(next, effects)
        return MoveResult(finalize(next), effects)
    }

    fun continueLevel(state: GameState): MoveResult {
        if (state.status != GameStatus.LOST || state.coins < CONTINUE_COST) return MoveResult(state)
        val effects = mutableListOf<GameEffect>()
        var glass = state.glass
        val removed = state.storage.take(2)
        removed.forEach { color ->
            val target = findExposedColor(glass, color, null, state.columns)
                ?: glass.firstOrNull { it.color == color }
            if (target != null) {
                glass = glass - target
                effects += GameEffect.Break(target)
            }
        }
        var next = state.copy(
            coins = state.coins - CONTINUE_COST,
            storage = state.storage.drop(removed.size),
            glass = glass,
            status = GameStatus.PLAYING,
        )
        next = releaseStored(next, effects)
        return MoveResult(finalize(next), effects)
    }

    fun buyBooster(state: GameState, kind: Int): GameState {
        if (state.coins < BOOSTER_COST || kind !in 0..2) return state
        val b = state.boosters
        val updated = when (kind) {
            0 -> b.copy(extraSlot = b.extraSlot + 1)
            1 -> b.copy(shatter = b.shatter + 1)
            else -> b.copy(pickStar = b.pickStar + 1)
        }
        return state.copy(coins = state.coins - BOOSTER_COST, boosters = updated)
    }

    private fun findExposedColor(glass: List<GlassPiece>, color: StarColor, queue: Int?, queueCount: Int): GlassPiece? {
        val preferredColumn = queue?.let { ((it + 0.5f) * BOARD_COLUMNS / queueCount).toInt() } ?: BOARD_COLUMNS / 2
        return glass.asSequence().filter { it.color == color && isExposed(it, glass) }
            .sortedWith(compareByDescending<GlassPiece> { it.row }
                .thenBy { abs(it.column - preferredColumn) }.thenBy { it.id }).firstOrNull()
    }

    private fun releaseStored(state: GameState, effects: MutableList<GameEffect>): GameState {
        var glass = state.glass
        val storage = state.storage.toMutableList()
        while (true) {
            val found = storage.withIndex().firstNotNullOfOrNull { (slot, color) ->
                findExposedColor(glass, color, null, state.columns)?.let { slot to it }
            } ?: break
            val (slot, piece) = found
            effects += GameEffect.Projectile(piece.color, StarOrigin.Storage(slot), piece)
            storage.removeAt(slot)
            glass = glass - piece
        }
        return state.copy(glass = glass, storage = storage)
    }

    private fun finalize(state: GameState): GameState = when {
        state.glass.isEmpty() -> state.copy(status = GameStatus.WON, mode = SelectionMode.NONE)
        state.status == GameStatus.LOST -> state
        state.queues.all { it.isEmpty() } && state.storage.none { color ->
            findExposedColor(state.glass, color, null, state.columns) != null
        } -> state.copy(status = GameStatus.LOST)
        else -> state
    }
}
