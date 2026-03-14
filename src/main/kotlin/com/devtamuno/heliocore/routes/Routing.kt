package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.repository.SolarConfigRepository
import com.devtamuno.heliocore.services.SolarConfigService

fun Application.configureRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider,
    solarForecastProvider: SolarForecastProvider?,
    authService: AuthService,
    solarConfigService: SolarConfigService,
    userRepository: UserRepository,
    solarConfigRepository: SolarConfigRepository
) {
    routing {
        rateLimit(RateLimitName("global")) {
            authRoutes(authService, userRepository, solarConfigRepository)
            healthRoutes()
            solarRoutes(calculator, solarDataProvider, solarForecastProvider)
            solarConfigRoutes(solarConfigService)

            // Dev only routes
            if (developmentMode) {
                devRoutes(authService, userRepository, solarConfigService)
            }
        }
    }
}
