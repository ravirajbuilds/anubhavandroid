package com.anubhav.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Fetches the device position with the framework [LocationManager] (no Play Services)
 * and reverse-geocodes it into one human-readable address line with [Geocoder].
 *
 * The callback is invoked exactly once, always on the main thread.
 */
object LocationHelper {

    private const val SINGLE_UPDATE_TIMEOUT_MS = 15_000L
    private const val LAST_KNOWN_MAX_AGE_MS = 5L * 60L * 1000L

    enum class Error { NO_PERMISSION, LOCATION_DISABLED, LOCATION_UNAVAILABLE }

    sealed class Outcome {
        /** Got a fix and a readable address. */
        data class Success(val address: String, val latitude: Double, val longitude: Double) : Outcome()

        /** Got a fix but reverse-geocoding failed — coordinates are still usable. */
        data class NoAddress(val latitude: Double, val longitude: Double) : Outcome()

        data class Failure(val error: Error) : Outcome()
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Resolves the current position and address. Errors are reported as
     * [Outcome.Failure]; a fix whose reverse-geocoding fails is reported as
     * [Outcome.NoAddress] so the caller can still keep the coordinates.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentAddress(context: Context, onResult: (Outcome) -> Unit) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            onResult(Outcome.Failure(Error.NO_PERMISSION))
            return
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            onResult(Outcome.Failure(Error.LOCATION_UNAVAILABLE))
            return
        }
        val gpsEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val networkEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        if (!gpsEnabled && !networkEnabled) {
            onResult(Outcome.Failure(Error.LOCATION_DISABLED))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var delivered = false
        val deliver: (Outcome) -> Unit = { outcome ->
            if (!delivered) {
                delivered = true
                onResult(outcome)
            }
        }

        val lastKnown = bestLastKnown(manager)
        if (lastKnown != null &&
            System.currentTimeMillis() - lastKnown.time <= LAST_KNOWN_MAX_AGE_MS
        ) {
            geocode(appContext, lastKnown, handler, deliver)
            return
        }

        // No fresh cached fix — request a single live update, falling back to the
        // stale cached fix (or a clear error) if nothing arrives in time.
        val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Cancel the pending timeout; nothing else is queued on this handler yet.
                handler.removeCallbacksAndMessages(null)
                runCatching { manager.removeUpdates(this) }
                geocode(appContext, location, handler, deliver)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }
        val timeoutRunnable = Runnable {
            runCatching { manager.removeUpdates(listener) }
            if (lastKnown != null) {
                geocode(appContext, lastKnown, handler, deliver)
            } else {
                deliver(Outcome.Failure(Error.LOCATION_UNAVAILABLE))
            }
        }
        try {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            handler.postDelayed(timeoutRunnable, SINGLE_UPDATE_TIMEOUT_MS)
        } catch (_: SecurityException) {
            deliver(Outcome.Failure(Error.NO_PERMISSION))
        } catch (_: Exception) {
            if (lastKnown != null) {
                geocode(appContext, lastKnown, handler, deliver)
            } else {
                deliver(Outcome.Failure(Error.LOCATION_UNAVAILABLE))
            }
        }
    }

    private fun bestLastKnown(manager: LocationManager): Location? =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

    /** Reverse-geocodes off the main thread and posts the outcome back through [handler]. */
    private fun geocode(
        context: Context,
        location: Location,
        handler: Handler,
        onResult: (Outcome) -> Unit,
    ) {
        Thread {
            val outcome = try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                val line = addresses?.firstOrNull()?.let { formatAddress(it) }
                if (line.isNullOrBlank()) {
                    Outcome.NoAddress(location.latitude, location.longitude)
                } else {
                    Outcome.Success(line, location.latitude, location.longitude)
                }
            } catch (_: Exception) {
                Outcome.NoAddress(location.latitude, location.longitude)
            }
            handler.post { onResult(outcome) }
        }.start()
    }

    private fun formatAddress(address: Address): String {
        address.getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(
            address.featureName,
            address.thoroughfare,
            address.subLocality,
            address.locality,
            address.postalCode,
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }
}
