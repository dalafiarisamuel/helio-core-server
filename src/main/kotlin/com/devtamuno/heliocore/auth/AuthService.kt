package com.devtamuno.heliocore.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.devtamuno.heliocore.config.JwtSettings
import com.devtamuno.heliocore.domain.AuthResponse
import com.devtamuno.heliocore.domain.LoginRequest
import com.devtamuno.heliocore.domain.RefreshRequest
import com.devtamuno.heliocore.domain.RegisterRequest
import com.devtamuno.heliocore.domain.UserResponse
import com.devtamuno.heliocore.domain.ValidationException
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val jwtSettings: JwtSettings
) {

    suspend fun register(request: RegisterRequest): AuthResponse {
        validateCredentials(request.email, request.password)
        val hash = BCrypt.hashpw(request.password, BCrypt.gensalt())
        val user = userRepository.createUser(request.email, hash)
        return generateAuthResponse(user.id, user.email)
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email.trim().lowercase())
            ?: throw ValidationException("Invalid email or password")
        if (!BCrypt.checkpw(request.password, user.passwordHash)) {
            throw ValidationException("Invalid email or password")
        }

        return generateAuthResponse(user.id, user.email)
    }

    suspend fun refreshToken(request: RefreshRequest): AuthResponse {
        val (token, userId) = userRepository.findRefreshToken(request.refreshToken)
            ?: throw ValidationException("Invalid or expired refresh token")

        val user = userRepository.findById(userId)
            ?: throw ValidationException("User not found")

        userRepository.deleteRefreshToken(token)
        return generateAuthResponse(user.id, user.email)
    }

    private suspend fun generateAuthResponse(userId: UUID, email: String): AuthResponse {
        val accessToken = issueToken(userId, email)
        val refreshToken = UUID.randomUUID().toString()
        val expiresAt = LocalDateTime.now().plusDays(jwtSettings.refreshExpiryDays)

        userRepository.saveRefreshToken(refreshToken, userId, expiresAt)

        return AuthResponse(
            token = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtSettings.expirySeconds,
            user = UserResponse(email = email)
        )
    }

    private fun issueToken(userId: UUID, email: String): String {
        val now = Instant.now()
        val expiresAt = Date.from(now.plusSeconds(jwtSettings.expirySeconds))
        return JWT.create()
            .withIssuer(jwtSettings.issuer)
            .withAudience(jwtSettings.audience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("nonce", UUID.randomUUID().toString())
            .withExpiresAt(expiresAt)
            .sign(Algorithm.HMAC256(jwtSettings.secret))
    }

    private fun validateCredentials(email: String, password: String) {
        if (!EMAIL_REGEX.matches(email.trim().lowercase())) {
            throw ValidationException("Invalid email format")
        }
        if (password.length < 8) throw ValidationException("Password must be at least 8 characters")
    }

    companion object {
        private val EMAIL_REGEX =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
