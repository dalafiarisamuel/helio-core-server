package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.domain.RegisterRequest
import com.devtamuno.heliocore.domain.SeedResponse
import com.devtamuno.heliocore.domain.SolarConfigRequest
import com.devtamuno.heliocore.services.SolarConfigService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.devRoutes(
    authService: AuthService,
    userRepository: UserRepository,
    solarConfigService: SolarConfigService
) {
    route("/dev") {
        post("/seed") {
            val email = "dev@example.com"
            val password = "password123"

            // 1. Ensure user exists
            try {
                authService.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        firstName = "Dev",
                        lastName = "User"
                    )
                )
            } catch (e: Exception) {
                // Ignore if already registered
            }

            val user = userRepository.findByEmail(email)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, "Failed to find dev user")

            // 2. Add sample configs
            val sampleConfigs = listOf(
                SolarConfigRequest(
                    latitude = 34.0522,
                    longitude = -118.2437,
                    resolvedAddress = "Los Angeles, CA",
                    panelWattage = 400.0,
                    panelCount = 10,
                    panelTilt = 20.0,
                    azimuth = 180.0
                ),
                SolarConfigRequest(
                    latitude = 40.7128,
                    longitude = -74.0060,
                    resolvedAddress = "New York, NY",
                    panelWattage = 350.0,
                    panelCount = 12,
                    panelTilt = 35.0,
                    azimuth = 180.0
                )
            )

            val existingConfigs = solarConfigService.getByUserId(user.id)
            if (existingConfigs.isEmpty()) {
                sampleConfigs.forEach {
                    solarConfigService.create(user.id, it)
                }
            }

            call.respond(
                HttpStatusCode.Created,
                SeedResponse(
                    message = "Seed successful",
                    user = email,
                    password = password,
                    configsAdded = if (existingConfigs.isEmpty()) sampleConfigs.size else 0
                )
            )
        }
    }
}
