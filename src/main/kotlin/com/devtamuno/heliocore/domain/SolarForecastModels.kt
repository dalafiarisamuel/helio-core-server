package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarForecastEntry(
    val date: String,
    @SerialName("peak_sun_hours") val peakSunHours: MeasuredValue,
    @SerialName("expected_energy") val expectedEnergy: MeasuredValue,
    @SerialName("peak_irradiance_time") val peakIrradianceTime: String,
    @SerialName("peak_irradiance") val peakIrradiance: MeasuredValue,
    @SerialName("sun_window_start") val sunWindowStart: String,
    @SerialName("sun_window_end") val sunWindowEnd: String,
    @SerialName("weather_condition") val weatherCondition: String = "cloudy"
)

@Serializable
data class SolarForecastResponse(
    val forecasts: List<SolarForecastEntry>,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int
)
