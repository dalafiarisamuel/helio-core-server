package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.integrations.SolarDataProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

fun Application.configureRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider
) {
    routing {
        rateLimit(RateLimitName("global")) {
            healthRoutes()
            solarRoutes(calculator, solarDataProvider)
        }
    }
}
