package dev.arun.chargecatch

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arun.chargecatch.data.AppDatabase
import dev.arun.chargecatch.data.AppSettings
import dev.arun.chargecatch.data.AppTheme
import dev.arun.chargecatch.data.SpeciesStats
import dev.arun.chargecatch.data.ClockStyle
import dev.arun.chargecatch.data.DisplayMode
import dev.arun.chargecatch.data.Region
import dev.arun.chargecatch.data.SettingsRepository
import dev.arun.chargecatch.data.Species
import dev.arun.chargecatch.data.SpeciesRepository
import dev.arun.chargecatch.game.Encounters
import dev.arun.chargecatch.info.CalendarRepository
import dev.arun.chargecatch.info.DexDescription
import dev.arun.chargecatch.info.LocationSaver
import dev.arun.chargecatch.info.NotificationHub
import dev.arun.chargecatch.info.NowPlayingListener
import dev.arun.chargecatch.sprites.SpriteRepository
import dev.arun.chargecatch.ui.AppTypography
import dev.arun.chargecatch.ui.DarkAppColors
import dev.arun.chargecatch.ui.LightAppColors
import dev.arun.chargecatch.ui.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The current palette's colours (light or dark), read where they're drawn
private val Panel: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.panel
private val Muted: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.muted
private val Soft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.soft
private val Ink: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.text
private val ShinyGold = Color(0xFFFFD66B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First launch: fetch the Pokémon sprites in the background (no-op once they're all here)
        SpriteRepository.get(this).downloadMissing()
        setContent {
            val repo = remember { SettingsRepository(this) }
            val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }
            val colors = if (dark) DarkAppColors else LightAppColors
            // Status and navigation bar icons follow the app's theme, not just the system's
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                )
            }
            CompositionLocalProvider(LocalAppColors provides colors) {
                MaterialTheme(
                    colorScheme = if (dark) {
                        darkColorScheme(background = colors.background, surface = colors.background)
                    } else {
                        lightColorScheme(background = colors.background, surface = colors.background)
                    },
                    typography = AppTypography
                ) {
                    Surface(Modifier.fillMaxSize(), color = colors.background) {
                        App()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val accent = Color(settings.accent)
    val accentInk = LocalAppColors.current.readableAccent(accent)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Save the approximate location for the weather while the app is open
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        NotificationHub.rebind(context)
        if (settings.showWeather) {
            CoroutineScopeHolder.launchIO { LocationSaver.refresh(context) }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
        )
        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = accentInk,
            divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.divider)) },
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(tab, matchContentSize = true),
                    width = Dp.Unspecified,
                    color = accentInk
                )
            }
        ) {
            listOf("Pokédex", "Settings").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    selectedContentColor = accentInk,
                    unselectedContentColor = Muted,
                    text = { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                )
            }
        }
        when (tab) {
            0 -> PokedexTab(settings, accent)
            else -> SettingsTab(settings, accent, repo)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PokedexTab(settings: AppSettings, accent: Color) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).caughtDao() }
    val dexFlow = remember { dao.watchDex() }
    val dex by dexFlow.collectAsStateWithLifecycle(emptyList())
    val byId = remember(dex) { dex.associateBy { it.speciesId } }
    val species = remember(settings.region) {
        SpeciesRepository.all(context).filter { it.id <= settings.region.maxId }
    }
    val registered = species.count { it.id in byId }
    var filterChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val filter = filterChoice ?: if (registered > 0) "caught" else "all"
    val shownSpecies = remember(species, byId, filter) {
        when (filter) {
            "caught" -> species.filter { it.id in byId }.sortedByDescending { byId[it.id]?.lastCaughtAt ?: 0L }
            "missing" -> species.filter { it.id !in byId }
            else -> species
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("$registered of ${species.size} registered", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (species.isEmpty()) 0f else registered / species.size.toFloat() },
                    color = LocalAppColors.current.readableAccent(accent),
                    trackColor = LocalAppColors.current.track,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("caught" to "Caught · $registered", "all" to "All", "missing" to "Missing").forEach { (key, label) ->
                        Chip(label, filter == key, accent) { filterChoice = key }
                    }
                }
            }
        }
        if (shownSpecies.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (filter == "caught") "Nothing caught yet. Plug in and wait for the first one." else "All caught!",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(shownSpecies, key = { it.id }) { s ->
            val entry = byId[s.id]
            DexTile(
                s,
                caught = entry != null,
                shiny = entry?.anyShiny == true,
                count = entry?.count ?: 0,
                onClick = if (entry != null) ({ selectedId = s.id }) else null
            )
        }
    }

    selectedId?.let { id ->
        SpeciesRepository.byId(context, id)?.let { species ->
            DexDetailSheet(species, accent, shinyCaught = byId[id]?.anyShiny == true) { selectedId = null }
        }
    }
}

/**
 * Details for a caught Pokémon: big sprite (switch to shiny if you've caught one), type,
 * category, size, rarity, evolutions, and your own catch history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DexDetailSheet(species: Species, accent: Color, shinyCaught: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).caughtDao() }
    val statsFlow = remember(species.id) { dao.watchStats(species.id) }
    val stats by statsFlow.collectAsStateWithLifecycle(null)
    var showShiny by rememberSaveable(species.id) { mutableStateOf(false) }
    val revision by SpriteRepository.get(context).revision.collectAsStateWithLifecycle()
    val sprite by produceState<ImageBitmap?>(null, species.id, showShiny, revision) {
        value = withContext(Dispatchers.IO) { SpriteRepository.get(context).load(species.id, showShiny) }
    }
    val description by produceState(DexDescription.cached(context, species.id), species.id) {
        if (value == null) value = DexDescription.get(context, species.id)
    }
    val evolvesFrom = remember(species.id) { species.evolvesFrom?.let { SpeciesRepository.byId(context, it) } }
    val evolvesInto = remember(species.id) { SpeciesRepository.evolutionsOf(context, species.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalAppColors.current.sheet
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                sprite?.let {
                    Image(
                        bitmap = it,
                        contentDescription = species.name,
                        modifier = Modifier.fillMaxSize(),
                        filterQuality = FilterQuality.None
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#${species.number}", color = Muted, fontSize = 14.sp)
                Text(species.name, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                if (species.genus.isNotEmpty()) Text(species.genus, color = Muted, fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                species.types.forEach { type -> TypeBadge(type) }
            }
            if (shinyCaught) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Normal", !showShiny, accent) { showShiny = false }
                    Chip("Shiny ★", showShiny, accent) { showShiny = true }
                }
            }

            DetailCard {
                Text(
                    description ?: "Pokédex entry loads when you're online.",
                    color = if (description != null) Ink else Muted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            DetailCard {
                DetailLine("Height", species.heightM)
                DetailLine("Weight", species.weightKg)
                DetailLine("Rarity", species.rarity)
                DetailLine("Region", Region.entries.first { species.id <= it.maxId }.label)
                evolvesFrom?.let { DetailLine("Evolves from", it.name) }
                if (evolvesInto.isNotEmpty()) DetailLine("Evolves into", evolvesInto.joinToString(", ") { it.name })
            }

            stats?.let { s ->
                DetailCard {
                    DetailLine("Times caught", s.count.toString())
                    if (s.shinyCount > 0) DetailLine("Shiny catches", s.shinyCount.toString())
                    s.firstCaughtAt?.let { DetailLine("First caught", formatDate(it)) }
                    if (s.count > 1) s.lastCaughtAt?.let { DetailLine("Last caught", formatDate(it)) }
                    s.longestCharge?.takeIf { it > 0 }?.let { DetailLine("Longest charge at catch", formatMinutes(it)) }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 15.sp)
        Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** A type label in its usual game colour. */
@Composable
private fun TypeBadge(type: String) {
    val color = TYPE_COLORS[type] ?: Color(0xFF777777)
    Box(
        Modifier
            .background(color, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(type, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val TYPE_COLORS = mapOf(
    "Normal" to Color(0xFF8A8A6B), "Fire" to Color(0xFFD9652B), "Water" to Color(0xFF4A7BD9),
    "Electric" to Color(0xFFB8960F), "Grass" to Color(0xFF4E9A3A), "Ice" to Color(0xFF4FA3A3),
    "Fighting" to Color(0xFFA32B24), "Poison" to Color(0xFF8A3A8A), "Ground" to Color(0xFF9E7E3A),
    "Flying" to Color(0xFF7A68C4), "Psychic" to Color(0xFFD9466E), "Bug" to Color(0xFF7A8A1A),
    "Rock" to Color(0xFF8E7A30), "Ghost" to Color(0xFF5C4A86), "Dragon" to Color(0xFF5B30D9),
    "Dark" to Color(0xFF5A473A), "Steel" to Color(0xFF7A7A99), "Fairy" to Color(0xFFC2578F)
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)

private fun formatMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

@Composable
private fun DexTile(species: Species, caught: Boolean, shiny: Boolean, count: Int, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val revision by SpriteRepository.get(context).revision.collectAsStateWithLifecycle()
    val sprite by produceState<ImageBitmap?>(null, species.id, shiny, revision) {
        value = withContext(Dispatchers.IO) { SpriteRepository.get(context).load(species.id, shiny) }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .then(if (shiny) Modifier.border(1.dp, ShinyGold, RoundedCornerShape(16.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show details", onClick = onClick) else Modifier)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            val bitmap = sprite
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = if (caught) species.name else "Not caught yet",
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    filterQuality = FilterQuality.None,
                    colorFilter = if (caught) null else ColorFilter.tint(LocalAppColors.current.silhouette)
                )
            } else {
                Text("?", color = LocalAppColors.current.disabled, fontSize = 22.sp)
            }
        }
        Text(
            if (caught) species.name else "#${species.number}",
            color = if (caught) Ink else Muted,
            fontSize = 12.sp,
            maxLines = 1
        )
        if (count > 1) Text("×$count", color = Muted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTab(settings: AppSettings, accent: Color, repo: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sprites = remember { SpriteRepository.get(context) }
    val spriteRevision by sprites.revision.collectAsStateWithLifecycle()
    val spriteDownload by sprites.download.collectAsStateWithLifecycle()
    val spriteCount = remember(spriteRevision, spriteDownload.running) { sprites.spriteCount }
    val gestures = settings.displayMode == DisplayMode.WAKE_ON_GESTURE
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(change) }
    }

    // Permission state, re-read whenever the app comes back to the front
    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val hasLocation = remember(permissionTick) { LocationSaver.hasPermission(context) }
    val hasCalendar = remember(permissionTick) { CalendarRepository.hasPermission(context) }
    val hasNotificationAccess = remember(permissionTick) { NotificationHub.hasAccess(context) }
    val listenerConnected by NotificationHub.connected.collectAsStateWithLifecycle()
    val appIconCount by NotificationHub.appIcons.collectAsStateWithLifecycle()
    val notificationStatus = when {
        !hasNotificationAccess -> null
        !listenerConnected -> "Access on · reconnecting (reopen the app if this stays)"
        else -> "Connected · ${appIconCount.size} apps with notifications now"
    }

    fun openAppInfo() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    // After two "Don't allow"s Android stops showing the prompt; then the app's own settings page is the only way
    fun deniedForGood(permission: String): Boolean {
        val activity = context as? Activity ?: return false
        return !activity.shouldShowRequestPermissionRationale(permission)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionTick++
        if (granted) scope.launch { LocationSaver.refresh(context) }
        else if (deniedForGood(Manifest.permission.ACCESS_COARSE_LOCATION)) openAppInfo()
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionTick++
        if (!granted && deniedForGood(Manifest.permission.READ_CALENDAR)) openAppInfo()
    }
    fun openNotificationAccess() {
        // Straight to PokéCharge's own switch where the phone supports it, otherwise the list
        val direct = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, NowPlayingListener::class.java).flattenToString()
        )
        runCatching { context.startActivity(direct) }
            .onFailure { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (spriteCount < SpriteRepository.TOTAL_SPRITES) {
            val message = when {
                spriteDownload.running ->
                    "Downloading Pokémon sprites… ${spriteDownload.done} of ${spriteDownload.total}. " +
                        "Keep the app open; it only happens once."
                else ->
                    "$spriteCount of ${SpriteRepository.TOTAL_SPRITES} Pokémon sprites downloaded. " +
                        "Connect to the internet and tap here to fetch the rest."
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.warnBackground)
                    .then(if (spriteDownload.running) Modifier else Modifier.clickable { sprites.downloadMissing() })
                    .padding(16.dp)
            ) {
                Text(message, color = LocalAppColors.current.warnText, fontSize = 14.sp)
            }
        }

        Section("Display") {
            RadioRow("Always on while charging", "Animation stays on until you unplug", !gestures, accent) {
                update { it.copy(displayMode = DisplayMode.ALWAYS_ON) }
            }
            RadioRow("Black until woken", "Screen looks off; wake it with a gesture", gestures, accent) {
                update { it.copy(displayMode = DisplayMode.WAKE_ON_GESTURE) }
            }
        }

        Section("Wake gestures") {
            SwitchRow("Tap to wake", "Tap the black screen to show it", settings.tapToWake, gestures, accent) { on ->
                update { it.copy(tapToWake = on) }
            }
            SwitchRow("Lift to check", "Tilt the phone up · best on a cable", settings.liftToCheck, gestures, accent) { on ->
                update { it.copy(liftToCheck = on) }
            }
            Text("Stay visible for", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.VISIBLE_SECONDS.forEach { seconds ->
                    Chip("$seconds s", settings.visibleSeconds == seconds, accent, enabled = gestures) {
                        update { it.copy(visibleSeconds = seconds) }
                    }
                }
            }
        }

        Section("Pokémon") {
            SwitchRow("Encounters while charging", "A Pokémon shows up as soon as you plug in", settings.encounters, true, accent) { on ->
                update { it.copy(encounters = on) }
            }
            Text("New Pokémon every", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.ENCOUNTER_MINUTES.forEach { minutes ->
                    Chip("$minutes min", settings.encounterMinutes == minutes, accent, enabled = settings.encounters) {
                        update { it.copy(encounterMinutes = minutes) }
                    }
                }
            }
            Text("Pokémon pool", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            FlowRow(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Region.entries.forEach { region ->
                    Chip("${region.label} · ${region.maxId}", settings.region == region, accent) {
                        update { it.copy(region = region) }
                    }
                }
            }
        }

        Section("On the screen") {
            val missing = listOf(
                Triple("Location", "for weather", hasLocation || !settings.showWeather),
                Triple("Calendar", "for your next event", hasCalendar || !settings.showCalendar),
                Triple(
                    "Notification access",
                    "for now playing and icons",
                    hasNotificationAccess || (!settings.showNowPlaying && !settings.showNotifications)
                )
            ).filterNot { it.third }
            if (missing.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(LocalAppColors.current.infoBackground, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "These items stay hidden until you allow access",
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    missing.forEach { (name, why, _) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$name $why", color = Soft, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    when (name) {
                                        "Location" -> locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        "Calendar" -> calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                                        else -> openNotificationAccess()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                            ) { Text("Allow") }
                        }
                    }
                    if (missing.any { it.first == "Notification access" }) {
                        Text(
                            "If the PokéCharge switch is greyed out, Android is protecting a sideloaded app: " +
                                "open App info, tap ⋮ (top right), choose Allow restricted settings, then try again.",
                            color = Muted,
                            fontSize = 13.sp
                        )
                        OutlinedButton(onClick = { openAppInfo() }) { Text("Open App info", color = Ink) }
                    }
                }
            }
            InfoRow(
                title = "Weather",
                subtitle = if (hasLocation) "From your approximate location, every 30 min" else "Needs approximate location · tap to allow",
                checked = settings.showWeather,
                accent = accent
            ) { on ->
                update { it.copy(showWeather = on) }
                if (on && !hasLocation) locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            InfoRow(
                title = "Next calendar event",
                subtitle = if (hasCalendar) "Next timed event in the coming 24 hours" else "Needs calendar access · tap to allow",
                checked = settings.showCalendar,
                accent = accent
            ) { on ->
                update { it.copy(showCalendar = on) }
                if (on && !hasCalendar) calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
            InfoRow(
                title = "Charging speed and temperature",
                subtitle = "Watts going in and battery temperature",
                checked = settings.showChargingDetails,
                accent = accent
            ) { on -> update { it.copy(showChargingDetails = on) } }
            InfoRow(
                title = "Now playing",
                subtitle = if (hasNotificationAccess) "Track and artist from your music app" else "Needs notification access · tap to allow",
                checked = settings.showNowPlaying,
                accent = accent
            ) { on ->
                update { it.copy(showNowPlaying = on) }
                if (on && !hasNotificationAccess) openNotificationAccess()
            }
            InfoRow(
                title = "Notification icons",
                subtitle = notificationStatus ?: "Needs notification access · tap to allow",
                checked = settings.showNotifications,
                accent = accent
            ) { on ->
                update { it.copy(showNotifications = on) }
                if (on && !hasNotificationAccess) openNotificationAccess()
            }
        }

        Section("Appearance") {
            Text(
                "App theme · the screen saver always stays black",
                color = Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp)
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    Chip(theme.label, settings.appTheme == theme, accent) {
                        update { it.copy(appTheme = theme) }
                    }
                }
            }
        }

        Section("Clock") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockStyle.entries.forEach { style ->
                    Chip(style.label, settings.clockStyle == style, accent) {
                        update { it.copy(clockStyle = style) }
                    }
                }
            }
        }

        Section("Accent") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppSettings.ACCENTS.forEach { argb ->
                    val selected = settings.accent == argb
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color(argb), CircleShape)
                            .border(3.dp, if (selected) Ink else Color.Transparent, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { update { it.copy(accent = argb) } }
                    )
                }
            }
        }

        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) },
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Open Pixel screen saver settings") }
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, PreviewActivity::class.java)) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Preview the screen saver", color = Ink) }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val spawned = Encounters.spawn(context, minutesCharging = 60)
                    val message = if (spawned == null) "A Pokémon is already waiting" else "A Pokémon is waiting: open the preview"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Spawn a test encounter", color = Ink) }
        Text(
            "In the screen saver: double-tap or swipe up to leave for the lock screen. $spriteCount sprites loaded. " +
                "Text set in Google Sans Flex (SIL Open Font License).",
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A switch row for an info item. Tapping it while it's on but missing its permission asks again,
 * instead of turning it off.
 */
@Composable
private fun InfoRow(title: String, subtitle: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    val needsPermission = subtitle.contains("tap to allow")
    SwitchRow(title, subtitle, checked, true, accent) { on ->
        if (checked && needsPermission) onChange(true) else onChange(on)
    }
}

/** App-wide scope for small background jobs started from the UI (saving the location). */
private object CoroutineScopeHolder {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    fun launchIO(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))) { content() }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accent))
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, color = Ink, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Ink else LocalAppColors.current.disabled, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black)
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent,
            selectedLabelColor = Color.Black
        )
    )
}
