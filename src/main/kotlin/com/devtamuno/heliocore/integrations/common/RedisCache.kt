package com.devtamuno.heliocore.integrations.common

import com.devtamuno.heliocore.domain.SolarEstimateRequest
import com.devtamuno.heliocore.domain.SolarForecastResponse
import com.devtamuno.heliocore.domain.SolarPotentialResponse
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.RedisCredentials
import io.lettuce.core.RedisCredentialsProvider
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.json.Json
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory

class CachingSolarDataProvider(
    private val delegate: SolarDataProvider,
    private val connection: StatefulRedisConnection<String, String>,
    private val ttlSeconds: Long = 1800
) : SolarDataProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(CachingSolarDataProvider::class.java)

    override suspend fun fetchSolarData(
        request: SolarEstimateRequest,
        systemCapacityKw: Double
    ): SolarPotentialResponse {
        val key =
            "pv:${request.latitude}:${request.longitude}:${request.panelTilt}:${request.azimuth}:${request.panelWattage}:${request.panelCount}"
        val commands = connection.async()
        val cached = commands.get(key).await()
        if (cached != null) {
            val decoded: SolarPotentialResponse = json.decodeFromString(cached)
            logger.info(
                "Redis cache hit for solar data key={}, acAnnual={} {}",
                key,
                decoded.acAnnual.value,
                decoded.acAnnual.unit
            )
            return decoded
        }

        logger.info("Redis cache miss for solar data key={}, fetching delegate", key)

        val fresh = delegate.fetchSolarData(request, systemCapacityKw)
        commands.setex(key, ttlSeconds, json.encodeToString(fresh))
        logger.info(
            "Redis cache set for solar data key={} ttlSeconds={} acAnnual={} {}",
            key,
            ttlSeconds,
            fresh.acAnnual.value,
            fresh.acAnnual.unit
        )
        return fresh
    }
}

class CachingSolarForecastProvider(
    private val delegate: SolarForecastProvider,
    private val connection: StatefulRedisConnection<String, String>,
    private val ttlSeconds: Long = 3600
) : SolarForecastProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(CachingSolarForecastProvider::class.java)

    override suspend fun forecast(request: SolarEstimateRequest): SolarForecastResponse {
        val key = "fc:${request.latitude}:${request.longitude}:${request.panelWattage}:${request.panelCount}"
        val commands = connection.async()
        val cached = commands.get(key).await()
        if (cached != null) {
            val decoded: SolarForecastResponse = json.decodeFromString(cached)
            logger.info(
                "Redis cache hit for forecast key={}, days={}, avgExpectedEnergy={}",
                key,
                decoded.forecasts.size,
                decoded.forecasts.map { it.expectedEnergy.value }.average()
            )
            return decoded
        }

        logger.info("Redis cache miss for forecast key={}, fetching delegate", key)

        val fresh = delegate.forecast(request)
        commands.setex(key, ttlSeconds, json.encodeToString(fresh))
        logger.info(
            "Redis cache set for forecast key={} ttlSeconds={} days={} avgExpectedEnergy={}",
            key,
            ttlSeconds,
            fresh.forecasts.size,
            fresh.forecasts.map { it.expectedEnergy.value }.average()
        )
        return fresh
    }
}

object RedisFactory {
    fun connect(uri: String, username: String?, password: String?): StatefulRedisConnection<String, String> {
        val redisUri = RedisURI.create(uri)
        if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
            val provider = RedisCredentialsProvider.from {
                RedisCredentials.just(username ?: "", (password ?: "").toCharArray())
            }
            redisUri.setCredentialsProvider(provider)
        }
        return RedisClient.create(redisUri).connect()
    }
}
