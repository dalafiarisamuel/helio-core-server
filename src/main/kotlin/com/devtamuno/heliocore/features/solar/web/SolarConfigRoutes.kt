package com.devtamuno.heliocore.features.solar.web

import com.devtamuno.heliocore.features.shared.web.pathId
import com.devtamuno.heliocore.features.shared.web.userId
import com.devtamuno.heliocore.features.solar.domain.SolarConfigRequest
import com.devtamuno.heliocore.features.solar.domain.SuccessResponse
import com.devtamuno.heliocore.features.solar.service.SolarConfigService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.solarConfigRoutes(solarConfigService: SolarConfigService) {
    authenticate("auth-jwt") {
        route("/solar/configs") {
            post {
                val userId = call.userId
                val request = call.receive<SolarConfigRequest>()
                val response = solarConfigService.create(userId, request)
                call.respond(HttpStatusCode.Created, response)
            }

            get {
                val userId = call.userId
                val response = solarConfigService.getByUserId(userId)
                call.respond(response)
            }

            put("/{id}") {
                val userId = call.userId
                val id = call.pathId
                val request = call.receive<SolarConfigRequest>()
                val updated = solarConfigService.update(userId, id, request)
                if (updated) {
                    call.respond(HttpStatusCode.OK, SuccessResponse("Updated successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/{id}") {
                val userId = call.userId
                val id = call.pathId
                val deleted = solarConfigService.deleteById(userId, id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, SuccessResponse("Deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

