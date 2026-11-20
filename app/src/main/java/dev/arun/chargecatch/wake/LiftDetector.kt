package dev.arun.chargecatch.wake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * "Lift to check": fires when the phone tilts noticeably from where it was resting. Uses the
 * gravity sensor (the accelerometer as a fallback) at a low rate, and only while started, which is
 * only while the screen saver runs, so only while charging.
 */
class LiftDetector(context: Context, private val onLift: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var resting: FloatArray? = null
    private var lastFiredAt = 0L
    private var running = false

    val isAvailable: Boolean get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = true
        resting = null
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val current = normalize(event.values) ?: return
        val base = resting
        if (base == null) {
            resting = current
            return
        }
        val dot = (base[0] * current[0] + base[1] * current[1] + base[2] * current[2]).coerceIn(-1f, 1f)
        val degrees = Math.toDegrees(acos(dot).toDouble())
        val now = SystemClock.elapsedRealtime()
        if (degrees >= TRIGGER_DEGREES) {
            if (now - lastFiredAt > COOLDOWN_MS) {
                lastFiredAt = now
                onLift()
            }
            resting = current
        } else {
            // Follow slow drift so a phone settling on its stand doesn't count as a lift
            for (i in 0..2) base[i] = base[i] * 0.95f + current[i] * 0.05f
            normalize(base)?.let { resting = it }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalize(v: FloatArray): FloatArray? {
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (length < 1e-3f) return null
        return floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
    }

    private companion object {
        const val TRIGGER_DEGREES = 25.0
        const val COOLDOWN_MS = 2_000L
    }
}
