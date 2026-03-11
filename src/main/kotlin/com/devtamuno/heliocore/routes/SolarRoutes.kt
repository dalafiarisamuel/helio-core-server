package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.ValidationException
import com.devtamuno.heliocore.integrations.SolarDataProvider
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.solarRoutes(
    calculator: SolarProductionCalculator,
    solarDataProvider: SolarDataProvider
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

        get("/potential") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val tilt = call.request.queryParameters["tilt"]?.toDoubleOrNull()
            val azimuth = call.request.queryParameters["azimuth"]?.toDoubleOrNull()

            if (lat == null || lon == null) throw ValidationException("lat and lon are required query params")

            val request = SolarEstimateRequest(
                latitude = lat,
                longitude = lon,
                panelWattage = 1000.0,
                panelCount = 1,
                panelTilt = tilt,
                azimuth = azimuth
            )
            calculator.validate(request)
            val capacityKw = calculator.systemCapacityKw(request)
            val potential = solarDataProvider.fetchSolarData(request, capacityKw)
            call.respond(potential)
        }
    }
}
