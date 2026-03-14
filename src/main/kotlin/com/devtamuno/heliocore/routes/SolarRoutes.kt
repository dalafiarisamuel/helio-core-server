package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarPotentialRequest
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Route.solarRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider,
    solarForecastProvider: SolarForecastProvider?
) {
    route("/solar") {
        authenticate("auth-jwt") {
            rateLimit(RateLimitName("user")) {
                post("/estimate") {
                    val userId = call.userId.toString()
                    val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val body = call.receive<SolarEstimateRequest>()
                    val request = body.copy(date = body.date ?: currentDate)
                    calculator.validate(request)
                    val capacityKw = calculator.systemCapacityKw(request)
                    val potential = solarDataProvider.fetchSolarData(request, capacityKw, userId)
                    val estimate = calculator.calculate(request, potential.solradAnnual.value)
                    call.respond(estimate)
                }

                post("/forecast") {
                    val userId = call.userId.toString()
                    val provider = solarForecastProvider ?: throw ValidationException("Forecast provider not configured")
                    val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val body = call.receive<SolarPotentialRequest>()
                    val requestWithDate = body.copy(date = body.date ?: currentDate)
                    val estimateReq = requestWithDate.toEstimateRequest(defaultPanelWattage = 400.0, defaultPanelCount = 10)
                    calculator.validate(estimateReq)
                    val forecast = provider.forecast(estimateReq, userId)
                    call.respond(forecast)
                }

                post("/potential") {
                    val userId = call.userId.toString()
                    val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val body = call.receive<SolarPotentialRequest>()
                    val requestWithDate = body.copy(date = body.date ?: currentDate)
                    val estimateReq = requestWithDate.toEstimateRequest(defaultPanelWattage = 1000.0, defaultPanelCount = 1)
                    calculator.validate(estimateReq)
                    val capacityKw = calculator.systemCapacityKw(estimateReq)
                    val potential = solarDataProvider.fetchSolarData(estimateReq, capacityKw, userId)
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
    azimuth = azimuth,
    date = date
)
