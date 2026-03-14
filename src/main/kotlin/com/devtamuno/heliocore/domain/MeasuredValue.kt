package com.devtamuno.heliocore.domain

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.round

@Serializable
data class MeasuredValue(
    val value: Double,
    val unit: String
) {
    companion object {
        fun roundToDecimals(value: Double, decimals: Int = 3): Double {
            val factor = 10.0.pow(decimals)
            return round(value * factor) / factor
        }
    }
}
