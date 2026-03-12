package com.devtamuno.heliocore.config

import com.devtamuno.heliocore.integrations.common.CachingSolarDataProvider
import com.devtamuno.heliocore.integrations.common.CachingSolarForecastProvider
import com.devtamuno.heliocore.integrations.common.RedisFactory
import com.devtamuno.heliocore.integrations.common.SolarDataProvider
import com.devtamuno.heliocore.integrations.common.SolarForecastProvider
import com.devtamuno.heliocore.integrations.forecast.OpenMeteoForecastClient
import com.devtamuno.heliocore.integrations.pvwatts.PvWattsClient
import com.devtamuno.heliocore.services.SolarProductionCalculator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Builds the list of Koin modules for the application.
 * Caching providers are installed only when a Redis URL is configured.
 */
fun buildAppModules(appConfig: AppConfig): List<Module> {
    val logger = LoggerFactory.getLogger("DiModules")

    val core = module {
        single { Json { ignoreUnknownKeys = true; prettyPrint = false } }
        single {
            HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = appConfig.httpClientTimeoutMillis
                    connectTimeoutMillis = appConfig.httpClientTimeoutMillis
                    socketTimeoutMillis = appConfig.httpClientTimeoutMillis
                }
                install(ClientContentNegotiation) {
                    json(get())
                }
                install(Logging) {
                    level = LogLevel.INFO
                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
                }
            }
        }
        single { SolarProductionCalculator() }
        single { PvWattsClient(appConfig.pvWattsApiKey, get()) }
        single { OpenMeteoForecastClient(get()) }
    }

    val providers = appConfig.redisUrl?.let { redisUrl ->
        logger.info("Redis URL detected; enabling cached providers via Redis")
        module {
            single<StatefulRedisConnection<String, String>> {
                RedisFactory.connect(redisUrl, appConfig.redisUsername, appConfig.redisPassword)
            }
            single<SolarDataProvider> {
                CachingSolarDataProvider(get<PvWattsClient>(), get())
            }
            single<SolarForecastProvider> {
                CachingSolarForecastProvider(get<OpenMeteoForecastClient>(), get())
            }
        }
    } ?: module {
        logger.info("No Redis URL configured; using direct providers without caching")
        single<SolarDataProvider> { get<PvWattsClient>() }
        single<SolarForecastProvider> { get<OpenMeteoForecastClient>() }
    }

    return listOf(core, providers)
}
