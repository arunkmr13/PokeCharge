package dev.arun.chargecatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

val DotOff = Color(0xFF1D1D1D)
val DotOn = Color(0xFFF2F2F2)

/** Small dot-art glyphs drawn inside the 25×25 ring. */
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
 * The round 25×25 dot grid, like a Glyph Matrix: a charge ring around the edge (lit clockwise from
 * the top to [level], with an [accent] head) and an optional [glyph] in the middle. With
 * [showInner] off, only the ring is drawn, so a sprite can sit inside it.
 */
@Composable
fun DotRing(
    level: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 340.dp,
    glyph: List<String>? = null,
    glyphColor: Color = accent,
    showInner: Boolean = true,
    ringOverride: Color? = null
) {
    Canvas(modifier.size(size)) {
        val cell = this.size.width / GRID
        val radius = cell * 0.34f
        val glyphH = glyph?.size ?: 0
        val glyphW = glyph?.maxOfOrNull { it.length } ?: 0
        val gx = CENTER - glyphW / 2
        val gy = CENTER - glyphH / 2
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                val dx = (x - CENTER).toFloat()
                val dy = (y - CENTER).toFloat()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > OUTER) continue
                val inRing = dist >= INNER
                if (!inRing && !showInner) continue
                var color = DotOff
                var glow = false
                if (inRing) {
                    var angle = (atan2(dx, -dy) / (2 * PI)).toFloat()
                    if (angle < 0) angle += 1f
                    if (ringOverride != null) {
                        color = ringOverride
                    } else if (angle <= level) {
                        val isHead = level - angle < 0.045f && level < 0.999f
                        color = if (isHead) accent else DotOn
                        glow = isHead
                    }
                } else if (glyph != null) {
                    val row = glyph.getOrNull(y - gy)
                    if (row != null && (x - gx) in row.indices && row[x - gx] == '#') color = glyphColor
                }
                val center = Offset(x * cell + cell / 2, y * cell + cell / 2)
                if (glow) drawCircle(color.copy(alpha = 0.35f), radius * 2.1f, center)
                drawCircle(color, radius, center)
            }
        }
    }
}

/**
 * Text drawn in 3×5 dots (digits, ':', '%' and space), for the clock and the charge percentage.
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

private const val GRID = 25
private const val CENTER = 12
private const val OUTER = 12.4f
private const val INNER = 10.5f
