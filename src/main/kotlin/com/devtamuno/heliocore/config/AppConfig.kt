package com.devtamuno.heliocore.config

data class AppConfig(
    val pvWattsApiKey: String,
    val serverPort: Int,
    val httpClientTimeoutMillis: Long = 30_000,
    val redisUrl: String?,
    val redisUsername: String? = null,
    val redisPassword: String? = null
) {
    companion object {

        fun fromConfig(config: io.ktor.server.config.ApplicationConfig): AppConfig {
            val apiKey = config.property("app.pvwattsApiKey").getString()
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing app.pvwattsApiKey (env PVWATTS_API_KEY)")

            val port = config.propertyOrNull("app.serverPort")?.getString()?.toIntOrNull() ?: 8080
            return AppConfig(
                pvWattsApiKey = apiKey,
                serverPort = port,
                redisUrl = config.propertyOrNull("app.redisUrl")?.getString(),
                redisUsername = config.propertyOrNull("app.redisUsername")?.getString(),
                redisPassword = config.propertyOrNull("app.redisPassword")?.getString()
            )
        }
    }
}
