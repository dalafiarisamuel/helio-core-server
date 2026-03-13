package com.devtamuno.heliocore.auth

import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.domain.LoginRequest
import com.devtamuno.heliocore.domain.RefreshRequest
import com.devtamuno.heliocore.domain.RegisterRequest
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
import io.ktor.serialization.kotlinx.json.json as serverJson
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

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
        userRepository.ensureSchema()
        val authService = AuthService(userRepository, jwtSettings)

        application {
            install(ServerContentNegotiation) { serverJson(json) }
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
                authRoutes(authService)
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
            setBody(RegisterRequest("test@example.com", "password123"))
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

        // 4. Refresh with old token should fail
        val secondRefreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(loginAuth.refreshToken))
        }
        assertEquals(HttpStatusCode.BadRequest, secondRefreshResponse.status)
    }
}
