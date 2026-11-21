package dev.arun.chargecatch.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

data class BatteryState(
    val level: Int,
    val charging: Boolean,
    val full: Boolean,
    val plug: PlugType,
    /** Estimated time to full, in minutes, or null when the system has no estimate. */
    val minutesToFull: Int?,
    /** Power going into the battery, in watts, or null when the phone doesn't report it. */
    val watts: Float?,
    /** Battery temperature in °C, or null when unknown. */
    val temperatureC: Float?
) {
    /** "Fast charging" from 15 W, "Charging slowly" under 7.5 W. */
    val speedLabel: String
        get() {
            val w = watts
            val wireless = plug == PlugType.WIRELESS
            return when {
                w != null && w >= FAST_WATTS -> if (wireless) "Fast wireless charging" else "Fast charging"
                w != null && w < SLOW_WATTS -> "Charging slowly"
                wireless -> "Charging wirelessly"
                else -> "Charging"
            }
        }

    private companion object {
        const val FAST_WATTS = 15f
        const val SLOW_WATTS = 7.5f
    }
}

/**
 * Live battery state: the sticky ACTION_BATTERY_CHANGED broadcast, re-read every few seconds so
 * the charging power (which moves much faster than the broadcast) stays current.
 */
fun batteryStates(context: Context): Flow<BatteryState> = callbackFlow {
    val app = context.applicationContext
    val batteryManager = app.getSystemService(BatteryManager::class.java)
    var last: Intent? = null

    fun parse(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val millivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val tenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val percent = if (level >= 0) (level * 100 / scale) else 0
        val plug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
            else -> PlugType.NONE
        }
        val charging = plug != PlugType.NONE
        val remainingMs = batteryManager?.computeChargeTimeRemaining() ?: -1L

        // current_now is in microamps on Pixels; the sign differs between devices, so use its size
        val microamps = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val watts = if (charging && millivolts > 0 && microamps != Int.MIN_VALUE && microamps != 0) {
            (abs(microamps.toLong()) / 1_000_000f) * (millivolts / 1_000f)
        } else {
            null
        }

        return BatteryState(
            level = percent,
            charging = charging,
            full = status == BatteryManager.BATTERY_STATUS_FULL || percent >= 100,
            plug = plug,
            minutesToFull = if (remainingMs > 0) (remainingMs / 60_000L).toInt() else null,
            watts = watts?.takeIf { it in 0.5f..200f },
            temperatureC = if (tenthsC != Int.MIN_VALUE && tenthsC > -400) tenthsC / 10f else null
        )
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            last = intent
            trySend(parse(intent))
        }
    }
    val sticky = app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
    sticky?.let {
        last = it
        trySend(parse(it))
    }
    val poller = launch {
        while (true) {
            delay(POLL_MS)
            last?.let { trySend(parse(it)) }
        }
    }
    awaitClose {
        poller.cancel()
        app.unregisterReceiver(receiver)
    }
}.distinctUntilChanged()

private const val POLL_MS = 5_000L
