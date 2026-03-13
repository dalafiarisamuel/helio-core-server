package com.devtamuno.heliocore.config

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expiryMinutes: Long = 60,
    val refreshExpiryDays: Long = 30
)
