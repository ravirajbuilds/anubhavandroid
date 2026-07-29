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
import java.util.concurrent.Executors

/**
 * Fetches the device position with the framework [LocationManager] (no Play Services)
 * and converts between coordinates and human-readable addresses with [Geocoder].
 *
 * Every callback is invoked exactly once, always on the main thread.
 */
object LocationHelper {

    private const val SINGLE_UPDATE_TIMEOUT_MS = 15_000L
    private const val LAST_KNOWN_MAX_AGE_MS = 5L * 60L * 1000L

    /** Rough bounding box of India, used to bias place search towards the clinic's patients. */
    private const val INDIA_MIN_LATITUDE = 6.0
    private const val INDIA_MAX_LATITUDE = 37.5
    private const val INDIA_MIN_LONGITUDE = 68.0
    private const val INDIA_MAX_LONGITUDE = 97.5

    /** Both are requested together: on Android 12+ the user can grant only the coarse one. */
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** Geocoding is blocking I/O; keep it off the main thread and off `new Thread()` per call. */
    private val geocodeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "anubhav-geocoder").apply { isDaemon = true }
    }

    enum class Error { NO_PERMISSION, LOCATION_DISABLED, LOCATION_UNAVAILABLE }

    sealed class Outcome {
        /** Got a fix and a readable address. */
        data class Success(val address: String, val latitude: Double, val longitude: Double) : Outcome()

        /** Got a fix but reverse-geocoding failed — coordinates are still usable. */
        data class NoAddress(val latitude: Double, val longitude: Double) : Outcome()

        data class Failure(val error: Error) : Outcome()
    }

    /** A raw position, without any address lookup. */
    data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

    sealed class FixOutcome {
        data class Found(val fix: Fix) : FixOutcome()
        data class Failed(val error: Error) : FixOutcome()
    }

    /** One forward-geocoding hit, used by the map picker's search box. */
    data class Place(val label: String, val latitude: Double, val longitude: Double)

    fun hasLocationPermission(context: Context): Boolean =
        LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Guards against NaN/infinite/out-of-range values before they reach the API. */
    fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude.isNaN() || longitude.isNaN()) return false
        if (latitude.isInfinite() || longitude.isInfinite()) return false
        if (latitude < -90.0 || latitude > 90.0) return false
        if (longitude < -180.0 || longitude > 180.0) return false
        // (0,0) is in the Atlantic — always a bug, never a home-collection address.
        return !(latitude == 0.0 && longitude == 0.0)
    }

    /** ~1 m precision is plenty for a doorstep and keeps the remark line short. */
    fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    /**
     * Resolves the current position only. Prefers a fresh cached fix, otherwise listens
     * on every enabled provider and takes whichever answers first.
     */
    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context, onResult: (FixOutcome) -> Unit) {
        val appContext = context.applicationContext
        if (!hasLocationPermission(appContext)) {
            onResult(FixOutcome.Failed(Error.NO_PERMISSION))
            return
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            onResult(FixOutcome.Failed(Error.LOCATION_UNAVAILABLE))
            return
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        if (providers.isEmpty()) {
            onResult(FixOutcome.Failed(Error.LOCATION_DISABLED))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var delivered = false
        val deliver: (FixOutcome) -> Unit = { outcome ->
            if (!delivered) {
                delivered = true
                onResult(outcome)
            }
        }

        val lastKnown = bestLastKnown(manager)
        if (lastKnown != null &&
            System.currentTimeMillis() - lastKnown.time <= LAST_KNOWN_MAX_AGE_MS
        ) {
            deliver(FixOutcome.Found(lastKnown.toFix()))
            return
        }

        // No fresh cached fix — listen live on every enabled provider, falling back to the
        // stale cached fix (or a clear error) if nothing arrives in time.
        val listeners = mutableListOf<LocationListener>()
        val stopAll = {
            listeners.forEach { runCatching { manager.removeUpdates(it) } }
            listeners.clear()
        }
        val timeoutRunnable = Runnable {
            stopAll()
            if (lastKnown != null) {
                deliver(FixOutcome.Found(lastKnown.toFix()))
            } else {
                deliver(FixOutcome.Failed(Error.LOCATION_UNAVAILABLE))
            }
        }

        var refusedForPermission = false
        providers.forEach { provider ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    handler.removeCallbacks(timeoutRunnable)
                    stopAll()
                    if (isValidCoordinate(location.latitude, location.longitude)) {
                        deliver(FixOutcome.Found(location.toFix()))
                    } else if (lastKnown != null) {
                        // A provider can emit a (0,0) placeholder before it has a real fix.
                        deliver(FixOutcome.Found(lastKnown.toFix()))
                    } else {
                        deliver(FixOutcome.Failed(Error.LOCATION_UNAVAILABLE))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
            val started = runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure { if (it is SecurityException) refusedForPermission = true }.isSuccess
            if (started) listeners += listener
        }

        if (listeners.isEmpty()) {
            // Every provider refused (revoked permission, hardware missing, …). A
            // permission revoked between the check above and here has to be reported as
            // such, or the user is told to "turn on GPS" that is already on.
            if (refusedForPermission) {
                deliver(FixOutcome.Failed(Error.NO_PERMISSION))
            } else if (lastKnown != null) {
                deliver(FixOutcome.Found(lastKnown.toFix()))
            } else {
                deliver(FixOutcome.Failed(Error.LOCATION_UNAVAILABLE))
            }
            return
        }
        handler.postDelayed(timeoutRunnable, SINGLE_UPDATE_TIMEOUT_MS)
    }

    /**
     * Resolves the current position and address. Errors are reported as
     * [Outcome.Failure]; a fix whose reverse-geocoding fails is reported as
     * [Outcome.NoAddress] so the caller can still keep the coordinates.
     */
    fun fetchCurrentAddress(context: Context, onResult: (Outcome) -> Unit) {
        fetchCurrentLocation(context) { outcome ->
            when (outcome) {
                is FixOutcome.Failed -> onResult(Outcome.Failure(outcome.error))
                is FixOutcome.Found -> {
                    val fix = outcome.fix
                    reverseGeocode(context, fix.latitude, fix.longitude) { address ->
                        onResult(
                            if (address.isNullOrBlank()) {
                                Outcome.NoAddress(fix.latitude, fix.longitude)
                            } else {
                                Outcome.Success(address, fix.latitude, fix.longitude)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Coordinates -> one readable address line. Delivers null when nothing is resolvable. */
    fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    ) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        if (!isValidCoordinate(latitude, longitude) || !Geocoder.isPresent()) {
            handler.post { onResult(null) }
            return
        }
        geocodeExecutor.execute {
            val line = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(appContext, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.let { formatAddress(it) }
            }.getOrNull()
            handler.post { onResult(line?.takeIf { it.isNotBlank() }) }
        }
    }

    /** Free-text search -> candidate places. Delivers an empty list when nothing matches. */
    fun searchPlaces(
        context: Context,
        query: String,
        maxResults: Int = 6,
        onResult: (List<Place>) -> Unit,
    ) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        val trimmed = query.trim()
        if (trimmed.length < 3 || !Geocoder.isPresent()) {
            handler.post { onResult(emptyList()) }
            return
        }
        geocodeExecutor.execute {
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val limit = maxResults.coerceIn(1, 10)
            // Bias to India first: unbounded, "park street" ranks Sydney above the
            // Kolkata one every collector actually drives to. Fall back to a worldwide
            // lookup so a genuinely out-of-box search still resolves.
            val places = runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(
                    trimmed,
                    limit,
                    INDIA_MIN_LATITUDE,
                    INDIA_MIN_LONGITUDE,
                    INDIA_MAX_LATITUDE,
                    INDIA_MAX_LONGITUDE,
                ).toPlaces()
            }.getOrDefault(emptyList()).ifEmpty {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(trimmed, limit).toPlaces()
                }.getOrDefault(emptyList())
            }
            handler.post { onResult(places) }
        }
    }

    private fun List<Address>?.toPlaces(): List<Place> =
        orEmpty()
            .filter { isValidCoordinate(it.latitude, it.longitude) }
            .map { Place(formatAddress(it), it.latitude, it.longitude) }
            .filter { it.label.isNotBlank() }
            .distinctBy { it.label }

    private fun Location.toFix(): Fix =
        Fix(latitude, longitude, if (hasAccuracy()) accuracy else null)

    // Only ever reached after hasLocationPermission(); a revoked-mid-call SecurityException
    // is swallowed by the runCatching below.
    @SuppressLint("MissingPermission")
    private fun bestLastKnown(manager: LocationManager): Location? =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { isValidCoordinate(it.latitude, it.longitude) }
            .maxByOrNull { it.time }

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
