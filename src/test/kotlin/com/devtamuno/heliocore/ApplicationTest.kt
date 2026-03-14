package com.devtamuno.heliocore

import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.devtamuno.heliocore.domain.MeasuredValue
import com.devtamuno.heliocore.domain.MonthlySolarData
import com.devtamuno.heliocore.domain.SolarPotentialResponse
import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.routes.configureRoutes
import com.devtamuno.heliocore.services.SolarProductionCalculator
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.config.JwtSettings
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.Database
import com.devtamuno.heliocore.services.SolarConfigService
import com.devtamuno.heliocore.repository.SolarConfigRepository
import io.ktor.client.request.get
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json as clientJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

class ApplicationTest {
    @Test
    fun `health endpoint responds OK`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("global")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
                register(RateLimitName("user")) { rateLimiter(limit = 100, refillPeriod = 60.seconds) }
            }
            install(ContentNegotiation) { json() }
            val calculator = SolarProductionCalculator()
            val fakeProvider = object : SolarDataProvider {
                override suspend fun fetchSolarData(
                    request: SolarEstimateRequest,
                    systemCapacityKw: Double,
                    userId: String?
                ): SolarPotentialResponse =
                    SolarPotentialResponse(
                        solradAnnual = MeasuredValue(5.0, "kWh/m²/day"),
                        acMonthly = mapOf("january" to MonthlySolarData(MeasuredValue(0.0, "kWh"))),
                        acAnnual = MeasuredValue(0.0, "kWh"),
                        panelWattage = request.panelWattage,
                        panelCount = request.panelCount
                    )
            }
            val jwtSettings = JwtSettings(
                secret = "test-secret",
                issuer = "test-issuer",
                audience = "test-audience",
                realm = "test-realm",
                expiryMinutes = 60,
                refreshExpiryDays = 30
            )
            val db = Database.connect("jdbc:h2:mem:app-test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            val userRepository = UserRepository(db)
            val solarConfigRepository = SolarConfigRepository(db)
            userRepository.ensureSchema()
            solarConfigRepository.ensureSchema()
            val authService = AuthService(userRepository, jwtSettings)
            val solarConfigService = SolarConfigService(solarConfigRepository)
            install(Authentication) {
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
            configureRoutes(
                calculator,
                fakeProvider,
                solarForecastProvider = null,
                authService = authService,
                solarConfigService = solarConfigService,
                userRepository = userRepository,
                solarConfigRepository = solarConfigRepository
            )
        }

        val client = createClient { install(ClientContentNegotiation) { clientJson() } }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
