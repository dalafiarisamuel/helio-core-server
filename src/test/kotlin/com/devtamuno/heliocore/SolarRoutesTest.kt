package com.devtamuno.heliocore

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.domain.*
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.routes.configureRoutes
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SolarRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun jwtSettings() = JwtSettings(
        secret = "test-secret",
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        expiryMinutes = 60,
        refreshExpiryDays = 30
    )

    private fun authConfig(jwtSettings: JwtSettings): Pair<AuthenticationConfig.() -> Unit, String> {
        val token = JWT.create()
            .withAudience(jwtSettings.audience)
            .withIssuer(jwtSettings.issuer)
            .withClaim("email", "user@example.com")
            .sign(Algorithm.HMAC256(jwtSettings.secret))
        val config: AuthenticationConfig.() -> Unit = {
            jwt("auth-jwt") {
                realm = jwtSettings.realm
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(jwtSettings.secret))
                        .withAudience(jwtSettings.audience)
                        .withIssuer(jwtSettings.issuer)
                        .build()
                )
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        return config to token
    }

    @Test
    fun `estimate endpoint returns units per value`() = testApplication {
        val jwtSettings = jwtSettings()
        val (authCfg, token) = authConfig(jwtSettings)
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
                register(RateLimitName("user")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(Authentication, configure = authCfg)
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
            val db = Database.connect("jdbc:h2:mem:solar-estimate;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            val userRepository = UserRepository(db)
            userRepository.ensureSchema()
            val authService = AuthService(userRepository, jwtSettings)
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null, authService = authService)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.post("/solar/estimate") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
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
        val jwtSettings = jwtSettings()
        val (authCfg, token) = authConfig(jwtSettings)
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
                register(RateLimitName("user")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(Authentication, configure = authCfg)
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse =
                    SolarPotentialResponse(MeasuredValue(4.5, "kWh/m²/day"), emptyList(), MeasuredValue(0.0, "kWh"), panelWattage = request.panelWattage, panelCount = request.panelCount)
            }
            val db = Database.connect("jdbc:h2:mem:solar-validate;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            val userRepository = UserRepository(db)
            userRepository.ensureSchema()
            val authService = AuthService(userRepository, jwtSettings)
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null, authService = authService)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }

        val response = client.post("/solar/estimate") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"latitude":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().isNotBlank())
    }

    @Test
    fun `potential endpoint returns measured values`() = testApplication {
        val jwtSettings = jwtSettings()
        val (authCfg, token) = authConfig(jwtSettings)
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
                register(RateLimitName("user")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(Authentication, configure = authCfg)
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
            val db = Database.connect("jdbc:h2:mem:solar-potential;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            val userRepository = UserRepository(db)
            userRepository.ensureSchema()
            val authService = AuthService(userRepository, jwtSettings)
            configureRoutes(calculator, fakeProvider, solarForecastProvider = null, authService = authService)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }
        val response = client.post("/solar/potential") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
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
        assertEquals(1000.0, body.panelWattage)
        assertEquals(1, body.panelCount)
    }

    @Test
    fun `forecast endpoint returns window times`() = testApplication {
        val jwtSettings = jwtSettings()
        val (authCfg, token) = authConfig(jwtSettings)
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
                register(RateLimitName("user")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            install(Authentication, configure = authCfg)
            val calculator = SolarProductionCalculator()
            val fakeData = object : SolarDataProvider {
                override suspend fun fetchSolarData(request: SolarEstimateRequest, systemCapacityKw: Double): SolarPotentialResponse =
                    SolarPotentialResponse(
                        solradAnnual = MeasuredValue(5.0, "kWh/m²/day"),
                        acMonthly = emptyList(),
                        acAnnual = MeasuredValue(0.0, "kWh"),
                        panelWattage = request.panelWattage,
                        panelCount = request.panelCount
                    )
            }
            val fakeForecast = object : SolarForecastProvider {
                override suspend fun forecast(request: SolarEstimateRequest): SolarForecastResponse =
                    SolarForecastResponse(
                        forecasts = listOf(
                            SolarForecastEntry(
                                date = "2026-03-12",
                                peakSunHours = MeasuredValue(5.0, "hours"),
                                expectedEnergy = MeasuredValue(10.0, "kWh"),
                                peakIrradianceTime = "2026-03-12T12:00",
                                peakIrradiance = MeasuredValue(500.0, "Wh/m²"),
                                sunWindowStart = "2026-03-12T09:00",
                                sunWindowEnd = "2026-03-12T15:00"
                            )
                        ),
                        panelWattage = request.panelWattage,
                        panelCount = request.panelCount
                    )
            }
            val db = Database.connect("jdbc:h2:mem:solar-forecast;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            val userRepository = UserRepository(db)
            userRepository.ensureSchema()
            val authService = AuthService(userRepository, jwtSettings)
            configureRoutes(calculator, fakeData, solarForecastProvider = fakeForecast, authService = authService)
        }

        val client = createClient { install(ContentNegotiation) { json(json) } }
        val response = client.post("/solar/forecast") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                SolarEstimateRequest(
                    latitude = 1.0,
                    longitude = 1.0,
                    panelWattage = 400.0,
                    panelCount = 10
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SolarForecastResponse>()
        assertTrue(body.forecasts.isNotEmpty())
        val entry = body.forecasts.first()
        assertEquals("2026-03-12T09:00", entry.sunWindowStart)
        assertEquals("2026-03-12T15:00", entry.sunWindowEnd)
        assertEquals(400.0, body.panelWattage)
        assertEquals(10, body.panelCount)
    }
}
