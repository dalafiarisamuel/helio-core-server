package com.devtamuno.heliocore.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.domain.ErrorResponse
import com.devtamuno.heliocore.features.solar.domain.SuccessResponse
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.features.auth.data.UserRepository
import com.devtamuno.heliocore.features.solar.data.SolarConfigRepository
import com.devtamuno.heliocore.features.solar.domain.SolarConfigRequest
import com.devtamuno.heliocore.features.solar.domain.SolarConfigResponse
import com.devtamuno.heliocore.features.solar.service.SolarConfigService
import com.devtamuno.heliocore.features.solar.web.solarConfigRoutes

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.util.UUID
import kotlin.test.*

class SolarConfigRoutesTest {

    private val jwtSettings = JwtSettings(
        secret = "test-secret",
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        expiryMinutes = 10,
        refreshExpiryDays = 1
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val testUserId = UUID.randomUUID()

    @Test
    fun `test store and retrieve solar config`() = testApplication {
        val db = Database.connect("jdbc:h2:mem:solar-config-test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        val userRepository = UserRepository(db)
        val repository = SolarConfigRepository(db)
        repository.ensureSchema()
        userRepository.ensureSchema()
        val service = SolarConfigService(repository)

        // Create user in DB so foreign key is satisfied
        userRepository.createUser(
            email = "test@example.com",
            passwordHash = "password",
            firstName = "Test",
            lastName = "User"
        )
        val userId = userRepository.findByEmail("test@example.com")!!.id

        val testToken = JWT.create()
            .withAudience(jwtSettings.audience)
            .withIssuer(jwtSettings.issuer)
            .withSubject(userId.toString())
            .withClaim("email", "test@example.com")
            .sign(Algorithm.HMAC256(jwtSettings.secret))

        application {
            install(ServerContentNegotiation) { json(json) }
            install(StatusPages) {
                exception<com.devtamuno.heliocore.domain.ValidationException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, com.devtamuno.heliocore.domain.ErrorResponse(cause.message ?: "Validation error"))
                }
            }
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = jwtSettings.realm
                    verifier(
                        JWT.require(Algorithm.HMAC256(jwtSettings.secret))
                            .withAudience(jwtSettings.audience)
                            .withIssuer(jwtSettings.issuer)
                            .build()
                    )
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
            routing {
                solarConfigRoutes(service)
            }
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        // 1. Create config
        val createResponse = client.post("/solar/configs") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(SolarConfigRequest(
                latitude = 45.0,
                longitude = 90.0,
                resolvedAddress = "Test Location",
                panelWattage = 400.0,
                panelCount = 10,
                panelTilt = 30.0,
                azimuth = 180.0
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<SolarConfigResponse>()
        assertEquals("Test Location", created.resolvedAddress)
        assertEquals(400.0, created.panelWattage)

        // 2. Get configs
        val getResponse = client.get("/solar/configs") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val configs = getResponse.body<List<SolarConfigResponse>>()
        assertTrue(configs.isNotEmpty())
        assertEquals(created.id, configs[0].id)

        // 3. Update config
        val updateResponse = client.put("/solar/configs/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
            contentType(ContentType.Application.Json)
            setBody(SolarConfigRequest(
                latitude = 46.0,
                longitude = 91.0,
                resolvedAddress = "Updated Location",
                panelWattage = 450.0,
                panelCount = 12,
                panelTilt = 35.0,
                azimuth = 190.0
            ))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateBody = updateResponse.body<SuccessResponse>()
        assertEquals("Updated successfully", updateBody.message)

        // 3.1 Verify updated
        val getAfterUpdateResponse = client.get("/solar/configs") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }
        val configsAfterUpdate = getAfterUpdateResponse.body<List<SolarConfigResponse>>()
        val updated = configsAfterUpdate.find { it.id == created.id }!!
        assertEquals("Updated Location", updated.resolvedAddress)
        assertEquals(450.0, updated.panelWattage)
        assertEquals(46.0, updated.latitude)

        // 4. Delete config
        val deleteResponse = client.delete("/solar/configs/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
        val deleteBody = deleteResponse.body<SuccessResponse>()
        assertEquals("Deleted successfully", deleteBody.message)

        // 4. Verify deleted
        val getAfterDeleteResponse = client.get("/solar/configs") {
            header(HttpHeaders.Authorization, "Bearer $testToken")
        }
        val configsAfterDelete = getAfterDeleteResponse.body<List<SolarConfigResponse>>()
        assertTrue(configsAfterDelete.isEmpty())
    }
}
