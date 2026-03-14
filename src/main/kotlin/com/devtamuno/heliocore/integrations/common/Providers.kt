package com.devtamuno.heliocore.integrations.common

import com.devtamuno.heliocore.features.solar.domain.SolarEstimateRequest
import com.devtamuno.heliocore.features.solar.domain.SolarForecastResponse
import com.devtamuno.heliocore.features.solar.domain.SolarPotentialResponse

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
