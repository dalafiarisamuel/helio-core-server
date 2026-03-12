package com.devtamuno.heliocore.integrations.forecast

import com.devtamuno.heliocore.domain.ExternalServiceException
import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarForecastEntry
import com.devtamuno.heliocore.domain.SolarForecastResponse
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory


class OpenMeteoForecastClient(
    private val client: HttpClient,
    private val forecastDays: Int = 7,
    private val defaultLosses: Double = 0.14
) : SolarForecastProvider {
    private val logger = LoggerFactory.getLogger(OpenMeteoForecastClient::class.java)

    override suspend fun forecast(request: SolarEstimateRequest): SolarForecastResponse {
        logger.info("Calling Open-Meteo forecast: lat={}, lon={}, days={}", request.latitude, request.longitude, forecastDays)
        val response = client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", request.latitude)
            parameter("longitude", request.longitude)
            parameter("hourly", "shortwave_radiation")
            parameter("forecast_days", forecastDays)
            parameter("timezone", "UTC")
        }

        if (response.status != HttpStatusCode.OK) {
            throw ExternalServiceException("Open-Meteo returned ${response.status}")
        }

        val body = response.body<OpenMeteoResponse>()
        val hours = body.hourly ?: throw ExternalServiceException("Open-Meteo missing hourly data")
        if (hours.time.isEmpty() || hours.shortwaveRadiation.isEmpty()) {
            throw ExternalServiceException("Open-Meteo hourly arrays empty")
        }

        val capacityKw = (request.panelWattage * request.panelCount) / 1000.0
        val grouped = hours.time.zip(hours.shortwaveRadiation)
            .groupBy { parseDate(it.first) }
            .toSortedMap()

        val entries = grouped.entries.take(forecastDays).map { (date, values) ->
            val dailyWhPerM2 = values.sumOf { it.second } // shortwave_radiation is Wh/m2 for the hour window
            val (peakTimeIso, peakIrradiance) = values.maxByOrNull { it.second } ?: ("" to 0.0)
            val threshold = peakIrradiance * 0.2
            val windowTimes = values.filter { it.second >= threshold }.map { it.first }
            val windowStart = windowTimes.minOrNull().orEmpty()
            val windowEnd = windowTimes.maxOrNull().orEmpty()
            val peakSunHours = dailyWhPerM2 / 1000.0
            val expectedEnergy = capacityKw * peakSunHours * (1 - defaultLosses)
            SolarForecastEntry(
                date = date.format(DateTimeFormatter.ISO_DATE),
                peakSunHours = MeasuredValue(peakSunHours, "hours"),
                expectedEnergy = MeasuredValue(expectedEnergy, "kWh"),
                peakIrradianceTime = peakTimeIso,
                peakIrradiance = MeasuredValue(peakIrradiance, "Wh/m²"),
                sunWindowStart = windowStart,
                sunWindowEnd = windowEnd
            )
        }

        return SolarForecastResponse(
            forecasts = entries,
            panelWattage = request.panelWattage,
            panelCount = request.panelCount
        )

        logger.info(
            "Open-Meteo response processed: days={} peakSunHours(avg)={}",
            entries.size,
            if (entries.isNotEmpty()) entries.map { it.peakSunHours.value }.average() else 0.0
        )
    }

    private fun parseDate(isoTime: String): LocalDate =
        LocalDateTime.parse(isoTime).toLocalDate()
}

@Serializable
private data class OpenMeteoResponse(
    val hourly: Hourly? = null
)

@Serializable
private data class Hourly(
    val time: List<String> = emptyList(),
    @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double> = emptyList()
)
