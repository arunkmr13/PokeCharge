package dev.arun.chargecatch.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

data class BatteryState(
    val level: Int,
    val charging: Boolean,
    val full: Boolean,
    val plug: PlugType,
    /** Estimated time to full, in minutes, or null when the system has no estimate. */
    val minutesToFull: Int?
)

/**
 * Live battery state from the sticky ACTION_BATTERY_CHANGED broadcast.
 */
fun batteryStates(context: Context): Flow<BatteryState> = callbackFlow {
    val app = context.applicationContext
    val batteryManager = app.getSystemService(BatteryManager::class.java)

    fun parse(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val percent = if (level >= 0) (level * 100 / scale) else 0
        val plug = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
            else -> PlugType.NONE
        }
        val remainingMs = batteryManager?.computeChargeTimeRemaining() ?: -1L
        return BatteryState(
            level = percent,
            charging = plug != PlugType.NONE,
            full = status == BatteryManager.BATTERY_STATUS_FULL || percent >= 100,
            plug = plug,
            minutesToFull = if (remainingMs > 0) (remainingMs / 60_000L).toInt() else null
        )
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            trySend(parse(intent))
        }
    }
    val sticky = app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
    sticky?.let { trySend(parse(it)) }
    awaitClose { app.unregisterReceiver(receiver) }
}.distinctUntilChanged()
