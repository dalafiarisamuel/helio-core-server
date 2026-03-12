package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarForecastEntry(
    val date: String,
    @SerialName("peak_sun_hours") val peakSunHours: MeasuredValue,
    @SerialName("expected_energy") val expectedEnergy: MeasuredValue
)

@Serializable
data class SolarForecastResponse(
    val forecasts: List<SolarForecastEntry>,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int
)
