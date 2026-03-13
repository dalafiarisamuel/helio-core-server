package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarPotentialRequest
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.solarRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider,
    solarForecastProvider: SolarForecastProvider?
) {
    route("/solar") {
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("user")) {
                post("/estimate") {
                    val request = call.receive<SolarEstimateRequest>()
                    calculator.validate(request)
                    val capacityKw = calculator.systemCapacityKw(request)
                    val potential = solarDataProvider.fetchSolarData(request, capacityKw)
                    val estimate = calculator.calculate(request, potential.solradAnnual.value)
                    call.respond(estimate)
                }

                post("/forecast") {
                    val provider = solarForecastProvider ?: throw ValidationException("Forecast provider not configured")
                    val body = call.receive<SolarPotentialRequest>()
                    val estimateReq = body.toEstimateRequest(defaultPanelWattage = 400.0, defaultPanelCount = 10)
                    calculator.validate(estimateReq)
                    val forecast = provider.forecast(estimateReq)
                    call.respond(forecast)
                }

                post("/potential") {
                    val body = call.receive<SolarPotentialRequest>()
                    val estimateReq = body.toEstimateRequest(defaultPanelWattage = 1000.0, defaultPanelCount = 1)
                    calculator.validate(estimateReq)
                    val capacityKw = calculator.systemCapacityKw(estimateReq)
                    val potential = solarDataProvider.fetchSolarData(estimateReq, capacityKw)
                    call.respond(potential)
                }
            }
        }
    }
}

private fun SolarPotentialRequest.toEstimateRequest(
    defaultPanelWattage: Double,
    defaultPanelCount: Int
): SolarEstimateRequest = SolarEstimateRequest(
    latitude = latitude,
    longitude = longitude,
    panelWattage = panelWattage ?: defaultPanelWattage,
    panelCount = panelCount ?: defaultPanelCount,
    panelTilt = panelTilt,
    azimuth = azimuth
)
