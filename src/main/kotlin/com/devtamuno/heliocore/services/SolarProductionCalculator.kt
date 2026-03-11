package com.devtamuno.heliocore.services

import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarEstimateResponse
import com.devtamuno.heliocore.domain.ValidationException
import kotlin.math.round

class SolarProductionCalculator(private val defaultSystemLosses: Double = 0.14) {

    fun validate(request: SolarEstimateRequest) {
        if (request.latitude !in -90.0..90.0) throw ValidationException("Latitude must be between -90 and 90")
        if (request.longitude !in -180.0..180.0) throw ValidationException("Longitude must be between -180 and 180")
        if (request.panelWattage <= 0) throw ValidationException("panel_wattage must be positive")
        if (request.panelCount <= 0) throw ValidationException("panel_count must be positive")
        request.panelTilt?.let { tilt ->
            if (tilt !in 0.0..90.0) throw ValidationException("panel_tilt must be between 0 and 90 degrees")
        }
        request.azimuth?.let { azimuth ->
            if (azimuth !in 0.0..360.0) throw ValidationException("azimuth must be between 0 and 360 degrees")
        }
    }

    fun systemCapacityKw(request: SolarEstimateRequest): Double =
        (request.panelWattage * request.panelCount) / 1000.0

    fun calculate(
        request: SolarEstimateRequest,
        peakSunHours: Double,
        systemLosses: Double = defaultSystemLosses
    ): SolarEstimateResponse {
        val systemCapacityKw = systemCapacityKw(request)
        val dailyEnergy = systemCapacityKw * peakSunHours * (1 - systemLosses)
        val monthlyEnergy = dailyEnergy * 30
        val annualEnergy = dailyEnergy * 365

        return SolarEstimateResponse(
            systemCapacity = measured(roundToSingleDecimal(systemCapacityKw), "kW"),
            peakSunHours = measured(roundToSingleDecimal(peakSunHours), "hours"),
            dailyEnergy = measured(roundToSingleDecimal(dailyEnergy), "kWh"),
            monthlyEnergy = measured(roundToSingleDecimal(monthlyEnergy), "kWh"),
            annualEnergy = measured(roundToSingleDecimal(annualEnergy), "kWh")
        )
    }

    private fun roundToSingleDecimal(value: Double): Double = round(value * 10) / 10.0

    private fun measured(value: Double, unit: String) = MeasuredValue(value = value, unit = unit)
}
