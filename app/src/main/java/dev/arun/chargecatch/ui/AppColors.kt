package dev.arun.chargecatch.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The app's colours (Pokédex and Settings). The screen saver always stays black: it runs at night
 * on an OLED panel, where black pixels are off.
 */
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val panel: Color,
    val sheet: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val disabled: Color,
    val divider: Color,
    val track: Color,
    val silhouette: Color,
    val infoBackground: Color,
    val warnBackground: Color,
    val warnText: Color
) {
    /** The accent made dark enough to read as text and lines on a light background. */
    fun readableAccent(accent: Color): Color = if (isDark) accent else lerp(accent, Color.Black, 0.45f)
}

val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF0B0B0B),
    panel = Color(0xFF161616),
    sheet = Color(0xFF121212),
    text = Color(0xFFF2F2F2),
    soft = Color(0xFFBDBDBD),
    muted = Color(0xFF9A9A9A),
    disabled = Color(0xFF6A6A6A),
    divider = Color(0xFF222222),
    track = Color(0xFF222222),
    silhouette = Color(0xFF2A2A2A),
    infoBackground = Color(0xFF1E2430),
    warnBackground = Color(0xFF2A1E0E),
    warnText = Color(0xFFFFD39A)
)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFF6F6F4),
    panel = Color(0xFFFFFFFF),
    sheet = Color(0xFFFFFFFF),
    text = Color(0xFF151515),
    soft = Color(0xFF3D3D3D),
    muted = Color(0xFF5E5E5E),
    disabled = Color(0xFF9E9E9E),
    divider = Color(0xFFE2E2DE),
    track = Color(0xFFE2E2DE),
    silhouette = Color(0xFFD6D6D2),
    infoBackground = Color(0xFFE8EEF8),
    warnBackground = Color(0xFFFFF1DC),
    warnText = Color(0xFF6B4300)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
