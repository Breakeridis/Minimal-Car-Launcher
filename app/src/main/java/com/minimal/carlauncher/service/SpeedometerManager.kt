package com.minimal.carlauncher.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class SpeedUnit(val label: String) {
    KMH("KM/H"),
    MPH("MPH")
}

class SpeedometerManager(private val context: Context) : LocationListener {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    private val _unit = MutableStateFlow(SpeedUnit.KMH)
    val unit: StateFlow<SpeedUnit> = _unit.asStateFlow()

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _cardinalDirection = MutableStateFlow("N")
    val cardinalDirection: StateFlow<String> = _cardinalDirection.asStateFlow()

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private var isListening = false

    fun toggleUnit() {
        _unit.value = if (_unit.value == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isListening || locationManager == null) return

        try {
            val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (hasGps) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L, // 500ms intervals for responsive car speed
                    0.5f,  // 0.5m displacement
                    this
                )
                isListening = true
                _isGpsActive.value = true
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1.0f,
                    this
                )
                isListening = true
                _isGpsActive.value = true
            }

            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                _currentLocation.value = lastKnown
                if (lastKnown.hasBearing()) {
                    _bearing.value = lastKnown.bearing
                    _cardinalDirection.value = getCardinalDirection(lastKnown.bearing)
                }
            }
        } catch (e: SecurityException) {
            _isGpsActive.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            _isGpsActive.value = false
        }
    }

    fun stopTracking() {
        if (!isListening || locationManager == null) return

        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isListening = false
            _isGpsActive.value = false
            _currentSpeed.value = 0
        }
    }

    override fun onLocationChanged(location: Location) {
        _currentLocation.value = location
        if (location.hasSpeed()) {
            // Speed in meters/second
            val speedMps = location.speed
            val convertedSpeed = when (_unit.value) {
                SpeedUnit.KMH -> (speedMps * 3.6f).roundToInt()
                SpeedUnit.MPH -> (speedMps * 2.23694f).roundToInt()
            }
            // Clamp negative or near-zero jitter when car is parked
            _currentSpeed.value = if (convertedSpeed < 2) 0 else convertedSpeed
        } else {
            _currentSpeed.value = 0
        }
        if (location.hasBearing()) {
            _bearing.value = location.bearing
            _cardinalDirection.value = getCardinalDirection(location.bearing)
        }
        _isGpsActive.value = true
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _isGpsActive.value = true
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _isGpsActive.value = false
            _currentSpeed.value = 0
        }
    }

    companion object {
        fun getCardinalDirection(bearingDegrees: Float): String {
            val normalized = ((bearingDegrees % 360f) + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> "N"
                normalized < 67.5f -> "NE"
                normalized < 112.5f -> "E"
                normalized < 157.5f -> "SE"
                normalized < 202.5f -> "S"
                normalized < 247.5f -> "SW"
                normalized < 292.5f -> "W"
                else -> "NW"
            }
        }
    }
}
