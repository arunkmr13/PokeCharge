package dev.arun.chargecatch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class DisplayMode { ALWAYS_ON, WAKE_ON_GESTURE }

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
    val region: Region = Region.UNOVA
) {
    companion object {
        val ACCENTS = listOf(0xFF7CFFB2, 0xFFFFB547, 0xFF7FB8FF, 0xFFFF7A6B)
        val VISIBLE_SECONDS = listOf(10, 15, 30)
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
            region = p[REGION]?.let { runCatching { Region.valueOf(it) }.getOrNull() } ?: Region.UNOVA
        )
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
            p[REGION] = next.region.name
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
        val REGION = stringPreferencesKey("region")

        val PENDING_ID = intPreferencesKey("pending_id")
        val PENDING_SHINY = booleanPreferencesKey("pending_shiny")
        val PENDING_AT = longPreferencesKey("pending_at")
        val PENDING_MINUTES = intPreferencesKey("pending_minutes")

        val SESSION_MINUTES = intPreferencesKey("session_minutes")
        val SESSION_SINCE_ENCOUNTER = intPreferencesKey("session_since_encounter")
        val SESSION_LAST_TICK = longPreferencesKey("session_last_tick")
    }
}
