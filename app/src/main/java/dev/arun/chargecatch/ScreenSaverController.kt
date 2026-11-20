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
import dev.arun.chargecatch.wake.LiftDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatchPhase {
    data object Idle : CatchPhase
    data object Throwing : CatchPhase
    data class Caught(val result: CatchResult) : CatchPhase
}

/**
 * Everything the screen saver shows and does: battery, the wake/hide state (tap to wake, lift to
 * check, the hide timer), the minute-by-minute encounter rolls and catching. Used by the screen
 * saver itself and by the in-app preview ([requireCharging] = false there).
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

    private val liftDetector = LiftDetector(app) { scope.launch { wake() } }
    private var hideJob: Job? = null
    private var session = ChargeSession(0, 0, 0L)

    fun start() {
        scope.launch {
            val saved = settingsRepo.session()
            val now = System.currentTimeMillis()
            session = if (now - saved.lastTickAt < SESSION_GAP_MS) saved else ChargeSession(0, 0, now)
            tickLoop()
        }
        scope.launch {
            settings.collect { applySettings(it) }
        }
        wake()
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
                session = session.copy(minutesSinceEncounter = 0)
                settingsRepo.saveSession(session)
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
            val charging = battery.value?.charging == true
            if (requireCharging && !charging) continue
            session = session.copy(
                minutes = session.minutes + 1,
                minutesSinceEncounter = session.minutesSinceEncounter + 1,
                lastTickAt = System.currentTimeMillis()
            )
            if (settings.value.encounters && pending.value == null &&
                Encounters.shouldAppear(session.minutesSinceEncounter)
            ) {
                if (Encounters.spawn(app, session.minutes) != null) {
                    session = session.copy(minutesSinceEncounter = 0)
                    // A new arrival lights the screen up briefly, like a notification would
                    wake()
                }
            }
            settingsRepo.saveSession(session)
        }
    }

    private companion object {
        /** Ticks closer together than this belong to the same charge. */
        const val SESSION_GAP_MS = 10 * 60_000L
        const val THROW_MS = 650L
        const val CAUGHT_SHOWN_MS = 3_500L
    }
}
