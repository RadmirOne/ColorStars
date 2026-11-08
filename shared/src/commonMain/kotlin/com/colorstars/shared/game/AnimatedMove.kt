package com.colorstars.shared.game

data class PendingGameEffect(
    val id: Long,
    val effect: GameEffect,
    val source: GameState,
    val predecessor: Long?,
)

/** Capture origins before committing a move; later clicks must use the committed state. */
fun prepareMoveEffects(before: GameState, move: MoveResult, firstId: Long): List<PendingGameEffect> {
    var source = before
    return move.effects.mapIndexed { index, effect ->
        val pending = PendingGameEffect(firstId + index, effect, source,
            if (index == 0) null else firstId + index - 1)
        source = when (effect) {
            is GameEffect.Projectile -> source.copy(
                glass = source.glass - effect.target,
                storage = if (effect.origin is StarOrigin.Storage)
                    source.storage.filterIndexed { slot, _ -> slot != effect.origin.slot } else source.storage,
            )
            is GameEffect.Break -> source.copy(glass = source.glass - effect.target)
            is GameEffect.Stored -> source.copy(storage = source.storage + effect.color)
        }
        pending
    }
}

fun visibleGlass(game: GameState, pending: List<PendingGameEffect>): List<GlassPiece> =
    (game.glass + pending.mapNotNull {
        when (val effect = it.effect) {
            is GameEffect.Projectile -> effect.target
            is GameEffect.Break -> effect.target
            is GameEffect.Stored -> null
        }
    }).distinctBy { it.id }
