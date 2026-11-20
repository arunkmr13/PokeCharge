package dev.arun.chargecatch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arun.chargecatch.CatchPhase
import dev.arun.chargecatch.ScreenSaverController
import dev.arun.chargecatch.battery.BatteryState
import dev.arun.chargecatch.battery.PlugType
import dev.arun.chargecatch.data.DisplayMode
import dev.arun.chargecatch.data.PendingEncounter
import dev.arun.chargecatch.data.SpeciesRepository
import dev.arun.chargecatch.sprites.SpriteRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val Muted = Color(0xFF9A9A9A)
private val Soft = Color(0xFFBDBDBD)
private val Shiny = Color(0xFFFFD66B)

@Composable
fun ScreenSaverScreen(controller: ScreenSaverController, onExit: () -> Unit) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    val battery by controller.battery.collectAsStateWithLifecycle()
    val pending by controller.pending.collectAsStateWithLifecycle()
    val visible by controller.visible.collectAsStateWithLifecycle()
    val phase by controller.phase.collectAsStateWithLifecycle()
    val now = rememberMinuteClock()
    val accent = Color(settings.accent)

    val shown = visible || settings.displayMode == DisplayMode.ALWAYS_ON
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "fade")

    // Shift everything a few pixels each minute so nothing burns into the OLED panel
    val shiftX = ((now.minute % 5) - 2) * 3
    val shiftY = (((now.minute / 5) % 5) - 2) * 3

    var dragTotal by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controller.onTap() }, onDoubleTap = { onExit() })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -200f) onExit() },
                    onVerticalDrag = { _, dy -> dragTotal += dy }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .alpha(alpha)
                .offset(shiftX.dp, shiftY.dp)
                .padding(top = 96.dp, bottom = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(now.format(CLOCK), DotOn, pitch = 13.dp)
                Spacer(Modifier.height(14.dp))
                Text(now.format(DATE), color = Muted, fontSize = 15.sp)
            }

            Center(battery, pending, phase, accent)

            Footer(battery, pending, phase, accent)
        }
    }
}

@Composable
private fun Center(battery: BatteryState?, pending: PendingEncounter?, phase: CatchPhase, accent: Color) {
    val level = (battery?.level ?: 0) / 100f
    Box(Modifier.size(340.dp), contentAlignment = Alignment.Center) {
        when {
            phase is CatchPhase.Caught -> DotRing(
                level = 1f,
                accent = accent,
                glyph = Glyphs.CHECK,
                ringOverride = accent
            )
            pending != null -> {
                DotRing(level = level, accent = accent, showInner = false)
                EncounterSprite(pending, throwing = phase is CatchPhase.Throwing)
            }
            battery?.full == true -> DotRing(level = 1f, accent = accent, glyph = Glyphs.FULL)
            else -> DotRing(level = level, accent = accent, glyph = Glyphs.BOLT)
        }
    }
}

@Composable
private fun EncounterSprite(pending: PendingEncounter, throwing: Boolean) {
    val context = LocalContext.current
    val sprite = remember(pending.speciesId, pending.shiny) {
        SpriteRepository.get(context).load(pending.speciesId, pending.shiny)
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY"
    )
    val scale by animateFloatAsState(if (throwing) 0f else 1f, tween(600), label = "throw")
    val modifier = Modifier
        .size(210.dp)
        .graphicsLayer {
            translationY = if (throwing) 0f else bob * density
            scaleX = scale
            scaleY = scale
            alpha = scale.coerceIn(0f, 1f)
        }
    if (sprite != null) {
        Image(
            bitmap = sprite,
            contentDescription = SpeciesRepository.byId(context, pending.speciesId)?.name,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None
        )
    } else {
        // No sprite file yet: a placeholder so the flow still works
        Box(
            modifier.border(2.dp, if (pending.shiny) Shiny else Color(0xFF5A5A5A), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "#" + pending.speciesId.toString().padStart(3, '0') + "\nno sprite",
                color = Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Footer(battery: BatteryState?, pending: PendingEncounter?, phase: CatchPhase, accent: Color) {
    val context = LocalContext.current
    AnimatedContent(
        targetState = when {
            phase is CatchPhase.Caught -> "caught"
            pending != null -> "encounter"
            else -> "battery"
        },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "footer"
    ) { state ->
        Column(
            Modifier.height(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
        ) {
            when (state) {
                "caught" -> {
                    val result = (phase as? CatchPhase.Caught)?.result
                    if (result != null) {
                        Text(
                            (if (result.shiny) "Shiny " else "") + result.species.name + " caught!",
                            color = if (result.shiny) Shiny else DotOn,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (result.isNew) "New Pokédex entry · ${result.registered} of ${result.total}"
                            else "Already registered · ${result.registered} of ${result.total}",
                            color = Muted,
                            fontSize = 14.sp
                        )
                    }
                }
                "encounter" -> {
                    val p = pending
                    val name = p?.let { SpeciesRepository.byId(context, it.speciesId)?.name } ?: "Pokémon"
                    if (p?.shiny == true) {
                        Text("SHINY", color = Shiny, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    }
                    Text("A wild $name appeared", color = DotOn, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (phase is CatchPhase.Throwing) "Catching…" else "Tap to catch",
                        color = accent,
                        fontSize = 15.sp
                    )
                }
                else -> {
                    DotText("${battery?.level ?: 0}%", DotOn, pitch = 8.dp)
                    Text(statusLine(battery), color = Soft, fontSize = 15.sp)
                    Text("Double-tap or swipe up to unlock", color = Color(0xFF8A8A8A), fontSize = 13.sp)
                }
            }
        }
    }
}

private fun statusLine(battery: BatteryState?): String {
    if (battery == null) return ""
    if (!battery.charging) return "Not charging"
    if (battery.full) return "Charged"
    val how = if (battery.plug == PlugType.WIRELESS) "Charging wirelessly" else "Charging"
    val minutes = battery.minutesToFull ?: return how
    val eta = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return "$how · Full in $eta"
}

@Composable
private fun rememberMinuteClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            delay((60 - current.second) * 1_000L - current.nano / 1_000_000L + 50)
        }
    }
    return now
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")
