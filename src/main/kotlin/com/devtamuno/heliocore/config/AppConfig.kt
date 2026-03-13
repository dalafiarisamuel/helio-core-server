package com.devtamuno.heliocore.config

data class AppConfig(
    val pvWattsApiKey: String,
    val serverPort: Int,
    val httpClientTimeoutMillis: Long = 30_000,
    val redis: RedisConfig,
    val db: DbConfig,
    val jwt: JwtConfig
) {
    companion object {
        fun fromConfig(config: io.ktor.server.config.ApplicationConfig): AppConfig {
            val apiKey = config.property("app.pvwattsApiKey").getString()
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing app.pvwattsApiKey (env PVWATTS_API_KEY)")

            val port = config.propertyOrNull("app.serverPort")?.getString()?.toIntOrNull() ?: 8080

            val jwtSecret = config.property("app.jwt.secret").getString()
                .takeIf { it.isNotBlank() } ?: throw IllegalStateException("Missing app.jwt.secret (env JWT_SECRET)")

            val redis = RedisConfig(
                url = config.propertyOrNull("app.redis.url")?.getString(),
                username = config.propertyOrNull("app.redis.username")?.getString(),
                password = config.propertyOrNull("app.redis.password")?.getString()
            )

            val db = DbConfig(
                url = config.propertyOrNull("app.db.url")?.getString() ?: "",
                user = config.propertyOrNull("app.db.user")?.getString() ?: "",
                password = config.propertyOrNull("app.db.password")?.getString() ?: ""
            )

            val jwt = JwtConfig(
                secret = jwtSecret,
                issuer = config.propertyOrNull("app.jwt.issuer")?.getString() ?: "",
                audience = config.propertyOrNull("app.jwt.audience")?.getString() ?: "",
                realm = config.propertyOrNull("app.jwt.realm")?.getString() ?: "",
                expiryMinutes = config.propertyOrNull("app.jwt.expiryMinutes")?.getString()?.toLongOrNull() ?: 60
            )

            return AppConfig(
                pvWattsApiKey = apiKey,
                serverPort = port,
                redis = redis,
                db = db,
                jwt = jwt
            )
        }
    }
}
