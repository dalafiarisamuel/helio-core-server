package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarPotentialRequest(
    val latitude: Double,
    val longitude: Double,
    @SerialName("panel_wattage") val panelWattage: Double? = null,
    @SerialName("panel_count") val panelCount: Int? = null,
    @SerialName("panel_tilt") val panelTilt: Double? = null,
    val azimuth: Double? = null
)
