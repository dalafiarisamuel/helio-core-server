package com.devtamuno.heliocore.domain

import kotlinx.serialization.Serializable

@Serializable
data class MeasuredValue(
    val value: Double,
    val unit: String
)
