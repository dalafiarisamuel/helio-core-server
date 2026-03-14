package com.devtamuno.heliocore.services

import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarEstimateResponse
import com.devtamuno.heliocore.domain.ValidationException
import kotlin.math.round

class SolarProductionCalculator(private val defaultSystemLosses: Double = DEFAULT_SYSTEM_LOSSES) {

    fun validate(request: SolarEstimateRequest) {
        if (request.latitude !in MIN_LAT..MAX_LAT) throw ValidationException("Latitude must be between -90 and 90")
        if (request.longitude !in MIN_LON..MAX_LON) throw ValidationException("Longitude must be between -180 and 180")
        if (request.panelWattage <= 0) throw ValidationException("panel_wattage must be positive")
        if (request.panelCount <= 0) throw ValidationException("panel_count must be positive")
        request.panelTilt?.let { tilt ->
            if (tilt !in MIN_TILT..MAX_TILT) throw ValidationException("panel_tilt must be between 0 and 90 degrees")
        }
        request.azimuth?.let { azimuth ->
            if (azimuth !in MIN_AZIMUTH..MAX_AZIMUTH) throw ValidationException("azimuth must be between 0 and 360 degrees")
        }
    }

    fun systemCapacityKw(request: SolarEstimateRequest): Double =
        (request.panelWattage * request.panelCount) / WATTS_PER_KILOWATT

    fun calculate(
        request: SolarEstimateRequest,
        peakSunHours: Double,
        systemLosses: Double = defaultSystemLosses
    ): SolarEstimateResponse {
        val systemCapacityKw = systemCapacityKw(request)
        val dailyEnergy = systemCapacityKw * peakSunHours * (1 - systemLosses)
        val monthlyEnergy = dailyEnergy * DAYS_PER_MONTH
        val annualEnergy = dailyEnergy * DAYS_PER_YEAR

        return SolarEstimateResponse(
            systemCapacity = measured(roundToDecimals(systemCapacityKw), "kW"),
            peakSunHours = measured(roundToDecimals(peakSunHours), "hours"),
            dailyEnergy = measured(roundToDecimals(dailyEnergy), "kWh"),
            monthlyEnergy = measured(roundToDecimals(monthlyEnergy), "kWh"),
            annualEnergy = measured(roundToDecimals(annualEnergy), "kWh")
        )
    }

    private fun roundToDecimals(value: Double): Double = MeasuredValue.roundToDecimals(value)

    private fun measured(value: Double, unit: String) = MeasuredValue(value = value, unit = unit)

    companion object {
        const val DEFAULT_SYSTEM_LOSSES = 0.14
        const val DAYS_PER_MONTH = 30
        const val DAYS_PER_YEAR = 365
        const val MIN_LAT = -90.0
        const val MAX_LAT = 90.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0
        const val MIN_TILT = 0.0
        const val MAX_TILT = 90.0
        const val MIN_AZIMUTH = 0.0
        const val MAX_AZIMUTH = 360.0
        const val WATTS_PER_KILOWATT = 1000.0
    }
}
