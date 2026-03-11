package com.devtamuno.heliocore.config

data class AppConfig(
    val pvWattsApiKey: String,
    val serverPort: Int,
    val httpClientTimeoutSeconds: Long = 30
) {
    companion object {
        fun fromEnv(): AppConfig {
            val apiKey = System.getenv("PVWATTS_API_KEY")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing PVWATTS_API_KEY environment variable")
            val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
            return AppConfig(apiKey, port)
        }
    }
}
