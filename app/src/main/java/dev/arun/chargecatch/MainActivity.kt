package dev.arun.chargecatch

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arun.chargecatch.data.AppDatabase
import dev.arun.chargecatch.data.AppSettings
import dev.arun.chargecatch.data.DisplayMode
import dev.arun.chargecatch.data.Region
import dev.arun.chargecatch.data.SettingsRepository
import dev.arun.chargecatch.data.Species
import dev.arun.chargecatch.data.SpeciesRepository
import dev.arun.chargecatch.game.Encounters
import dev.arun.chargecatch.sprites.SpriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Panel = Color(0xFF161616)
private val Muted = Color(0xFF9A9A9A)
private val ShinyGold = Color(0xFFFFD66B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0B0B0B), surface = Color(0xFF0B0B0B))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0B0B)) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val settings by repo.settings.collectAsStateWithLifecycle(AppSettings())
    val accent = Color(settings.accent)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            "Charge Catch",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF2F2F2),
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
        )
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = accent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pokédex") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Settings") })
        }
        when (tab) {
            0 -> PokedexTab(settings, accent)
            else -> SettingsTab(settings, accent, repo)
        }
    }
}

@Composable
private fun PokedexTab(settings: AppSettings, accent: Color) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).caughtDao() }
    val dex by dao.watchDex().collectAsStateWithLifecycle(emptyList())
    val byId = remember(dex) { dex.associateBy { it.speciesId } }
    val species = remember(settings.region) {
        SpeciesRepository.all(context).filter { it.id <= settings.region.maxId }
    }
    val registered = species.count { it.id in byId }

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
                    color = accent,
                    trackColor = Color(0xFF222222),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        items(species, key = { it.id }) { s ->
            val entry = byId[s.id]
            DexTile(s, caught = entry != null, shiny = entry?.anyShiny == true, count = entry?.count ?: 0)
        }
    }
}

@Composable
private fun DexTile(species: Species, caught: Boolean, shiny: Boolean, count: Int) {
    val context = LocalContext.current
    val sprite by produceState<ImageBitmap?>(null, species.id, shiny) {
        value = withContext(Dispatchers.IO) { SpriteRepository.get(context).load(species.id, shiny) }
    }
    Column(
        Modifier
            .background(Panel, RoundedCornerShape(16.dp))
            .then(if (shiny) Modifier.border(1.dp, ShinyGold, RoundedCornerShape(16.dp)) else Modifier)
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
                    colorFilter = if (caught) null else ColorFilter.tint(Color(0xFF2A2A2A))
                )
            } else {
                Text("?", color = Color(0xFF5A5A5A), fontSize = 22.sp)
            }
        }
        Text(
            if (caught) species.name else "#${species.number}",
            color = if (caught) Color(0xFFF2F2F2) else Muted,
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
    val spriteCount = remember { SpriteRepository.get(context).availableIds.size }
    val gestures = settings.displayMode == DisplayMode.WAKE_ON_GESTURE
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(change) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (spriteCount == 0) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF2A1E0E), RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text(
                    "No sprites found. Copy the Gen5 PNGs into app/src/main/assets/sprites/ " +
                        "(shiny ones into sprites/shiny/) and rebuild. Placeholders show until then.",
                    color = Color(0xFFFFD39A),
                    fontSize = 14.sp
                )
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
            SwitchRow("Encounters while charging", "A Pokémon can appear after 15 min on the charger", settings.encounters, true, accent) { on ->
                update { it.copy(encounters = on) }
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

        Section("Accent") {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppSettings.ACCENTS.forEach { argb ->
                    val selected = settings.accent == argb
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color(argb), CircleShape)
                            .border(3.dp, if (selected) Color(0xFFF2F2F2) else Color.Transparent, CircleShape)
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
        ) { Text("Preview the screen saver", color = Color(0xFFF2F2F2)) }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val spawned = Encounters.spawn(context, minutesCharging = 60)
                    val message = if (spawned == null) "A Pokémon is already waiting" else "A Pokémon is waiting: open the preview"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Spawn a test encounter", color = Color(0xFFF2F2F2)) }
        Text(
            "In the screen saver: double-tap or swipe up to leave for the lock screen. $spriteCount sprites loaded.",
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))
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
            Text(title, color = Color(0xFFF2F2F2), fontSize = 16.sp)
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
            Text(title, color = if (enabled) Color(0xFFF2F2F2) else Color(0xFF6A6A6A), fontSize = 16.sp)
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
