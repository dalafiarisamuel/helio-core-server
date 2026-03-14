package com.devtamuno.heliocore

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.repository.SolarConfigRepository
import com.devtamuno.heliocore.services.SolarConfigService
import com.devtamuno.heliocore.config.AppConfig
import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.config.buildAppModules
import com.devtamuno.heliocore.domain.ErrorResponse
import com.devtamuno.heliocore.domain.ExternalServiceException
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.domain.UnauthorizedException
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.routes.configureRoutes
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.logger.slf4jLogger
import com.zaxxer.hikari.HikariDataSource

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    val appConfig = AppConfig.fromConfig(environment.config)
    install(Koin) {
        slf4jLogger()
        modules(buildAppModules(appConfig))
    }

    val json by inject<Json>()
    val httpClient by inject<HttpClient>()
    val calculator by inject<SolarProductionCalculator>()
    val dataProvider by inject<SolarDataProvider>()
    val forecastProvider by inject<SolarForecastProvider>()
    val authService by inject<AuthService>()
    val solarConfigService by inject<SolarConfigService>()
    val jwtSettings by inject<JwtSettings>()
    val dataSource by inject<HikariDataSource>()
    val userRepository by inject<UserRepository>()
    val solarConfigRepository by inject<SolarConfigRepository>()

    launch {
        userRepository.ensureSchema()
        solarConfigRepository.ensureSchema()
    }

    monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        getKoin().getOrNull<StatefulRedisConnection<String, String>>()?.close()
        dataSource.close()
    }

    install(ContentNegotiation) { json(json) }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.Unauthorized,
                ErrorResponse(cause.message ?: "Unauthorized")
            )
        }
        exception<ExternalServiceException> { call, cause ->
            call.respond(
                io.ktor.http.HttpStatusCode.BadGateway,
                ErrorResponse(cause.message ?: "Upstream service error")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    install(RateLimit) {
        register(RateLimitName("global")) {
            rateLimiter(limit = 100, refillPeriod = 60.seconds)
        }
        register(RateLimitName("user")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call ->
                call.principal<JWTPrincipal>()?.subject ?: call.request.local.remoteAddress
            }
        }
    }

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
            validate { credential ->
                val email = credential.payload.getClaim("email").asString()
                if (!email.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
        }
    }

    configureRoutes(
        calculator,
        dataProvider,
        forecastProvider,
        authService,
        solarConfigService,
        userRepository,
        solarConfigRepository
    )
}
