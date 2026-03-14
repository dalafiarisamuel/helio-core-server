package com.devtamuno.heliocore.features.shared.web

import com.devtamuno.heliocore.features.auth.data.UserRepository
import com.devtamuno.heliocore.features.auth.service.AuthService
import com.devtamuno.heliocore.features.auth.web.authRoutes
import com.devtamuno.heliocore.features.solar.data.SolarConfigRepository
import com.devtamuno.heliocore.features.solar.service.SolarConfigService
import com.devtamuno.heliocore.features.solar.service.SolarProductionCalculator
import com.devtamuno.heliocore.features.solar.web.solarConfigRoutes
import com.devtamuno.heliocore.features.solar.web.solarRoutes
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing

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
