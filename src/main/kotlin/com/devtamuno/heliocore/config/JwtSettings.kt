package com.devtamuno.heliocore.config

data class JwtSettings(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expiryMinutes: Long,
    val refreshExpiryDays: Long
) {
    val expirySeconds: Long = expiryMinutes * 60
}
