package com.devtamuno.heliocore.features.solar.service

import com.devtamuno.heliocore.domain.ValidationException

object SolarValidator {
    const val MIN_LAT = -90.0
    const val MAX_LAT = 90.0
    const val MIN_LON = -180.0
    const val MAX_LON = 180.0
    const val MIN_TILT = 0.0
    const val MAX_TILT = 90.0
    const val MIN_AZIMUTH = 0.0
    const val MAX_AZIMUTH = 360.0

    fun validate(
        latitude: Double,
        longitude: Double,
        panelWattage: Double,
        panelCount: Int,
        panelTilt: Double?,
        azimuth: Double?
    ) {
        if (latitude !in MIN_LAT..MAX_LAT) {
            throw ValidationException("Latitude must be between -90 and 90")
        }
        if (longitude !in MIN_LON..MAX_LON) {
            throw ValidationException("Longitude must be between -180 and 180")
        }
        if (panelWattage <= 0) {
            throw ValidationException("Panel wattage must be positive")
        }
        if (panelCount <= 0) {
            throw ValidationException("Panel count must be positive")
        }
        panelTilt?.let {
            if (it !in MIN_TILT..MAX_TILT) {
                throw ValidationException("Panel tilt must be between 0 and 90")
            }
        }
        azimuth?.let {
            if (it !in MIN_AZIMUTH..MAX_AZIMUTH) {
                throw ValidationException("Azimuth must be between 0 and 360")
            }
        }
    }
}
