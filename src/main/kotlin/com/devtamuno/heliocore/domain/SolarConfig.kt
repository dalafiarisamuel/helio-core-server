package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SolarConfigRequest(
    val latitude: Double,
    val longitude: Double,
    @SerialName("resolved_address") val resolvedAddress: String? = null,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int,
    @SerialName("panel_tilt") val panelTilt: Double,
    val azimuth: Double
)

@Serializable
data class SolarConfigResponse(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("resolved_address") val resolvedAddress: String? = null,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int,
    @SerialName("panel_tilt") val panelTilt: Double,
    val azimuth: Double,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SuccessResponse(val message: String)
