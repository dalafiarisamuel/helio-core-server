package com.devtamuno.heliocore

import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.devtamuno.heliocore.routes.configureRoutes
import com.devtamuno.heliocore.services.SolarProductionCalculator
import com.devtamuno.heliocore.integrations.SolarDataProvider
import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.SolarPotentialResponse
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json as clientJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

class ApplicationTest {
    @Test
    fun `health endpoint responds OK`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(ContentNegotiation) { json() }
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse =
                    SolarPotentialResponse(
                        solradAnnual = MeasuredValue(5.0, "kWh/m²/day"),
                        acMonthly = emptyList(),
                        acAnnual = MeasuredValue(0.0, "kWh")
                    )
            }
            configureRoutes(calculator, fakeProvider)
        }

        val client = createClient { install(ClientContentNegotiation) { clientJson() } }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
