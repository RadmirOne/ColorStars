package com.colorstars.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colorstars.shared.game.Boosters
import com.colorstars.shared.game.ColorStarsEngine
import com.colorstars.shared.game.GameEffect
import com.colorstars.shared.game.GameState
import com.colorstars.shared.game.GameStatus
import com.colorstars.shared.game.GlassVertex
import com.colorstars.shared.game.ProjectileFlight
import com.colorstars.shared.game.GlassPiece
import com.colorstars.shared.game.PendingGameEffect
import com.colorstars.shared.game.prepareMoveEffects
import com.colorstars.shared.game.visibleGlass
import com.colorstars.shared.game.MoveResult
import com.colorstars.shared.game.SelectionMode
import com.colorstars.shared.game.StarColor
import com.colorstars.shared.game.StarOrigin
import com.colorstars.shared.game.glassCorner
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Night = Color(0xFF0A1024)
private val Lavender = Color(0xFFB6A1F7)
private val Pearl = Color(0xFFF6F1FF)
private val Gold = Color(0xFFF3C565)

private fun StarColor.tint(): Color = when (this) {
    StarColor.BLUE -> Color(0xFF5DCEF6)
    StarColor.GOLD -> Color(0xFFF5C761)
    StarColor.GREEN -> Color(0xFF61DAB9)
    StarColor.PINK -> Color(0xFFE982CC)
    StarColor.PURPLE -> Color(0xFFB59AFE)
}

private data class BoardGeometry(
    val left: Float, val top: Float, val tileWidth: Float, val tileHeight: Float,
    val boardRows: Int, val boardColumns: Int,
) {
    val width: Float get() = tileWidth * boardColumns
    val height: Float get() = tileHeight * boardRows
    fun tileCenter(piece: GlassPiece): Offset = Offset(left + (piece.column + .5f) * tileWidth, top + (piece.row + .5f) * tileHeight)
}

@Composable
fun ColorStarsGameScreen() {
    var game by remember { mutableStateOf(ColorStarsEngine.newGame()) }
    var flights by remember { mutableStateOf(emptyList<PendingGameEffect>()) }
    var nextFlightId by remember { mutableStateOf(0L) }
    var queueTransition by remember { mutableStateOf<Pair<GameState, MoveResult>?>(null) }
    var purchasing by remember { mutableIntStateOf(-1) }
    var menuOpen by remember { mutableStateOf(false) }
    val busy = flights.isNotEmpty()
    val onMove: (MoveResult) -> Unit = { move ->
        if (move.state != game) {
            val additions = prepareMoveEffects(game, move, nextFlightId)
            nextFlightId += additions.size
            queueTransition = game to move
            flights = flights + additions
            // Reserve targets and consume stars now. Animation completion never overwrites newer moves.
            game = move.state
        }
    }
    LaunchedEffect(queueTransition) {
        if (queueTransition != null) {
            delay(160)
            queueTransition = null
        }
    }
    val displayedGame = game.copy(glass = visibleGlass(game, flights))
    MaterialTheme {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF202B52), Color(0xFF111A37), Night)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val header = 56.dp
            val slotsHeight = 66.dp
            val queueHeight = 192.dp
            val toolsHeight = 64.dp
            val glassArea = (maxHeight - header - slotsHeight - queueHeight - toolsHeight).coerceAtLeast(150.dp)
            val density = LocalDensity.current
            val geometry = with(density) {
                val width = maxWidth.toPx()
                val glassPx = glassArea.toPx()
                val boardWidth = min(width * .78f, 324.dp.toPx())
                val boardHeight = min(boardWidth * .84f, glassPx * .65f)
                BoardGeometry((width - boardWidth) / 2f, header.toPx() + (glassPx - boardHeight) / 2f,
                    boardWidth / game.boardColumns, boardHeight / game.boardRows,
                    game.boardRows, game.boardColumns)
            }
            val screenWidth = with(density) { maxWidth.toPx() }
            val queuesY = with(density) { (header + glassArea + slotsHeight).toPx() }
            val slotsY = with(density) { (header + glassArea + slotsHeight / 2).toPx() }
            val queueFirstY = queuesY + with(density) { 24.dp.toPx() }

            Column(Modifier.fillMaxSize()) {
                Header(game, header, onMenu = { menuOpen = true })
                Mosaic(
                    game = displayedGame,
                    geometry = geometry.copy(top = geometry.top - with(density) { header.toPx() }),
                    modifier = Modifier.fillMaxWidth().height(glassArea),
                    onShatter = { if (!busy) onMove(ColorStarsEngine.shatter(game, it)) },
                )
                StorageRow(game, slotsHeight)
                QueueRows(queueTransition?.first ?: game, queueHeight, queueTransition?.second) { column, row ->
                    onMove(ColorStarsEngine.takeStar(game, column, row))
                }
                ToolsRow(game, toolsHeight, busy) { index ->
                    val stock = when (index) { 0 -> game.boosters.extraSlot; 1 -> game.boosters.shatter; else -> game.boosters.pickStar }
                    if (stock == 0) purchasing = index
                    else when (index) {
                        0 -> onMove(ColorStarsEngine.addSlot(game))
                        1 -> game = ColorStarsEngine.toggleMode(game, SelectionMode.SHATTER_GLASS)
                        else -> game = ColorStarsEngine.toggleMode(game, SelectionMode.PICK_STAR)
                    }
                }
            }

            flights.forEach { flight ->
                key(flight.id) {
                    FlightAnimation(flight, displayedGame.glass,
                        ready = flights.none { it.id == flight.predecessor },
                        geometry = geometry, width = screenWidth, queueY = queueFirstY,
                        rowHeight = with(density) { 48.dp.toPx() }, slotsY = slotsY,
                        onFinished = { flights = flights.filterNot { it.id == flight.id } })
                }
            }
            if (menuOpen) {
                GameDialog(
                    title = "ColorStars",
                    message = "Уровень ${game.level}",
                    confirm = "Начать заново",
                    dismiss = "Закрыть",
                    onConfirm = { flights = emptyList(); queueTransition = null; game = ColorStarsEngine.newGame(game.level, game.coins, game.boosters); menuOpen = false },
                    onDismiss = { menuOpen = false },
                )
            }
            if (purchasing >= 0) {
                val index = purchasing
                GameDialog(
                    title = "Купить инструмент?",
                    message = when (index) {
                        0 -> "Добавляет слот для хранения"
                        1 -> "Разрушает любой фрагмент"
                        else -> "Позволяет выбрать любую звезду"
                    },
                    detail = "🟡 ${ColorStarsEngine.BOOSTER_COST}",
                    confirm = "Купить",
                    dismiss = "Отмена",
                    onConfirm = { game = ColorStarsEngine.buyBooster(game, index); purchasing = -1 },
                    onDismiss = { purchasing = -1 },
                )
            }
            if (!busy && game.status == GameStatus.LOST) {
                GameDialog(
                    title = "Ходы закончились",
                    message = "Освободить два слота",
                    detail = "🟡 ${ColorStarsEngine.CONTINUE_COST}",
                    confirm = "Продолжить",
                    dismiss = "Заново",
                    onConfirm = { onMove(ColorStarsEngine.continueLevel(game)) },
                    onDismiss = { game = ColorStarsEngine.newGame(game.level, game.coins, game.boosters) },
                )
            }
            if (!busy && game.status == GameStatus.WON) {
                GameDialog(
                    title = "Уровень пройден!",
                    message = "🟡 +80",
                    confirm = "Следующий уровень",
                    dismiss = "Повторить",
                    onConfirm = { game = ColorStarsEngine.newGame(game.level + 1, game.coins + 80, game.boosters) },
                    onDismiss = { game = ColorStarsEngine.newGame(game.level, game.coins, game.boosters) },
                )
            }
        }
    }
}

@Composable
private fun FlightAnimation(
    flight: PendingGameEffect, glass: List<GlassPiece>, ready: Boolean,
    geometry: BoardGeometry, width: Float, queueY: Float, rowHeight: Float, slotsY: Float,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val padding = with(density) { 12.dp.toPx() }
    val candidate = remember(flight, glass, geometry, width, queueY, rowHeight, slotsY, padding) {
        val effect = flight.effect
        if (effect is GameEffect.Projectile) {
            projectilePoints(effectStart(effect.origin, flight.source, width, queueY, rowHeight, slotsY, padding),
                geometry, flight.source.copy(glass = glass), effect)
        } else emptyList()
    }
    // The shatter booster may intentionally target an enclosed fragment.
    val shatter = flight.source.mode == SelectionMode.SHATTER_GLASS && candidate.isEmpty()
    val canLaunch = ready && (flight.effect !is GameEffect.Projectile || candidate.isNotEmpty() || shatter)
    val plan by rememberUpdatedState(if (canLaunch) candidate else null)
    val finish by rememberUpdatedState(onFinished)
    val travel = remember { Animatable(0f) }
    var path by remember { mutableStateOf<List<Offset>?>(null) }
    var effect by remember { mutableStateOf(flight.effect) }
    LaunchedEffect(flight.id) {
        path = snapshotFlow { plan }.first { it != null }
        if (flight.effect is GameEffect.Projectile && path!!.isEmpty()) {
            effect = GameEffect.Break(flight.effect.target)
        }
        travel.animateTo(1f, if (effect is GameEffect.Projectile)
            tween(500, easing = LinearEasing) else tween(180))
        finish()
    }
    if (path != null) {
        Canvas(Modifier.fillMaxSize()) {
            drawMovingEffect(effect, flight.source, geometry, width, queueY, rowHeight, slotsY,
                travel.value, path.orEmpty())
        }
    }
}

@Composable
private fun Header(game: GameState, height: Dp, onMenu: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height).background(Color(0x550B142B))) {
        Text("☰", modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onMenu).padding(horizontal = 20.dp, vertical = 8.dp), color = Pearl, fontSize = 26.sp)
        Text("Уровень ${game.level}", modifier = Modifier.align(Alignment.Center), color = Pearl, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("✦ ${game.coins}", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun Mosaic(game: GameState, geometry: BoardGeometry, modifier: Modifier, onShatter: (Int) -> Unit) {
    Canvas(modifier.pointerInput(game.glass, game.mode) {
        detectTapGestures { tapped ->
            if (game.mode != SelectionMode.SHATTER_GLASS) return@detectTapGestures
            val col = ((tapped.x - geometry.left) / geometry.tileWidth).toInt()
            val row = ((tapped.y - geometry.top) / geometry.tileHeight).toInt()
            game.glass.firstOrNull { it.row == row && it.column == col }?.let { onShatter(it.id) }
        }
    }) {
        drawCircle(brush = Brush.radialGradient(listOf(Color(0x444D51CE), Color.Transparent), center = Offset(size.width / 2, size.height / 2)), radius = geometry.width * .76f, center = Offset(size.width / 2, size.height / 2))
        game.glass.forEach { tile ->
            val path = tilePath(tile, geometry)
            val center = geometry.tileCenter(tile)
            val tint = tile.color.tint()
            drawPath(path, brush = Brush.linearGradient(listOf(tint.copy(alpha = .97f), tint.copy(alpha = .56f), tint.copy(alpha = .88f)),
                start = Offset(center.x - geometry.tileWidth / 2, center.y - geometry.tileHeight / 2),
                end = Offset(center.x + geometry.tileWidth / 2, center.y + geometry.tileHeight / 2)))
            drawPath(path, color = if (game.level == 1) Color(0xFF281A2A) else Color(0xFFDBE9FF).copy(alpha = .70f),
                style = Stroke(width = if (game.level == 1) 3.2f else 1.9f))
            drawPath(path, color = tint.copy(alpha = .24f), style = Stroke(width = 6f))
            drawLine(Color.White.copy(alpha = .26f),
                Offset(center.x - geometry.tileWidth * .28f, center.y - geometry.tileHeight * .26f),
                Offset(center.x - geometry.tileWidth * .03f, center.y - geometry.tileHeight * .39f),
                strokeWidth = 1.7f, cap = StrokeCap.Round)
            // Details from the owl concept live on the corresponding removable glass fragments.
            if (game.level == 1 && tile.row == 3 && (tile.column == 2 || tile.column == 5)) {
                val radius = min(geometry.tileWidth, geometry.tileHeight) * .33f
                drawCircle(Color(0xFF361747), radius, center)
                drawCircle(Color(0xFF100A22), radius * .7f, center)
                drawCircle(Color.White.copy(alpha = .92f), radius * .21f,
                    Offset(center.x - radius * .29f, center.y - radius * .30f))
            }
            if (game.level == 1 && tile.row == 4 && (tile.column == 3 || tile.column == 4)) {
                val axisX = geometry.left + geometry.tileWidth * 4f
                val top = geometry.top + geometry.tileHeight * 4.12f
                val bottom = geometry.top + geometry.tileHeight * 4.84f
                val sideX = axisX + geometry.tileWidth * (if (tile.column == 3) -.38f else .38f)
                val beak = Path().apply {
                    moveTo(axisX, top); lineTo(sideX, top + geometry.tileHeight * .15f)
                    lineTo(axisX, bottom); close()
                }
                drawPath(beak, Color(0xFFFFA721))
                drawPath(beak, Color(0xFF5C2942), style = Stroke(width = 2.4f))
            }
            if (game.mode == SelectionMode.SHATTER_GLASS) {
                drawPath(path, color = Color.White.copy(alpha = .53f), style = Stroke(width = 3f))
            }
        }
    }
}

private fun tilePath(tile: GlassPiece, geo: BoardGeometry): Path {
    val c = tile.column
    val r = tile.row
    fun corner(col: Int, row: Int): Offset {
        val vertex = glassCorner(col, row, geo.boardColumns, geo.boardRows)
        return Offset(geo.left + vertex.x * geo.tileWidth, geo.top + vertex.y * geo.tileHeight)
    }
    val tl = corner(c, r)
    val tr = corner(c + 1, r)
    val br = corner(c + 1, r + 1)
    val bl = corner(c, r + 1)
    return Path().apply {
        moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
    }
}

@Composable
private fun StorageRow(game: GameState, height: Dp) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        repeat(game.slotCapacity) { index ->
            val diameter = when (game.slotCapacity) { 4 -> 46.dp; 5 -> 42.dp; else -> 38.dp }
            Canvas(Modifier.size(diameter)) {
                val c = center
                drawCircle(Color(0xFF050A18), radius = size.minDimension / 2)
                drawCircle(brush = Brush.verticalGradient(listOf(Color(0xFF4D628C), Color(0xFF11192D), Color(0xFF283653))), radius = size.minDimension * .47f, center = c)
                drawCircle(Color(0xFF070D1C), radius = size.minDimension * .35f, center = c)
                drawCircle(Color(0xFF8492BD).copy(alpha = .55f), radius = size.minDimension * .46f, center = c, style = Stroke(width = 1.5f))
                game.storage.getOrNull(index)?.let { drawStar(c, size.minDimension * .29f, it.tint(), 1f, glow = true) }
            }
        }
    }
}

@Composable
private fun QueueRows(game: GameState, height: Dp, pending: MoveResult?, onStar: (Int, Int) -> Unit) {
    val selectableGame = pending?.state ?: game
    val removed = pending?.effects?.firstNotNullOfOrNull { effect ->
        when (effect) {
            is GameEffect.Projectile -> effect.origin as? StarOrigin.Queue
            is GameEffect.Stored -> effect.origin
            is GameEffect.Break -> null
        }
    }
    // Animate the consumed star while clicks already address the updated queue.
    val shift = remember(pending) { Animatable(0f) }
    LaunchedEffect(pending) {
        if (removed != null) shift.animateTo(1f, tween(160))
    }
    val selection = game.mode == SelectionMode.PICK_STAR
    val pulse by rememberInfiniteTransition(label = "star-select").animateFloat(
        initialValue = .24f, targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "select-halo"
    )
    val visibleColumns = game.queues.indices.filter { game.queues[it].isNotEmpty() }
    val nextQueues = pending?.state?.queues ?: game.queues
    val nextColumns = nextQueues.indices.filter { nextQueues[it].isNotEmpty() }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp)) {
        visibleColumns.forEachIndexed { index, col ->
            val queue = game.queues[col]
            val nextIndex = nextColumns.indexOf(col)
            val disappearing = nextIndex < 0
            val oldWidth = maxWidth / visibleColumns.size
            val newWidth = if (disappearing) oldWidth else maxWidth / nextColumns.size
            val columnWidth = oldWidth + (newWidth - oldWidth) * shift.value
            val oldCenter = oldWidth * (index + .5f)
            val newCenter = if (disappearing) oldCenter else newWidth * (nextIndex + .5f)
            val columnLeft = oldCenter + (newCenter - oldCenter) * shift.value - columnWidth / 2
            Box(Modifier.width(columnWidth).fillMaxHeight().graphicsLayer {
                translationX = columnLeft.toPx()
                alpha = if (disappearing) 1f - shift.value else 1f
            }.clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val oldCount = min(queue.size, ColorStarsEngine.MAX_VISIBLE_STARS)
                    val newCount = min(nextQueues[col].size, ColorStarsEngine.MAX_VISIBLE_STARS)
                    val visibleCount = oldCount + (newCount - oldCount) * shift.value
                    val rowHeight = 48.dp.toPx()
                    val starsTop = (size.height - rowHeight * ColorStarsEngine.MAX_VISIBLE_STARS) / 2f
                    val endY = (starsTop + (visibleCount - .5f) * rowHeight).coerceAtLeast(3f)
                    drawLine(Lavender.copy(alpha = .09f), Offset(cx, 2f), Offset(cx, endY), strokeWidth = 12f, cap = StrokeCap.Round)
                    drawLine(Lavender.copy(alpha = .52f), Offset(cx, 3f), Offset(cx, endY), strokeWidth = 2f)
                }
                Box(Modifier.align(Alignment.Center).width(48.dp).height(48.dp * ColorStarsEngine.MAX_VISIBLE_STARS)) {
                    val removedRow = removed?.takeIf { it.column == col }?.row
                    // Include one offscreen star so it can slide into the last visible slot.
                    repeat(ColorStarsEngine.MAX_VISIBLE_STARS + if (removedRow != null) 1 else 0) { row ->
                        if (row == removedRow) return@repeat
                        val color = queue.getOrNull(row)
                        val selectedRow = if (removedRow != null && row > removedRow) row - 1 else row
                        val enabled = color != null && ColorStarsEngine.isSelectable(selectableGame, col, selectedRow)
                        val displacement = if (removedRow != null && row > removedRow) shift.value else 0f
                        val visualRow = row - displacement
                        val prominence = (1f - visualRow).coerceIn(0f, 1f)
                        Box(
                            Modifier.size(48.dp).graphicsLayer {
                                translationY = visualRow * 48.dp.toPx()
                            }.clickable(enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() }, indication = null) { onStar(col, selectedRow) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color != null) {
                                Canvas(Modifier.size(38.dp)) {
                                    drawStar(center, (33.dp.toPx() + 5.dp.toPx() * prominence) * .42f,
                                        color.tint(), .67f + .33f * prominence,
                                        glow = prominence > 0f || selection, haloOpacity = if (selection) pulse else 0f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsRow(game: GameState, height: Dp, busy: Boolean, onTool: (Int) -> Unit) {
    val stock = listOf(game.boosters.extraSlot, game.boosters.shatter, game.boosters.pickStar)
    Row(Modifier.fillMaxWidth().height(height).background(Color(0xB20B142B)),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            if (index != 0) Spacer(Modifier.width(9.dp))
            val active = (index == 1 && game.mode == SelectionMode.SHATTER_GLASS) || (index == 2 && game.mode == SelectionMode.PICK_STAR)
            val disabled = index == 0 && game.extraSlotsUsed >= 2
            Box(Modifier.size(48.dp).clickable(enabled = !busy && !disabled && game.status == GameStatus.PLAYING) { onTool(index) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(43.dp)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(if (active) listOf(Color(0xFF59438C), Color(0xFF362E62)) else listOf(Color(0xFF334265), Color(0xFF1A2640))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                    )
                    drawRoundRect(if (active) Lavender else Color(0xFF7283B0), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
                Text(when (index) { 0 -> "+"; 1 -> "✧"; else -> "★" }, color = if (disabled) Lavender.copy(alpha = .4f) else Pearl, fontSize = 25.sp)
                Text("${stock[index]}", modifier = Modifier.align(Alignment.BottomEnd).background(Night, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp),
                    color = Pearl, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameDialog(title: String, message: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit, detail: String = "") {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 21.sp, color = Pearl, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (message.isNotEmpty()) Text(message, color = Color(0xFFDFE3FA), fontSize = 15.sp, textAlign = TextAlign.Center)
                if (detail.isNotEmpty()) Text(detail, color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF3C2756))) {
                Text(confirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = Lavender) } },
        containerColor = Color(0xFF233252),
        shape = RoundedCornerShape(25.dp),
    )
}

private fun DrawScope.drawStar(center: Offset, radius: Float, tint: Color, opacity: Float, glow: Boolean, haloOpacity: Float = 0f) {
    if (glow) drawCircle(tint.copy(alpha = .12f * opacity), radius = radius * 1.75f, center = center)
    if (haloOpacity > 0f) drawCircle(tint.copy(alpha = haloOpacity), radius = radius * 1.48f, center = center, style = Stroke(width = 2.3f))
    val path = Path()
    for (i in 0..9) {
        val angle = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) radius else radius * .46f
        val x = center.x + cos(angle).toFloat() * rr
        val y = center.y + sin(angle).toFloat() * rr
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint.copy(alpha = opacity))
    drawPath(path, Color.White.copy(alpha = .72f * opacity), style = Stroke(width = 1.4f))
    drawCircle(Color.White.copy(alpha = .65f * opacity), radius = radius * .10f, center = Offset(center.x - radius * .19f, center.y - radius * .20f))
}

private fun DrawScope.drawMovingEffect(
    effect: GameEffect, game: GameState, geo: BoardGeometry, width: Float,
    queueY: Float, queueRowHeight: Float, slotsY: Float, progress: Float, flightPoints: List<Offset>,
) {
    val target = when (effect) {
        is GameEffect.Projectile -> effect.target
        is GameEffect.Break -> effect.target
        is GameEffect.Stored -> null
    }
    if (effect is GameEffect.Break && target != null) {
        val c = geo.tileCenter(target)
        drawCircle(Color.White.copy(alpha = (1f - progress) * .72f), radius = geo.tileWidth * (.25f + progress * .9f), center = c)
        return
    }
    val origin = when (effect) {
        is GameEffect.Projectile -> effect.origin
        is GameEffect.Stored -> effect.origin
        is GameEffect.Break -> StarOrigin.Booster
    }
    val start = effectStart(origin, game, width, queueY, queueRowHeight, slotsY, 12.dp.toPx())
    if (effect is GameEffect.Stored) {
        val end = Offset(width * (game.storage.size + .5f) / game.slotCapacity, slotsY)
        val position = Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress)
        drawStar(position, 15.dp.toPx() * (1f - progress * .15f), effect.color.tint(), 1f, glow = true)
        return
    }
    val shot = effect as GameEffect.Projectile
    val color = shot.color.tint()
    if (flightPoints.isEmpty()) return
    val smooth = flightPoints
    val total = lengthOf(smooth)
    val head = along(smooth, total * progress)
    fun tailPath(length: Float): Path = Path().apply {
        val end = total * progress
        val begin = (end - length).coerceAtLeast(0f)
        repeat(17) { index ->
            val point = along(smooth, begin + (end - begin) * index / 16f)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
    val tail = tailPath(27.dp.toPx())
    drawPath(tail, color.copy(alpha = .19f), style = Stroke(13.dp.toPx(), cap = StrokeCap.Round))
    drawPath(tail, color.copy(alpha = .78f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(tailPath(8.dp.toPx()), Color.White.copy(alpha = .54f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(color.copy(alpha = .21f), radius = 15.dp.toPx(), center = head)
    val coreRadius = min(6.dp.toPx(), min(geo.tileWidth, geo.tileHeight) * .16f)
    drawCircle(color, radius = coreRadius, center = head)
    drawCircle(Color.White, radius = coreRadius * .4f, center = head)
}

private fun effectStart(
    origin: StarOrigin, game: GameState, width: Float, queueY: Float, queueRowHeight: Float, slotsY: Float, padding: Float,
): Offset = when (origin) {
        is StarOrigin.Queue -> {
            val visibleColumns = game.queues.indices.filter { game.queues[it].isNotEmpty() }
            val index = visibleColumns.indexOf(origin.column)

            Offset(padding + (width - 2 * padding) * (index + .5f) / visibleColumns.size,
                queueY + origin.row * queueRowHeight)
        }
        is StarOrigin.Storage -> Offset(width * (origin.slot + .5f) / game.slotCapacity, slotsY)
        StarOrigin.Booster -> Offset(width / 2, slotsY)
    }

private fun projectilePoints(start: Offset, geo: BoardGeometry, game: GameState, shot: GameEffect.Projectile): List<Offset> =
    ProjectileFlight.route(game.glass, shot.target,
        GlassVertex((start.x - geo.left) / geo.tileWidth, (start.y - geo.top) / geo.tileHeight),
        game.boardRows, game.boardColumns,
    ).map { Offset(geo.left + it.x * geo.tileWidth, geo.top + it.y * geo.tileHeight) }
private fun lengthOf(points: List<Offset>): Float = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

private fun along(points: List<Offset>, distance: Float): Offset {
    if (points.isEmpty()) return Offset.Zero
    var left = distance
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val segment = hypot(b.x - a.x, b.y - a.y)
        if (left <= segment && segment > 0f) {
            val t = left / segment
            return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
        left -= segment
    }
    return points.last()
}
