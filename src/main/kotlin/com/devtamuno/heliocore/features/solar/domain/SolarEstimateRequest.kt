package com.devtamuno.heliocore.features.solar.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarEstimateRequest(
    val latitude: Double,
    val longitude: Double,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int,
    @SerialName("panel_tilt") val panelTilt: Double? = null,
    val azimuth: Double? = null,
    val date: String? = null
)
