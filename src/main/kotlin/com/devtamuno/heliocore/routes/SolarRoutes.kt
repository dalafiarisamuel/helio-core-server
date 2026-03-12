package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.solarRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider,
    solarForecastProvider: SolarForecastProvider?
) {
    route("/solar") {
        post("/estimate") {
            val request = call.receive<SolarEstimateRequest>()
            calculator.validate(request)
            val capacityKw = calculator.systemCapacityKw(request)
            val potential = solarDataProvider.fetchSolarData(request, capacityKw)
            val estimate = calculator.calculate(request, potential.solradAnnual.value)
            call.respond(estimate)
        }

        get("/forecast") {
            val provider = solarForecastProvider ?: throw ValidationException("Forecast provider not configured")
            val request = call.queryEstimateRequest(defaultPanelWattage = 400.0, defaultPanelCount = 10)
            calculator.validate(request)
            val forecast = provider.forecast(request)
            call.respond(forecast)
        }

        get("/potential") {
            val request = call.queryEstimateRequest(defaultPanelWattage = 1000.0, defaultPanelCount = 1)
            calculator.validate(request)
            val capacityKw = calculator.systemCapacityKw(request)
            val potential = solarDataProvider.fetchSolarData(request, capacityKw)
            call.respond(potential)
        }
    }
}

private fun ApplicationCall.queryEstimateRequest(
    defaultPanelWattage: Double,
    defaultPanelCount: Int
): SolarEstimateRequest {
    val lat = request.queryParameters["lat"]?.toDoubleOrNull()
    val lon = request.queryParameters["lon"]?.toDoubleOrNull()
    val tilt = request.queryParameters["tilt"]?.toDoubleOrNull()
    val azimuth = request.queryParameters["azimuth"]?.toDoubleOrNull()
    val panelWattage = request.queryParameters["panel_wattage"]?.toDoubleOrNull() ?: defaultPanelWattage
    val panelCount = request.queryParameters["panel_count"]?.toIntOrNull() ?: defaultPanelCount

    if (lat == null || lon == null) throw ValidationException("lat and lon are required query params")

    return SolarEstimateRequest(
        latitude = lat,
        longitude = lon,
        panelWattage = panelWattage,
        panelCount = panelCount,
        panelTilt = tilt,
        azimuth = azimuth
    )
}
