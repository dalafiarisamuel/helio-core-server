package com.devtamuno.heliocore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MonthlySolarData(
    val month: String,
    val data: MeasuredValue
)

@Serializable
data class SolarPotentialResponse(
    @SerialName("solrad_annual") val solradAnnual: MeasuredValue,
    @SerialName("ac_monthly") val acMonthly: List<MonthlySolarData>,
    @SerialName("ac_annual") val acAnnual: MeasuredValue,
    @SerialName("panel_wattage") val panelWattage: Double,
    @SerialName("panel_count") val panelCount: Int
)
