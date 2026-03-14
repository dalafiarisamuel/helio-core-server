package com.devtamuno.heliocore.features.auth.domain

import com.devtamuno.heliocore.features.solar.domain.SolarConfigResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    @SerialName("first_name") val firstName: String?,
    @SerialName("last_name") val lastName: String?,
    @SerialName("created_at") val createdAt: String,
    val configs: List<SolarConfigResponse> = emptyList()
)

@Serializable
data class UpdateUserRequest(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null
)

@Serializable
data class SeedResponse(
    val message: String,
    val user: String,
    val password: String,
    @SerialName("configs_added") val configsAdded: Int
)
