package com.devtamuno.heliocore.integrations.common

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarForecastResponse
import com.devtamuno.heliocore.domain.SolarPotentialResponse

interface SolarDataProvider {
    suspend fun fetchSolarData(
        request: SolarEstimateRequest,
        systemCapacityKw: Double,
        userId: String? = null
    ): SolarPotentialResponse
}

interface SolarForecastProvider {
    suspend fun forecast(
        request: SolarEstimateRequest,
        userId: String? = null
    ): SolarForecastResponse
}
