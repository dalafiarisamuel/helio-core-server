package com.devtamuno.heliocore

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.ExternalServiceException
import com.devtamuno.heliocore.integrations.forecast.OpenMeteoForecastClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenMeteoForecastClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `forecast returns sun window start and end`() {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                  "hourly": {
                    "time": [
                      "2026-03-12T09:00",
                      "2026-03-12T10:00",
                      "2026-03-12T11:00",
                      "2026-03-12T12:00",
                      "2026-03-12T13:00",
                      "2026-03-12T14:00",
                      "2026-03-12T15:00",
                      "2026-03-12T16:00"
                    ],
                    "shortwave_radiation": [100, 200, 300, 500, 400, 250, 150, 50]
                  }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val forecastClient = OpenMeteoForecastClient(client, forecastDays = 1)
        val request = SolarEstimateRequest(
            latitude = 6.5,
            longitude = 3.3,
            panelWattage = 500.0,
            panelCount = 4,
            panelTilt = null,
            azimuth = null,
            date = "2026-03-14"
        )

        val result = runBlocking { forecastClient.forecast(request) }
        val entry = result.forecasts.first()

        assertEquals("2026-03-12T12:00", entry.peakIrradianceTime)
        assertEquals("2026-03-12T09:00", entry.sunWindowStart)
        assertEquals("2026-03-12T15:00", entry.sunWindowEnd)
        assertTrue(entry.peakIrradiance.value > 0)
    }

    @Test
    fun `forecast handles empty hourly arrays`() {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"hourly": {"time": [], "shortwave_radiation": []}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val forecastClient = OpenMeteoForecastClient(client, forecastDays = 1)
        val request = SolarEstimateRequest(0.0, 0.0, 400.0, 4, date = "2026-03-14")

        val thrown: Throwable? = runCatching { runBlocking { forecastClient.forecast(request) } }.exceptionOrNull()
        assertTrue(thrown is ExternalServiceException)
    }

    @Test
    fun `forecast limits to configured days`() {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                  "hourly": {
                    "time": ["2026-03-12T00:00", "2026-03-13T00:00"],
                    "shortwave_radiation": [100, 200]
                  }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val forecastClient = OpenMeteoForecastClient(client, forecastDays = 1)
        val request = SolarEstimateRequest(0.0, 0.0, 400.0, 4, date = "2026-03-14")

        val result = runBlocking { forecastClient.forecast(request) }
        assertEquals(1, result.forecasts.size)
    }
}
