package com.devtamuno.heliocore.services

import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarEstimateResponse
import com.devtamuno.heliocore.domain.SolarValidator

class SolarProductionCalculator(private val defaultSystemLosses: Double = DEFAULT_SYSTEM_LOSSES) {

    fun validate(request: SolarEstimateRequest) {
        SolarValidator.validate(
            latitude = request.latitude,
            longitude = request.longitude,
            panelWattage = request.panelWattage,
            panelCount = request.panelCount,
            panelTilt = request.panelTilt,
            azimuth = request.azimuth
        )
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
        const val WATTS_PER_KILOWATT = 1000.0
    }
}
