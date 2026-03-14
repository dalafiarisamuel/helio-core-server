package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.auth.UserRepository
import com.devtamuno.heliocore.domain.*
import com.devtamuno.heliocore.repository.SolarConfigRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.http.HttpStatusCode
import java.time.format.DateTimeFormatter

fun Route.authRoutes(
    authService: AuthService,
    userRepository: UserRepository,
    solarConfigRepository: SolarConfigRepository
) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = authService.register(request)
            call.respond(response)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request)
            call.respond(response)
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val response = authService.refreshToken(request)
            call.respond(response)
        }

        authenticate("auth-jwt") {
            get("/profile") {
                val userId = call.userId

                val user = userRepository.findById(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                val configs = solarConfigRepository.getByUserId(userId)

                call.respond(
                    UserResponse(
                        id = user.id.toString(),
                        email = user.email,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        createdAt = user.createdAt.format(DateTimeFormatter.ISO_DATE_TIME),
                        configs = configs
                    )
                )
            }

            patch("/profile") {
                val userId = call.userId

                val request = call.receive<UpdateUserRequest>()
                val response = authService.updateUser(userId, request)
                call.respond(response)
            }
        }
    }
}
