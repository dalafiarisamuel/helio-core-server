package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarEstimateRequest(
    val latitude: Double,
    val longitude: Double,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int,
    @SerialName("panel_tilt") val panelTilt: Double? = null,
    val azimuth: Double? = null
)

@Serializable
data class SolarEstimateResponse(
    @SerialName("system_capacity") val systemCapacity: MeasuredValue,
    @SerialName("peak_sun_hours") val peakSunHours: MeasuredValue,
    @SerialName("daily_energy") val dailyEnergy: MeasuredValue,
    @SerialName("monthly_energy") val monthlyEnergy: MeasuredValue,
    @SerialName("annual_energy") val annualEnergy: MeasuredValue
)

@Serializable
data class SolarPotentialResponse(
    @SerialName("solrad_annual") val solradAnnual: MeasuredValue,
    @SerialName("ac_monthly") val acMonthly: List<MeasuredValue>,
    @SerialName("ac_annual") val acAnnual: MeasuredValue
)

@Serializable
data class MeasuredValue(
    val value: Double,
    val unit: String
)

sealed class DomainException(message: String) : RuntimeException(message)
class ValidationException(message: String) : DomainException(message)
class ExternalServiceException(message: String) : DomainException(message)

@Serializable
data class ErrorResponse(val message: String)
