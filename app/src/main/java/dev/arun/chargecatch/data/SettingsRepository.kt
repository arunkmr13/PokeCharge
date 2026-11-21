package dev.arun.chargecatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class DisplayMode { ALWAYS_ON, WAKE_ON_GESTURE }

/** How the screen saver draws the clock and charge percentage. */
enum class ClockStyle(val label: String) { SANS("Google Sans"), POKE("Poké"), DOT("Dot matrix") }

/**
 * What the player chose in Settings.
 */
data class AppSettings(
    val displayMode: DisplayMode = DisplayMode.WAKE_ON_GESTURE,
    val tapToWake: Boolean = true,
    val liftToCheck: Boolean = true,
    val visibleSeconds: Int = 15,
    val accent: Long = ACCENTS.first(),
    val encounters: Boolean = true,
    /** A new Pokémon turns up this often while charging, caught or not. */
    val encounterMinutes: Int = 10,
    val clockStyle: ClockStyle = ClockStyle.SANS,
    val region: Region = Region.UNOVA,
    val showWeather: Boolean = true,
    val showCalendar: Boolean = true,
    val showChargingDetails: Boolean = true,
    val showNowPlaying: Boolean = true,
    val showNotifications: Boolean = true
) {
    companion object {
        val ACCENTS = listOf(0xFF7CFFB2, 0xFFFFB547, 0xFF7FB8FF, 0xFFFF7A6B)
        val VISIBLE_SECONDS = listOf(10, 15, 30)
        val ENCOUNTER_MINUTES = listOf(5, 10, 15, 30)
    }
}

/**
 * A Pokémon waiting to be caught. Survives the screen saver closing, so it is still there next charge.
 */
data class PendingEncounter(val speciesId: Int, val shiny: Boolean, val appearedAt: Long, val minutesCharging: Int)

/**
 * Charging time carried across screen saver restarts within one charge (waking the phone briefly
 * on the charger restarts the screen saver).
 */
data class ChargeSession(val minutes: Int, val minutesSinceEncounter: Int, val lastTickAt: Long)

/** Approximate position for the weather, saved while the app is open (rounded to about 1 km). */
data class SavedLocation(val latitude: Double, val longitude: Double)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            displayMode = p[DISPLAY_MODE]?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
                ?: DisplayMode.WAKE_ON_GESTURE,
            tapToWake = p[TAP_TO_WAKE] ?: true,
            liftToCheck = p[LIFT_TO_CHECK] ?: true,
            visibleSeconds = p[VISIBLE_SECONDS] ?: 15,
            accent = p[ACCENT] ?: AppSettings.ACCENTS.first(),
            encounters = p[ENCOUNTERS] ?: true,
            encounterMinutes = p[ENCOUNTER_MINUTES] ?: 10,
            clockStyle = p[CLOCK_STYLE]?.let { runCatching { ClockStyle.valueOf(it) }.getOrNull() } ?: ClockStyle.SANS,
            region = p[REGION]?.let { runCatching { Region.valueOf(it) }.getOrNull() } ?: Region.UNOVA,
            showWeather = p[SHOW_WEATHER] ?: true,
            showCalendar = p[SHOW_CALENDAR] ?: true,
            showChargingDetails = p[SHOW_CHARGING] ?: true,
            showNowPlaying = p[SHOW_NOW_PLAYING] ?: true,
            showNotifications = p[SHOW_NOTIFICATIONS] ?: true
        )
    }

    val location: Flow<SavedLocation?> = store.data.map { p ->
        val lat = p[LATITUDE] ?: return@map null
        val lon = p[LONGITUDE] ?: return@map null
        SavedLocation(lat, lon)
    }

    suspend fun saveLocation(location: SavedLocation) {
        store.edit { p ->
            p[LATITUDE] = Math.round(location.latitude * 100) / 100.0
            p[LONGITUDE] = Math.round(location.longitude * 100) / 100.0
        }
    }

    val pending: Flow<PendingEncounter?> = store.data.map { p ->
        val id = p[PENDING_ID] ?: return@map null
        PendingEncounter(id, p[PENDING_SHINY] ?: false, p[PENDING_AT] ?: 0L, p[PENDING_MINUTES] ?: 0)
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.first())
        store.edit { p ->
            p[DISPLAY_MODE] = next.displayMode.name
            p[TAP_TO_WAKE] = next.tapToWake
            p[LIFT_TO_CHECK] = next.liftToCheck
            p[VISIBLE_SECONDS] = next.visibleSeconds
            p[ACCENT] = next.accent
            p[ENCOUNTERS] = next.encounters
            p[ENCOUNTER_MINUTES] = next.encounterMinutes
            p[CLOCK_STYLE] = next.clockStyle.name
            p[REGION] = next.region.name
            p[SHOW_WEATHER] = next.showWeather
            p[SHOW_CALENDAR] = next.showCalendar
            p[SHOW_CHARGING] = next.showChargingDetails
            p[SHOW_NOW_PLAYING] = next.showNowPlaying
            p[SHOW_NOTIFICATIONS] = next.showNotifications
        }
    }

    suspend fun setPending(encounter: PendingEncounter?) {
        store.edit { p ->
            if (encounter == null) {
                p.remove(PENDING_ID)
                p.remove(PENDING_SHINY)
                p.remove(PENDING_AT)
                p.remove(PENDING_MINUTES)
            } else {
                p[PENDING_ID] = encounter.speciesId
                p[PENDING_SHINY] = encounter.shiny
                p[PENDING_AT] = encounter.appearedAt
                p[PENDING_MINUTES] = encounter.minutesCharging
            }
        }
    }

    suspend fun session(): ChargeSession {
        val p = store.data.first()
        return ChargeSession(p[SESSION_MINUTES] ?: 0, p[SESSION_SINCE_ENCOUNTER] ?: 0, p[SESSION_LAST_TICK] ?: 0L)
    }

    suspend fun saveSession(session: ChargeSession) {
        store.edit { p ->
            p[SESSION_MINUTES] = session.minutes
            p[SESSION_SINCE_ENCOUNTER] = session.minutesSinceEncounter
            p[SESSION_LAST_TICK] = session.lastTickAt
        }
    }

    private companion object {
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val TAP_TO_WAKE = booleanPreferencesKey("tap_to_wake")
        val LIFT_TO_CHECK = booleanPreferencesKey("lift_to_check")
        val VISIBLE_SECONDS = intPreferencesKey("visible_seconds")
        val ACCENT = longPreferencesKey("accent")
        val ENCOUNTERS = booleanPreferencesKey("encounters")
        val ENCOUNTER_MINUTES = intPreferencesKey("encounter_minutes")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val REGION = stringPreferencesKey("region")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val SHOW_CALENDAR = booleanPreferencesKey("show_calendar")
        val SHOW_CHARGING = booleanPreferencesKey("show_charging")
        val SHOW_NOW_PLAYING = booleanPreferencesKey("show_now_playing")
        val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")

        val PENDING_ID = intPreferencesKey("pending_id")
        val PENDING_SHINY = booleanPreferencesKey("pending_shiny")
        val PENDING_AT = longPreferencesKey("pending_at")
        val PENDING_MINUTES = intPreferencesKey("pending_minutes")

        val SESSION_MINUTES = intPreferencesKey("session_minutes")
        val SESSION_SINCE_ENCOUNTER = intPreferencesKey("session_since_encounter")
        val SESSION_LAST_TICK = longPreferencesKey("session_last_tick")
    }
}
