package dev.arun.chargecatch

import android.content.Context
import dev.arun.chargecatch.battery.BatteryState
import dev.arun.chargecatch.battery.batteryStates
import dev.arun.chargecatch.data.AppSettings
import dev.arun.chargecatch.data.ChargeSession
import dev.arun.chargecatch.data.DisplayMode
import dev.arun.chargecatch.data.PendingEncounter
import dev.arun.chargecatch.data.SettingsRepository
import dev.arun.chargecatch.game.CatchResult
import dev.arun.chargecatch.game.Encounters
import dev.arun.chargecatch.info.AppIcon
import dev.arun.chargecatch.info.CalendarRepository
import dev.arun.chargecatch.info.NextEvent
import dev.arun.chargecatch.info.NotificationHub
import dev.arun.chargecatch.info.NowPlaying
import dev.arun.chargecatch.info.Weather
import dev.arun.chargecatch.info.WeatherRepository
import dev.arun.chargecatch.wake.LiftDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatchPhase {
    data object Idle : CatchPhase
    data object Throwing : CatchPhase
    data class Caught(val result: CatchResult) : CatchPhase
}

/**
 * Everything the screen saver shows and does: battery, the wake/hide state (tap to wake, lift to
 * check, the hide timer), the encounter rhythm and catching. Used by the screen saver itself and
 * by the in-app preview ([requireCharging] = false there).
 *
 * Encounters run on a fixed rhythm: a fresh charge brings a Pokémon right away, then a new one
 * every [AppSettings.encounterMinutes] minutes. Catching doesn't change the rhythm, and one left
 * uncaught runs off when the next one arrives.
 */
class ScreenSaverController(
    context: Context,
    private val scope: CoroutineScope,
    private val requireCharging: Boolean
) {
    private val app = context.applicationContext
    private val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val battery: StateFlow<BatteryState?> = batteryStates(app)
        .stateIn(scope, SharingStarted.Eagerly, null)
    val pending: StateFlow<PendingEncounter?> = settingsRepo.pending
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _phase = MutableStateFlow<CatchPhase>(CatchPhase.Idle)
    val phase: StateFlow<CatchPhase> = _phase.asStateFlow()

    /** Minutes until the next Pokémon, for the screen's "next in" line. */
    private val _minutesToNext = MutableStateFlow<Int?>(null)
    val minutesToNext: StateFlow<Int?> = _minutesToNext.asStateFlow()

    /** Glanceable info around the clock and ring. */
    private val _weather = MutableStateFlow(WeatherRepository.cached(app))
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    private val _nextEvent = MutableStateFlow<NextEvent?>(null)
    val nextEvent: StateFlow<NextEvent?> = _nextEvent.asStateFlow()
    val nowPlaying: StateFlow<NowPlaying?> = NotificationHub.nowPlaying
    val appIcons: StateFlow<List<AppIcon>> = NotificationHub.appIcons

    private val liftDetector = LiftDetector(app) { scope.launch { wake() } }
    private var hideJob: Job? = null
    private var session = ChargeSession(0, 0, 0L)

    fun start() {
        scope.launch {
            val saved = settingsRepo.session()
            val now = System.currentTimeMillis()
            val continuing = now - saved.lastTickAt < SESSION_GAP_MS
            session = if (continuing) saved else ChargeSession(0, 0, now)
            if (!continuing) {
                val current = settingsRepo.settings.first()
                if (current.encounters && settingsRepo.pending.first() == null && chargingOrPreview()) {
                    // A fresh charge starts with a Pokémon already there
                    Encounters.spawn(app, minutesCharging = 0)
                }
                settingsRepo.saveSession(session.copy(lastTickAt = now))
            }
            updateMinutesToNext()
            tickLoop()
        }
        scope.launch {
            settings.collect {
                applySettings(it)
                updateMinutesToNext()
            }
        }
        NotificationHub.rebind(app)
        scope.launch { weatherLoop() }
        scope.launch { calendarLoop() }
        wake()
    }

    private suspend fun weatherLoop() {
        while (true) {
            if (settings.value.showWeather) {
                val location = settingsRepo.location.first()
                if (location != null) {
                    WeatherRepository.current(app, location)?.let { _weather.value = it }
                }
            }
            delay(WEATHER_REFRESH_MS)
        }
    }

    private suspend fun calendarLoop() {
        while (true) {
            _nextEvent.value = if (settings.value.showCalendar) CalendarRepository.nextEvent(app) else null
            delay(60_000L)
        }
    }

    fun stop() {
        liftDetector.stop()
        hideJob?.cancel()
    }

    /** A tap on the screen: wakes it, or catches the waiting Pokémon when it is showing. */
    fun onTap() {
        val s = settings.value
        val showing = _visible.value || s.displayMode == DisplayMode.ALWAYS_ON
        when {
            showing && pending.value != null && _phase.value == CatchPhase.Idle -> throwBall()
            showing -> wake()
            s.tapToWake -> wake()
        }
    }

    fun wake() {
        _visible.value = true
        scheduleHide()
    }

    private fun chargingOrPreview(): Boolean = !requireCharging || battery.value?.charging != false

    private fun applySettings(s: AppSettings) {
        if (s.displayMode == DisplayMode.ALWAYS_ON) {
            hideJob?.cancel()
            _visible.value = true
            liftDetector.stop()
        } else {
            if (s.liftToCheck) liftDetector.start() else liftDetector.stop()
            if (_visible.value) scheduleHide()
        }
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        val s = settings.value
        if (s.displayMode == DisplayMode.ALWAYS_ON) return
        hideJob = scope.launch {
            delay(s.visibleSeconds * 1_000L)
            while (_phase.value != CatchPhase.Idle) delay(500)
            _visible.value = false
        }
    }

    private fun throwBall() {
        scope.launch {
            _phase.value = CatchPhase.Throwing
            hideJob?.cancel()
            delay(THROW_MS)
            val result = Encounters.catchPending(app)
            if (result != null) {
                _phase.value = CatchPhase.Caught(result)
                delay(CAUGHT_SHOWN_MS)
            }
            _phase.value = CatchPhase.Idle
            scheduleHide()
        }
    }

    private suspend fun tickLoop() {
        while (true) {
            delay(60_000L)
            if (requireCharging && battery.value?.charging != true) continue
            session = session.copy(
                minutes = session.minutes + 1,
                minutesSinceEncounter = session.minutesSinceEncounter + 1,
                lastTickAt = System.currentTimeMillis()
            )
            val s = settings.value
            if (s.encounters && session.minutesSinceEncounter >= s.encounterMinutes && _phase.value == CatchPhase.Idle) {
                // Time for the next one: it replaces any Pokémon still waiting
                if (Encounters.spawn(app, session.minutes, replaceExisting = true) != null) {
                    session = session.copy(minutesSinceEncounter = 0)
                    // A new arrival lights the screen up briefly, like a notification would
                    wake()
                }
            }
            settingsRepo.saveSession(session)
            updateMinutesToNext()
        }
    }

    private fun updateMinutesToNext() {
        val s = settings.value
        _minutesToNext.value = if (s.encounters) {
            (s.encounterMinutes - session.minutesSinceEncounter).coerceAtLeast(1)
        } else {
            null
        }
    }

    private companion object {
        /** Ticks closer together than this belong to the same charge. */
        const val SESSION_GAP_MS = 10 * 60_000L
        const val THROW_MS = 650L
        const val CAUGHT_SHOWN_MS = 3_500L
        const val WEATHER_REFRESH_MS = 30 * 60_000L
    }
}
