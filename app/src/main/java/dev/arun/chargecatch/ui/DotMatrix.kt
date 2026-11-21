package dev.arun.chargecatch.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val DotOff = Color(0xFF262626)
val DotOn = Color(0xFFF2F2F2)

/** Small dot-art glyphs drawn in the middle of the ring. */
object Glyphs {
    val BOLT = listOf(
        "....###",
        "...###.",
        "..###..",
        ".###...",
        "#######",
        "...###.",
        "..###..",
        ".###...",
        "###....",
        "##.....",
        "#......"
    )
    val CHECK = listOf(
        ".........##",
        "........##.",
        ".......##..",
        "##....##...",
        ".##..##....",
        "..####.....",
        "...##......"
    )
    val FULL = listOf(
        "..#####..",
        ".#.....#.",
        "#..#.#..#",
        "#.......#",
        "#.#...#.#",
        "#..###..#",
        ".#.....#.",
        "..#####.."
    )
}

/**
 * The charge ring: one circle of evenly spaced dots, lit clockwise from the top. The last lit dot
 * is the [accent] head. While charging, a soft accent pulse walks the lit dots one at a time from
 * the top, breathing in and out at each dot, and the head breathes slowly when the pulse reaches
 * it. The fill animates to [level], and with [complete] the whole ring sweeps to the accent colour.
 */
@Composable
fun ChargeRing(
    level: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 320.dp,
    dots: Int = 72,
    charging: Boolean = true,
    complete: Boolean = false
) {
    val shown by animateFloatAsState(level.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "level")
    val sweep by animateFloatAsState(if (complete) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "sweep")
    val transition = rememberInfiniteTransition(label = "ring")
    // Head: slow breath
    val breath by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "headBreath"
    )
    // Pulse position, in dots: walks the whole ring at a slow, even pace. Past the head it is
    // simply not drawn, which gives a natural rest before the next lap.
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = dots.toFloat(),
        animationSpec = infiniteRepeatable(tween(dots * PULSE_MS_PER_DOT, easing = LinearEasing)),
        label = "pulse"
    )

    Canvas(modifier.size(size)) {
        val dotRadius = this.size.minDimension * 0.0115f
        val ringRadius = this.size.minDimension / 2f - dotRadius * 2.6f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val litExact = shown * dots
        val headIndex = if (shown >= 0.999f) -1 else litExact.toInt().coerceAtMost(dots - 1)
        val pulseIndex = pulse.toInt()
        // 0 → 1 → 0 across each dot's turn: the breath in and out
        val pulseBreath = sin(PI.toFloat() * (pulse - pulseIndex))

        for (i in 0 until dots) {
            val angle = (i.toFloat() / dots) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val position = Offset(center.x + ringRadius * cos(angle), center.y + ringRadius * sin(angle))
            val sweptByCatch = i < sweep * dots
            val lit = i < litExact.toInt()

            val color: Color
            var radius = dotRadius
            when {
                sweptByCatch -> color = accent
                i == headIndex -> {
                    val partial = (litExact - litExact.toInt()).coerceIn(0.35f, 1f)
                    color = lerp(DotOff, accent, partial)
                    radius = dotRadius * 1.15f
                    if (charging) {
                        drawCircle(accent.copy(alpha = 0.20f * breath), dotRadius * 3.4f, position)
                        drawCircle(accent.copy(alpha = 0.32f * breath), dotRadius * 2.1f, position)
                    }
                }
                lit && charging && i == pulseIndex -> {
                    // The walking pulse: this dot breathes into the accent colour and back
                    color = lerp(DotOn, accent, pulseBreath)
                    radius = dotRadius * (1f + 0.18f * pulseBreath)
                    drawCircle(accent.copy(alpha = 0.28f * pulseBreath), dotRadius * 2.6f, position)
                }
                lit && charging && i == pulseIndex - 1 -> {
                    // A faint afterglow on the dot it just left
                    val fade = (1f - pulseBreath) * 0.45f
                    color = lerp(DotOn, accent, fade)
                }
                lit -> color = DotOn
                else -> color = DotOff
            }
            drawCircle(color, radius, position)
        }
    }
}

private const val PULSE_MS_PER_DOT = 420

/**
 * A dot-art [glyph] on its own (no background grid), centred in its box.
 */
@Composable
fun DotGlyph(glyph: List<String>, color: Color, modifier: Modifier = Modifier, pitch: Dp = 11.dp) {
    val columns = glyph.maxOf { it.length }
    Canvas(modifier.size(pitch * columns, pitch * glyph.size)) {
        val step = min(this.size.width / columns, this.size.height / glyph.size)
        val radius = step * 0.36f
        glyph.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '#') drawCircle(color, radius, Offset(col * step + step / 2, row * step + step / 2))
            }
        }
    }
}

/**
 * Text drawn in 3×5 dots (digits, ':', '%' and space), for the dot-matrix clock style.
 */
@Composable
fun DotText(text: String, color: Color, modifier: Modifier = Modifier, pitch: Dp = 8.dp) {
    val columns = text.mapIndexed { i, c -> glyphFor(c).first().length + if (i < text.lastIndex) 1 else 0 }.sum()
    Canvas(modifier.size(pitch * columns, pitch * 5)) {
        val step = pitch.toPx()
        val radius = step * 0.38f
        var col = 0
        text.forEach { c ->
            val g = glyphFor(c)
            g.forEachIndexed { row, line ->
                line.forEachIndexed { i, ch ->
                    if (ch == '#') {
                        drawCircle(color, radius, Offset((col + i) * step + step / 2, row * step + step / 2))
                    }
                }
            }
            col += g.first().length + 1
        }
    }
}

private fun glyphFor(c: Char): List<String> = DIGITS[c] ?: listOf("..", "..", "..", "..", "..")

private val DIGITS = mapOf(
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
    '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("###", "..#", "###", "#..", "###"),
    '3' to listOf("###", "..#", ".##", "..#", "###"),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
    '5' to listOf("###", "#..", "###", "..#", "###"),
    '6' to listOf("###", "#..", "###", "#.#", "###"),
    '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"),
    '9' to listOf("###", "#.#", "###", "..#", "###"),
    ':' to listOf(".", "#", ".", "#", "."),
    '%' to listOf("#.#", "..#", ".#.", "#..", "#.#")
)
