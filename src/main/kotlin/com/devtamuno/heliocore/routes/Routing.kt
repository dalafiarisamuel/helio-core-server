package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

fun Application.configureRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider,
    solarForecastProvider: SolarForecastProvider?
) {
    routing {
        rateLimit(RateLimitName("global")) {
            healthRoutes()
            solarRoutes(calculator, solarDataProvider, solarForecastProvider)
        }
    }
}
