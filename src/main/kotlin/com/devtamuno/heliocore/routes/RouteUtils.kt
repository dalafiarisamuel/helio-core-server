package com.devtamuno.heliocore.routes

import com.devtamuno.heliocore.domain.UnauthorizedException
import com.devtamuno.heliocore.domain.ValidationException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

val ApplicationCall.userId: UUID
    get() {
        val principal = principal<JWTPrincipal>()
            ?: throw UnauthorizedException("Invalid authentication")
        return principal.subject?.let(UUID::fromString)
            ?: throw UnauthorizedException("Invalid authentication")
    }

val ApplicationCall.pathId: String
    get() = parameters["id"] ?: throw ValidationException("Missing id")
