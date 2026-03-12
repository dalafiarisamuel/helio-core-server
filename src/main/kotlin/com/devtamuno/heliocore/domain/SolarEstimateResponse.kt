package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarEstimateResponse(
    @SerialName("system_capacity") val systemCapacity: MeasuredValue,
    @SerialName("peak_sun_hours") val peakSunHours: MeasuredValue,
    @SerialName("daily_energy") val dailyEnergy: MeasuredValue,
    @SerialName("monthly_energy") val monthlyEnergy: MeasuredValue,
    @SerialName("annual_energy") val annualEnergy: MeasuredValue
)
