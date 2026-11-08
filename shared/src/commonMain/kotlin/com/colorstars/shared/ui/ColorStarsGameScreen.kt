package com.colorstars.shared.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.colorstars.shared.game.GlassPiece
import com.colorstars.shared.game.GridPoint
import com.colorstars.shared.game.MoveResult
import com.colorstars.shared.game.ProjectileRouter
import com.colorstars.shared.game.SelectionMode
import com.colorstars.shared.game.StarColor
import com.colorstars.shared.game.StarOrigin
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

private data class BoardGeometry(val left: Float, val top: Float, val tileWidth: Float, val tileHeight: Float) {
    val width: Float get() = tileWidth * ColorStarsEngine.BOARD_COLUMNS
    val height: Float get() = tileHeight * ColorStarsEngine.BOARD_ROWS
    fun tileCenter(piece: GlassPiece): Offset = Offset(left + (piece.column + .5f) * tileWidth, top + (piece.row + .5f) * tileHeight)
    fun gridCenter(point: GridPoint): Offset = Offset(left + (point.column + .5f) * tileWidth, top + (point.row + .5f) * tileHeight)
}

@Composable
fun ColorStarsGameScreen() {
    var game by remember { mutableStateOf(ColorStarsEngine.newGame()) }
    var pending by remember { mutableStateOf<MoveResult?>(null) }
    var activeEffect by remember { mutableStateOf<GameEffect?>(null) }
    var purchasing by remember { mutableIntStateOf(-1) }
    var menuOpen by remember { mutableStateOf(false) }
    val travel = remember { Animatable(0f) }
    val busy = pending != null
    val onMove: (MoveResult) -> Unit = { move ->
        if (!busy && move.state != game) {
            if (move.effects.isEmpty()) game = move.state else pending = move
        }
    }

    LaunchedEffect(pending) {
        val move = pending ?: return@LaunchedEffect
        for (effect in move.effects) {
            activeEffect = effect
            travel.snapTo(0f)
            travel.animateTo(1f, tween(if (effect is GameEffect.Projectile) 420 else 270))
        }
        activeEffect = null
        game = move.state
        pending = null
    }

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
                val boardWidth = min(width * .84f, 324.dp.toPx())
                val boardHeight = min(boardWidth * .84f, glassPx * .85f)
                BoardGeometry((width - boardWidth) / 2f, header.toPx() + (glassPx - boardHeight) / 2f,
                    boardWidth / ColorStarsEngine.BOARD_COLUMNS, boardHeight / ColorStarsEngine.BOARD_ROWS)
            }
            val screenWidth = with(density) { maxWidth.toPx() }
            val queuesY = with(density) { (header + glassArea + slotsHeight).toPx() }
            val slotsY = with(density) { (header + glassArea + slotsHeight / 2).toPx() }
            val queueFirstY = queuesY + with(density) { 24.dp.toPx() }

            Column(Modifier.fillMaxSize()) {
                Header(game, header, onMenu = { menuOpen = true })
                Mosaic(
                    game = game,
                    geometry = geometry.copy(top = geometry.top - with(density) { header.toPx() }),
                    modifier = Modifier.fillMaxWidth().height(glassArea),
                    onShatter = { if (!busy) onMove(ColorStarsEngine.shatter(game, it)) },
                )
                StorageRow(game, slotsHeight)
                QueueRows(game, queueHeight, busy) { column, row ->
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

            val effect = activeEffect
            if (effect != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawMovingEffect(
                        effect, game, geometry, screenWidth, queueFirstY, with(density) { 48.dp.toPx() }, slotsY,
                        progress = travel.value,
                    )
                }
            }

            if (menuOpen) {
                GameDialog(
                    title = "ColorStars",
                    message = "Уровень ${game.level}",
                    confirm = "Начать заново",
                    dismiss = "Закрыть",
                    onConfirm = { game = ColorStarsEngine.newGame(game.level, game.coins, game.boosters); menuOpen = false },
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
            drawPath(path, color = Color(0xFFDBE9FF).copy(alpha = .70f), style = Stroke(width = 1.9f))
            drawPath(path, color = tint.copy(alpha = .24f), style = Stroke(width = 6f))
            drawLine(Color.White.copy(alpha = .26f),
                Offset(center.x - geometry.tileWidth * .28f, center.y - geometry.tileHeight * .26f),
                Offset(center.x - geometry.tileWidth * .03f, center.y - geometry.tileHeight * .39f),
                strokeWidth = 1.7f, cap = StrokeCap.Round)
            if (game.mode == SelectionMode.SHATTER_GLASS) {
                drawPath(path, color = Color.White.copy(alpha = .53f), style = Stroke(width = 3f))
            }
        }
        // The central spine binds the floating wing-shaped mosaic together.
        val x = geometry.left + geometry.width / 2
        val cy = geometry.top + geometry.height / 2
        drawLine(Color(0xFFAAA4E8).copy(alpha = .38f), Offset(x, cy - geometry.height * .33f), Offset(x, cy + geometry.height * .35f), strokeWidth = 3.5f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = .65f), radius = 2.5f, center = Offset(x, cy - geometry.height * .33f))
    }
}

private fun tilePath(tile: GlassPiece, geo: BoardGeometry): Path {
    val c = tile.column
    val r = tile.row
    fun corner(col: Int, row: Int): Offset {
        val dx = if (col == 0 || col == ColorStarsEngine.BOARD_COLUMNS) 0f else
            sin((col * 7 + row * 13).toFloat()) * geo.tileWidth * .12f
        val dy = if (row == 0 || row == ColorStarsEngine.BOARD_ROWS) 0f else
            cos((col * 11 + row * 5).toFloat()) * geo.tileHeight * .13f
        return Offset(geo.left + col * geo.tileWidth + dx, geo.top + row * geo.tileHeight + dy)
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
private fun QueueRows(game: GameState, height: Dp, busy: Boolean, onStar: (Int, Int) -> Unit) {
    val selection = game.mode == SelectionMode.PICK_STAR
    val pulse by rememberInfiniteTransition(label = "star-select").animateFloat(
        initialValue = .24f, targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "select-halo"
    )
    Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        game.queues.forEachIndexed { col, queue ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    drawLine(Lavender.copy(alpha = .09f), Offset(cx, 2f), Offset(cx, size.height), strokeWidth = 12f, cap = StrokeCap.Round)
                    drawLine(Lavender.copy(alpha = .52f), Offset(cx, 3f), Offset(cx, size.height - 3f), strokeWidth = 2f)
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(ColorStarsEngine.MAX_VISIBLE_STARS) { row ->
                        val color = queue.getOrNull(row)
                        val enabled = color != null && !busy && ColorStarsEngine.isSelectable(game, col, row)
                        Box(
                            Modifier.size(48.dp).clickable(enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() }, indication = null) { onStar(col, row) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color != null) {
                                Canvas(Modifier.size(if (row == 0) 38.dp else 33.dp)) {
                                    drawStar(center, size.minDimension * .42f, color.tint(), if (row == 0) 1f else .67f,
                                        glow = row == 0 || selection, haloOpacity = if (selection) pulse else 0f)
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
    queueY: Float, queueRowHeight: Float, slotsY: Float, progress: Float,
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
    val start = when (origin) {
        is StarOrigin.Queue -> Offset(queueCenterX(width, game.columns, origin.column), queueY + origin.row * queueRowHeight)
        is StarOrigin.Storage -> Offset(width * (origin.slot + .5f) / game.slotCapacity, slotsY)
        StarOrigin.Booster -> Offset(width / 2, slotsY)
    }
    if (effect is GameEffect.Stored) {
        val end = Offset(width * (game.storage.size + .5f) / game.slotCapacity, slotsY)
        val position = Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress)
        drawStar(position, 15.dp.toPx() * (1f - progress * .15f), effect.color.tint(), 1f, glow = true)
        return
    }
    val shot = effect as GameEffect.Projectile
    val color = shot.color.tint()
    val column = when (origin) {
        is StarOrigin.Queue -> ((origin.column + .5f) * ColorStarsEngine.BOARD_COLUMNS / game.columns).toInt()
        is StarOrigin.Storage -> ((origin.slot + .5f) * ColorStarsEngine.BOARD_COLUMNS / game.slotCapacity).toInt()
        StarOrigin.Booster -> ColorStarsEngine.BOARD_COLUMNS / 2
    }
    val route = ProjectileRouter.route(game.glass, shot.target, column)
    val points = buildList {
        add(start)
        route.forEach { add(geo.gridCenter(it)) }
        val edge = route.lastOrNull()
        if (edge != null) {
            val center = geo.tileCenter(shot.target)
            val direction = Offset(edge.column - shot.target.column.toFloat(), edge.row - shot.target.row.toFloat())
            add(Offset(center.x + direction.x * geo.tileWidth * .5f, center.y + direction.y * geo.tileHeight * .5f))
        } else add(geo.tileCenter(shot.target))
    }
    val smooth = smoothPoints(points)
    val total = lengthOf(smooth)
    val head = along(smooth, total * progress)
    val tail = along(smooth, (total * progress - 27.dp.toPx()).coerceAtLeast(0f))
    drawLine(color.copy(alpha = .19f), tail, head, strokeWidth = 13.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color.copy(alpha = .78f), tail, head, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = .54f), along(smooth, (total * progress - 8.dp.toPx()).coerceAtLeast(0f)), head, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(color.copy(alpha = .21f), radius = 15.dp.toPx(), center = head)
    drawCircle(color, radius = 6.dp.toPx(), center = head)
    drawCircle(Color.White, radius = 2.4.dp.toPx(), center = head)
}

private fun queueCenterX(width: Float, count: Int, index: Int): Float =
    12f * (width / 390f) + (width - 24f * (width / 390f)) * (index + .5f) / count

private fun smoothPoints(points: List<Offset>): List<Offset> {
    if (points.size < 3) return points
    val result = mutableListOf(points.first())
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        result += Offset(a.x * .75f + b.x * .25f, a.y * .75f + b.y * .25f)
        result += Offset(a.x * .25f + b.x * .75f, a.y * .25f + b.y * .75f)
    }
    result += points.last()
    return result
}

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
