package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.auth.AuthService
import com.devtamuno.heliocore.domain.LoginRequest
import com.devtamuno.heliocore.domain.RefreshRequest
import com.devtamuno.heliocore.domain.RegisterRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.authRoutes(authService: AuthService) {
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
            get("/me") {
                val principal = call.principal<JWTPrincipal>() ?: error("Missing principal")
                val userId = principal.subject?.let(UUID::fromString)
                val email = principal.getClaim("email", String::class)
                call.respond(mapOf("user_id" to userId?.toString(), "email" to email))
            }
        }
    }
}
