package com.devtamuno.heliocore.integrations.pvwatts

import com.devtamuno.heliocore.domain.ExternalServiceException
import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.MonthlySolarData
import com.devtamuno.heliocore.domain.SolarPotentialResponse
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory


class PvWattsClient(
    private val apiKey: String,
    private val client: HttpClient,
    private val defaultSystemLossesPercent: Double = 14.0
) : SolarDataProvider {
    private val logger = LoggerFactory.getLogger(PvWattsClient::class.java)

    override suspend fun fetchSolarData(
        request: SolarEstimateRequest,
        systemCapacityKw: Double,
        userId: String?
    ): SolarPotentialResponse {
        val tilt = request.panelTilt ?: 20.0
        val azimuth = request.azimuth ?: 180.0

        logger.info(
            "Calling PVWatts: lat={}, lon={}, capacityKw={}, tilt={}, azimuth={}",
            request.latitude,
            request.longitude,
            systemCapacityKw,
            tilt,
            azimuth
        )

        val response = client.get("https://developer.nrel.gov/api/pvwatts/v8.json") {
            parameter("api_key", apiKey)
            parameter("lat", request.latitude)
            parameter("lon", request.longitude)
            parameter("system_capacity", systemCapacityKw)
            parameter("azimuth", azimuth)
            parameter("tilt", tilt)
            parameter("array_type", 1) // Fixed open rack
            parameter("module_type", 0) // Standard
            parameter("losses", defaultSystemLossesPercent)
        }

        if (response.status != HttpStatusCode.OK) {
            val payload = response.bodyAsText()
            logger.error("PVWatts non-200 response: {} - {}", response.status, payload)
            throw ExternalServiceException("PVWatts returned ${response.status}")
        }

        val body = response.body<PvWattsResponse>()

        if (body.errors.isNotEmpty()) {
            logger.error("PVWatts errors: {}", body.errors)
            throw ExternalServiceException("PVWatts errors: ${body.errors.joinToString(", ")}")
        }

        val outputs = body.outputs ?: throw ExternalServiceException("PVWatts response missing outputs")

        logger.info(
            "PVWatts response: acAnnual={} kWh, solradAnnual={} kWh/m2/day, monthlyCount={}",
            outputs.acAnnual,
            outputs.solarRadAnnual,
            outputs.acMonthly.size
        )

        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        return SolarPotentialResponse(
            solradAnnual = MeasuredValue(MeasuredValue.roundToDecimals(outputs.solarRadAnnual), "kWh/m²/day"),
            acMonthly = outputs.acMonthly.mapIndexed { index, value ->
                MonthlySolarData(
                    month = monthNames.getOrElse(index) { "Unknown" },
                    data = MeasuredValue(MeasuredValue.roundToDecimals(value), "kWh")
                )
            },
            acAnnual = MeasuredValue(MeasuredValue.roundToDecimals(outputs.acAnnual), "kWh"),
            panelWattage = request.panelWattage,
            panelCount = request.panelCount
        )
    }
}

@Serializable
private data class PvWattsResponse(
    val outputs: Outputs? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
private data class Outputs(
    @SerialName("ac_annual") val acAnnual: Double,
    @SerialName("ac_monthly") val acMonthly: List<Double>,
    @SerialName("solrad_annual") val solarRadAnnual: Double
)
