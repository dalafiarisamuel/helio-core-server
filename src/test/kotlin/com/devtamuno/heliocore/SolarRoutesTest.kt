package com.devtamuno.heliocore

import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarEstimateResponse
import com.devtamuno.heliocore.domain.SolarPotentialResponse
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.routes.configureRoutes
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SolarRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `estimate endpoint returns units per value`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse {
                    return SolarPotentialResponse(
                        solradAnnual = MeasuredValue(4.5, "kWh/m²/day"),
                        acMonthly = emptyList(),
                        acAnnual = MeasuredValue(0.0, "kWh"),
                        panelWattage = request.panelWattage,
                        panelCount = request.panelCount
                    )
                }
            }
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null)
        }

        val client = createClient {
            install(ContentNegotiation) { json(json) }
        }

        val response = client.post("/solar/estimate") {
            contentType(ContentType.Application.Json)
            setBody(
                SolarEstimateRequest(
                    latitude = 37.0,
                    longitude = -122.0,
                    panelWattage = 400.0,
                    panelCount = 10,
                    panelTilt = 20.0,
                    azimuth = 180.0
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SolarEstimateResponse = response.body()
        assertEquals("kW", body.systemCapacity.unit)
        assertEquals("hours", body.peakSunHours.unit)
        assertEquals("kWh", body.dailyEnergy.unit)
    }

    @Test
    fun `estimate endpoint validates missing body fields`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse =
                    SolarPotentialResponse(MeasuredValue(4.5, "kWh/m²/day"), emptyList(), MeasuredValue(0.0, "kWh"), panelWattage = request.panelWattage, panelCount = request.panelCount)
            }
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.post("/solar/estimate") {
            contentType(ContentType.Application.Json)
            setBody("""{"latitude":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().isNotBlank())
    }

    @Test
    fun `potential endpoint returns measured values`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse =
                    SolarPotentialResponse(
                        solradAnnual = MeasuredValue(5.1, "kWh/m²/day"),
                        acMonthly = listOf(MeasuredValue(400.0, "kWh")),
                        acAnnual = MeasuredValue(4800.0, "kWh"),
                        panelWattage = request.panelWattage,
                        panelCount = request.panelCount
                    )
            }
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }
        val response = client.post("/solar/potential") {
            contentType(ContentType.Application.Json)
            setBody(
                SolarEstimateRequest(
                    latitude = 1.0,
                    longitude = 1.0,
                    panelWattage = 1000.0,
                    panelCount = 1
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SolarPotentialResponse = response.body()
        assertEquals("kWh/m²/day", body.solradAnnual.unit)
        assertTrue(body.acMonthly.first().value > 0)
    }
}
