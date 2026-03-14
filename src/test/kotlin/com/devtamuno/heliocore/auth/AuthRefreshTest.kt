package com.devtamuno.heliocore.auth

import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.domain.LoginRequest
import com.devtamuno.heliocore.domain.RefreshRequest
import com.devtamuno.heliocore.domain.RegisterRequest
import com.devtamuno.heliocore.domain.UpdateUserRequest
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import com.devtamuno.heliocore.routes.authRoutes
import com.devtamuno.heliocore.routes.solarConfigRoutes
import io.ktor.serialization.kotlinx.json.json as serverJson
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devtamuno.heliocore.services.SolarConfigService

class AuthRefreshTest {

    private val jwtSettings = JwtSettings(
        secret = "test-secret",
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        expiryMinutes = 1,
        refreshExpiryDays = 1
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test register login and refresh token`() = testApplication {
        val db = Database.connect("jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        val userRepository = UserRepository(db)
        val solarConfigRepository = com.devtamuno.heliocore.repository.SolarConfigRepository(db)
        val solarConfigService = SolarConfigService(solarConfigRepository)
        userRepository.ensureSchema()
        solarConfigRepository.ensureSchema()
        val authService = AuthService(userRepository, jwtSettings)

        application {
            install(ServerContentNegotiation) { serverJson(json) }
            install(StatusPages) {
                exception<com.devtamuno.heliocore.domain.ValidationException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, com.devtamuno.heliocore.domain.ErrorResponse(cause.message ?: "Validation error"))
                }
                exception<com.devtamuno.heliocore.domain.UnauthorizedException> { call, cause ->
                    call.respond(HttpStatusCode.Unauthorized, com.devtamuno.heliocore.domain.ErrorResponse(cause.message ?: "Unauthorized"))
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
                authRoutes(authService, userRepository, solarConfigRepository)
                solarConfigRoutes(solarConfigService)
            }
        }

        val client = createClient {
            install(ClientContentNegotiation) {
                json(json)
            }
        }

        // 1. Register
        val regResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("test@example.com", "password123", "Test", "User"))
        }
        assertEquals(HttpStatusCode.OK, regResponse.status)
        val regAuth = regResponse.body<com.devtamuno.heliocore.domain.AuthResponse>()
        assertNotNull(regAuth.refreshToken)

        // 2. Login
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("test@example.com", "password123"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val loginAuth = loginResponse.body<com.devtamuno.heliocore.domain.AuthResponse>()
        assertNotNull(loginAuth.refreshToken)

        // 3. Refresh
        val refreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(loginAuth.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshAuth = refreshResponse.body<com.devtamuno.heliocore.domain.AuthResponse>()
        assertNotEquals(loginAuth.token, refreshAuth.token)
        assertNotEquals(loginAuth.refreshToken, refreshAuth.refreshToken)

        // 3.1 Verify /profile works and queries DB
        val profileResponse = client.get("/auth/profile") {
            header(HttpHeaders.Authorization, "Bearer ${refreshAuth.token}")
        }
        assertEquals(HttpStatusCode.OK, profileResponse.status)
        val profileUser = profileResponse.body<com.devtamuno.heliocore.domain.UserResponse>()
        assertEquals("test@example.com", profileUser.email)
        assertEquals("Test", profileUser.firstName)
        assertEquals("User", profileUser.lastName)
        assertNotNull(profileUser.id)
        assertNotNull(profileUser.createdAt)
        assertTrue(profileUser.configs.isEmpty())

        // 3.1.1 Add a config and verify it appears in /profile
        client.post("/solar/configs") {
            header(HttpHeaders.Authorization, "Bearer ${refreshAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(com.devtamuno.heliocore.domain.SolarConfigRequest(
                latitude = 40.7128,
                longitude = -74.0060,
                panelWattage = 300.0,
                panelCount = 10,
                panelTilt = 30.0,
                azimuth = 180.0
            ))
        }

        val profileWithConfigsResponse = client.get("/auth/profile") {
            header(HttpHeaders.Authorization, "Bearer ${refreshAuth.token}")
        }
        val profileWithConfigs = profileWithConfigsResponse.body<com.devtamuno.heliocore.domain.UserResponse>()
        assertEquals(1, profileWithConfigs.configs.size)
        assertEquals(40.7128, profileWithConfigs.configs[0].latitude)

        // 3.2 Update info
        val updateResponse = client.patch("/auth/profile") {
            header(HttpHeaders.Authorization, "Bearer ${refreshAuth.token}")
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequest(firstName = "NewName", lastName = "NewSurname"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updatedUser = updateResponse.body<com.devtamuno.heliocore.domain.UserResponse>()
        assertEquals("NewName", updatedUser.firstName)
        assertEquals("NewSurname", updatedUser.lastName)
        assertEquals("test@example.com", updatedUser.email) // Email stays same

        // 4. Refresh with old token should fail
        val secondRefreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(loginAuth.refreshToken))
        }
        assertEquals(HttpStatusCode.BadRequest, secondRefreshResponse.status)
    }

    @Test
    fun `test unauthorized access should return 401 with friendly message`() = testApplication {
        val db = Database.connect("jdbc:h2:mem:auth-unauth-test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        val userRepository = UserRepository(db)
        val solarConfigRepository = com.devtamuno.heliocore.repository.SolarConfigRepository(db)
        val solarConfigService = SolarConfigService(solarConfigRepository)
        userRepository.ensureSchema()
        solarConfigRepository.ensureSchema()
        val authService = AuthService(userRepository, jwtSettings)

        application {
            install(ServerContentNegotiation) { serverJson(json) }
            install(StatusPages) {
                exception<com.devtamuno.heliocore.domain.UnauthorizedException> { call, cause ->
                    call.respond(HttpStatusCode.Unauthorized, com.devtamuno.heliocore.domain.ErrorResponse(cause.message ?: "Unauthorized"))
                }
            }
            install(Authentication) {
                jwt("auth-jwt") {
                    realm = jwtSettings.realm
                    verifier(JWT.require(Algorithm.HMAC256(jwtSettings.secret)).build())
                    validate { credential ->
                        val email = credential.payload.getClaim("email").asString()
                        // Force invalid ID for testing if needed, or just let it fail principal check
                        if (email == "invalid@example.com") {
                            JWTPrincipal(credential.payload) // principal subject will be missing
                        } else if (email == "valid@example.com") {
                            JWTPrincipal(credential.payload)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                authRoutes(authService, userRepository, solarConfigRepository)
                solarConfigRoutes(solarConfigService)
            }
        }

        val client = createClient {
            install(ClientContentNegotiation) { json(json) }
        }

        // Case 1: No token
        val noTokenResponse = client.get("/auth/profile")
        assertEquals(HttpStatusCode.Unauthorized, noTokenResponse.status)

        // Case 2: Invalid user id (missing subject in JWT)
        val tokenWithoutSubject = JWT.create()
            .withClaim("email", "invalid@example.com")
            .sign(Algorithm.HMAC256(jwtSettings.secret))

        val invalidIdResponse = client.get("/auth/profile") {
            header(HttpHeaders.Authorization, "Bearer $tokenWithoutSubject")
        }
        assertEquals(HttpStatusCode.Unauthorized, invalidIdResponse.status)
        val errorBody = invalidIdResponse.body<com.devtamuno.heliocore.domain.ErrorResponse>()
        assertEquals("Invalid authentication", errorBody.message)
    }
}
