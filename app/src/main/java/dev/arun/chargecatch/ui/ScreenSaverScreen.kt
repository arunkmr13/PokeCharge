package dev.arun.chargecatch.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arun.chargecatch.CatchPhase
import dev.arun.chargecatch.ScreenSaverController
import dev.arun.chargecatch.battery.BatteryState
import dev.arun.chargecatch.data.AppSettings
import dev.arun.chargecatch.data.ClockStyle
import dev.arun.chargecatch.data.DisplayMode
import dev.arun.chargecatch.data.PendingEncounter
import dev.arun.chargecatch.data.SpeciesRepository
import dev.arun.chargecatch.info.AppIcon
import dev.arun.chargecatch.info.NextEvent
import dev.arun.chargecatch.info.NowPlaying
import dev.arun.chargecatch.info.Weather
import dev.arun.chargecatch.sprites.SpriteRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Muted = Color(0xFF9A9A9A)
private val Soft = Color(0xFFBDBDBD)
private val Faint = Color(0xFF8A8A8A)
private val Pill = Color(0xFF141414)
private val Shiny = Color(0xFFFFD66B)

@Composable
fun ScreenSaverScreen(controller: ScreenSaverController, onExit: () -> Unit) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    val battery by controller.battery.collectAsStateWithLifecycle()
    val pending by controller.pending.collectAsStateWithLifecycle()
    val visible by controller.visible.collectAsStateWithLifecycle()
    val phase by controller.phase.collectAsStateWithLifecycle()
    val minutesToNext by controller.minutesToNext.collectAsStateWithLifecycle()
    val weather by controller.weather.collectAsStateWithLifecycle()
    val nextEvent by controller.nextEvent.collectAsStateWithLifecycle()
    val nowPlaying by controller.nowPlaying.collectAsStateWithLifecycle()
    val appIcons by controller.appIcons.collectAsStateWithLifecycle()
    val now = rememberMinuteClock()
    val accent = Color(settings.accent)

    val shown = visible || settings.displayMode == DisplayMode.ALWAYS_ON
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "fade")

    // Burn-in protection: drift everything by a couple of pixels each minute, gliding there over
    // a few seconds so the move is never noticeable
    val shiftX by animateDpAsState((((now.minute % 5) - 2) * 1.5f).dp, tween(4_000), label = "shiftX")
    val shiftY by animateDpAsState(((((now.minute / 5) % 5) - 2) * 1.5f).dp, tween(4_000), label = "shiftY")

    var dragTotal by remember { mutableStateOf(0f) }

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = GoogleSans, color = DotOn)) {
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
                    .offset(shiftX, shiftY)
                    .padding(top = 76.dp, bottom = 36.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Header(
                    now = now,
                    clockStyle = settings.clockStyle,
                    weather = weather.takeIf { settings.showWeather },
                    event = nextEvent.takeIf { settings.showCalendar },
                    accent = accent
                )
                Center(battery, pending, phase, accent, settings)
                Footer(battery, pending, phase, accent, minutesToNext, settings)
                Bottom(
                    nowPlaying = nowPlaying.takeIf { settings.showNowPlaying },
                    icons = if (settings.showNotifications) appIcons else emptyList(),
                    minutesToNext = minutesToNext
                )
            }
        }
    }
}

@Composable
private fun Header(
    now: LocalDateTime,
    clockStyle: ClockStyle,
    weather: Weather?,
    event: NextEvent?,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val time = now.format(if (DateFormat.is24HourFormat(LocalContext.current)) CLOCK_24 else CLOCK_12)
        when (clockStyle) {
            ClockStyle.DOT -> {
                DotText(time, DotOn, pitch = 13.dp)
                Spacer(Modifier.height(14.dp))
            }
            ClockStyle.POKE -> {
                PokeClock(time)
                Spacer(Modifier.height(10.dp))
            }
            ClockStyle.SANS -> {
                Text(
                    time,
                    fontSize = 104.sp,
                    fontWeight = FontWeight(300),
                    letterSpacing = (-3).sp,
                    lineHeight = 104.sp,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        // Date and weather share one centred line under the clock
        Text(
            listOfNotNull(now.format(DATE), weather?.label).joinToString("  ·  "),
            color = Muted,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AnimatedVisibility(visible = event != null) {
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .background(Pill, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    event?.label() ?: "",
                    color = Color(0xFFD6D6D6),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Chunky yellow digits in the colours of the Pokémon logo: each digit drawn on its own with a
 * slight tilt and bounce, a navy rim around a blue outline, a yellow gradient with a soft gloss,
 * and a short extruded shadow down and to the right. The colon is two matching dots.
 */
@Composable
private fun PokeClock(time: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy((-4).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var digitIndex = 0
        time.forEach { c ->
            if (c == ':') {
                PokeColon()
            } else {
                val i = digitIndex++
                PokeDigit(
                    c.toString(),
                    tilt = DIGIT_TILTS[i % DIGIT_TILTS.size],
                    lift = if (i % 2 == 0) 0.dp else (-5).dp
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun PokeDigit(digit: String, tilt: Float, lift: androidx.compose.ui.unit.Dp) {
    val base = TextStyle(fontFamily = PokeDisplay, fontSize = 78.sp, lineHeight = 86.sp)
    val rim = Stroke(width = RIM_PX, join = StrokeJoin.Round)
    Box(
        Modifier
            .offset(y = lift)
            .graphicsLayer { rotationZ = tilt }
    ) {
        // Extruded shadow: the rimmed digit repeated a step further down-right each time
        for (step in EXTRUDE_STEPS downTo 1) {
            val shift = Modifier.offset((step * 0.9f).dp, (step * 1.2f).dp)
            Text(digit, style = base.copy(color = PokeNavy, drawStyle = rim), modifier = shift)
            Text(digit, style = base.copy(color = PokeNavy), modifier = shift)
        }
        Text(digit, style = base.copy(color = PokeNavy, drawStyle = rim))
        Text(digit, style = base.copy(color = PokeBlue, drawStyle = Stroke(width = OUTLINE_PX, join = StrokeJoin.Round)))
        Text(digit, style = base.copy(brush = Brush.verticalGradient(listOf(PokeYellowLight, PokeYellow, PokeYellowDeep))))
        // Gloss on the upper half
        Text(
            digit,
            style = base.copy(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.45f),
                    0.42f to Color.White.copy(alpha = 0f)
                )
            )
        )
    }
}

@Composable
private fun PokeColon() {
    Column(
        Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(2) {
            Box {
                Box(
                    Modifier
                        .offset(3.dp, 4.dp)
                        .size(17.dp)
                        .background(PokeNavy, CircleShape)
                )
                Box(
                    Modifier
                        .size(17.dp)
                        .background(PokeNavy, CircleShape)
                        .padding(2.dp)
                        .background(PokeBlue, CircleShape)
                        .padding(3.dp)
                        .background(
                            Brush.verticalGradient(listOf(PokeYellowLight, PokeYellowDeep)),
                            CircleShape
                        )
                )
            }
        }
    }
}

private const val MAX_ICONS_SHOWN = 6
private val DIGIT_TILTS = listOf(-4f, 3f, -3f, 4f)
private const val EXTRUDE_STEPS = 5
private const val RIM_PX = 34f
private const val OUTLINE_PX = 20f
private val PokeYellowLight = Color(0xFFFFE867)
private val PokeYellow = Color(0xFFFFCB05)
private val PokeYellowDeep = Color(0xFFF5B400)
private val PokeBlue = Color(0xFF2F62C2)
private val PokeNavy = Color(0xFF16234F)

@Composable
private fun NotificationIcons(icons: List<AppIcon>) {
    if (icons.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        icons.take(MAX_ICONS_SHOWN).forEach { appIcon ->
            val bitmap = remember(appIcon) { appIcon.icon.asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Soft)
            )
        }
        if (icons.size > MAX_ICONS_SHOWN) Text("+${icons.size - MAX_ICONS_SHOWN}", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Center(
    battery: BatteryState?,
    pending: PendingEncounter?,
    phase: CatchPhase,
    accent: Color,
    settings: AppSettings
) {
    val level = (battery?.level ?: 0) / 100f
    val charging = battery?.charging != false
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        ChargeRing(
            level = if (battery?.full == true) 1f else level,
            accent = accent,
            size = 300.dp,
            charging = charging && battery?.full != true,
            complete = phase is CatchPhase.Caught
        )
        when {
            phase is CatchPhase.Caught -> DotGlyph(Glyphs.CHECK, accent)
            pending != null -> EncounterSprite(pending, throwing = phase is CatchPhase.Throwing)
            settings.encounters -> WobblingBall()
            battery?.full == true -> DotGlyph(Glyphs.FULL, accent)
            else -> DotGlyph(Glyphs.BOLT, accent)
        }
    }
}

/**
 * The ball while the next Pokémon is on its way: three quick shakes, then a pause, like a catch
 * in progress. Uses assets/items/ball.png; falls back to the bolt glyph if it's missing.
 */
@Composable
private fun WobblingBall() {
    val context = LocalContext.current
    val ball = remember { SpriteRepository.get(context).loadItem("ball.png") }
    if (ball == null) {
        DotGlyph(Glyphs.BOLT, Faint)
        return
    }
    val angle by rememberInfiniteTransition(label = "wobble").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2_600
                0f at 0
                -16f at 110
                14f at 260
                -12f at 410
                9f at 560
                -4f at 680
                0f at 780
                0f at 2_600
            }
        ),
        label = "wobbleAngle"
    )
    Image(
        bitmap = ball,
        contentDescription = "Waiting for the next Pokémon",
        modifier = Modifier
            .size(104.dp)
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(0.5f, 0.92f)
            },
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.Medium
    )
}

@Composable
private fun EncounterSprite(pending: PendingEncounter, throwing: Boolean) {
    val context = LocalContext.current
    val sprites = remember { SpriteRepository.get(context) }
    // Reload when the first-launch download brings in a sprite that was missing
    val revision by sprites.revision.collectAsState()
    val sprite = remember(pending.speciesId, pending.shiny, revision) {
        sprites.load(pending.speciesId, pending.shiny)
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bobY"
    )
    val scale by animateFloatAsState(if (throwing) 0f else 1f, tween(600), label = "throw")
    val modifier = Modifier
        .size(184.dp)
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
private fun Footer(
    battery: BatteryState?,
    pending: PendingEncounter?,
    phase: CatchPhase,
    accent: Color,
    minutesToNext: Int?,
    settings: AppSettings
) {
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
            Modifier.height(128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            when (state) {
                "caught" -> {
                    val result = (phase as? CatchPhase.Caught)?.result
                    if (result != null) {
                        Text(
                            (if (result.shiny) "Shiny " else "") + result.species.name + " caught!",
                            color = if (result.shiny) Shiny else DotOn,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (result.isNew) "New Pokédex entry · ${result.registered} of ${result.total}"
                            else "Already registered · ${result.registered} of ${result.total}",
                            color = Muted,
                            fontSize = 15.sp
                        )
                    }
                }
                "encounter" -> {
                    val p = pending
                    val name = p?.let { SpeciesRepository.byId(context, it.speciesId)?.name } ?: "Pokémon"
                    if (p?.shiny == true) {
                        Text("SHINY", color = Shiny, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    }
                    Text("A wild $name appeared", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (phase is CatchPhase.Throwing) "Catching…" else "Tap to catch",
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${battery?.level ?: 0}%" + (minutesToNext?.let { " · runs off in $it min" } ?: ""),
                        color = Faint,
                        fontSize = 13.sp
                    )
                }
                else -> {
                    if (settings.clockStyle == ClockStyle.DOT) {
                        DotText("${battery?.level ?: 0}%", DotOn, pitch = 8.dp)
                    } else {
                        Text(
                            "${battery?.level ?: 0}%",
                            fontSize = 44.sp,
                            fontWeight = FontWeight(300),
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                    }
                    Text(chargingLine(battery, settings.showChargingDetails), color = Soft, fontSize = 15.sp)
                    timeToFull(battery)?.let { Text(it, color = Faint, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun Bottom(nowPlaying: NowPlaying?, icons: List<AppIcon>, minutesToNext: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Apps with notifications, just above the music player
        NotificationIcons(icons)
        AnimatedVisibility(visible = nowPlaying != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Pill, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).background(Color(0xFF262626), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♪", color = Muted, fontSize = 18.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nowPlaying?.title ?: "",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = listOfNotNull(nowPlaying?.artist, nowPlaying?.appLabel).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (minutesToNext != null) {
            Text(
                if (minutesToNext <= 1) "Next Pokémon in a minute" else "Next Pokémon in $minutesToNext min",
                color = Faint,
                fontSize = 13.sp
            )
        }
    }
}

/** "Fast charging · 27 W · 34 °C", or just the speed when details are off. */
private fun chargingLine(battery: BatteryState?, details: Boolean): String {
    if (battery == null) return ""
    if (!battery.charging) return "Not charging"
    if (battery.full) return "Charged"
    if (!details) return battery.speedLabel
    return listOfNotNull(
        battery.speedLabel,
        battery.watts?.let { "${it.roundToInt()} W" },
        battery.temperatureC?.let { "${it.roundToInt()} °C" }
    ).joinToString(" · ")
}

private fun timeToFull(battery: BatteryState?): String? {
    if (battery == null || !battery.charging || battery.full) return null
    val minutes = battery.minutesToFull ?: return null
    return if (minutes >= 60) "Full in ${minutes / 60} h ${minutes % 60} min" else "Full in $minutes min"
}

/**
 * The current time, updated by the system's own minute tick (ACTION_TIME_TICK) and whenever the
 * time or time zone changes, so it always matches the status bar.
 */
@Composable
private fun rememberMinuteClock(): LocalDateTime {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                now = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        now = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

private val CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm")
private val CLOCK_12 = DateTimeFormatter.ofPattern("h:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM")
