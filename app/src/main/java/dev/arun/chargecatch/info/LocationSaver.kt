package dev.arun.chargecatch.info

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import dev.arun.chargecatch.data.SavedLocation
import dev.arun.chargecatch.data.SettingsRepository
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saves the phone's approximate location for the weather. Called while the app is open, because
 * Android doesn't hand out location to a screen saver running in the background.
 */
object LocationSaver {
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(context: Context) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val location = current(manager) ?: lastKnown(manager) ?: return
        SettingsRepository(context).saveLocation(SavedLocation(location.latitude, location.longitude))
    }

    @Suppress("MissingPermission")
    private suspend fun current(manager: LocationManager): Location? {
        val provider = when {
            manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(15_000L) {
            suspendCancellableCoroutine { continuation ->
                runCatching {
                    manager.getCurrentLocation(provider, null, executor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? =
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
}
