package com.devtamuno.heliocore.repository

import com.devtamuno.heliocore.domain.SolarConfigRequest
import com.devtamuno.heliocore.domain.SolarConfigResponse
import com.devtamuno.heliocore.tables.SolarConfigs
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SolarConfigRepository(private val db: Database) {

    suspend fun ensureSchema() {
        newSuspendedTransaction(Dispatchers.IO, db) {
            SchemaUtils.createMissingTablesAndColumns(SolarConfigs)
        }
    }

    suspend fun create(userId: UUID, request: SolarConfigRequest): SolarConfigResponse =
        newSuspendedTransaction(Dispatchers.IO, db) {
            val id = UUID.randomUUID()
            val now = LocalDateTime.now()
            SolarConfigs.insert {
                it[SolarConfigs.id] = id
                it[SolarConfigs.userId] = userId
                it[SolarConfigs.latitude] = request.latitude
                it[SolarConfigs.longitude] = request.longitude
                it[SolarConfigs.resolvedAddress] = request.resolvedAddress
                it[SolarConfigs.panelWattage] = request.panelWattage
                it[SolarConfigs.panelCount] = request.panelCount
                it[SolarConfigs.panelTilt] = request.panelTilt
                it[SolarConfigs.azimuth] = request.azimuth
                it[SolarConfigs.createdAt] = now
            }
            SolarConfigResponse(
                id = id.toString(),
                latitude = request.latitude,
                longitude = request.longitude,
                resolvedAddress = request.resolvedAddress,
                panelWattage = request.panelWattage,
                panelCount = request.panelCount,
                panelTilt = request.panelTilt,
                azimuth = request.azimuth,
                createdAt = now.format(DateTimeFormatter.ISO_DATE_TIME)
            )
        }

    suspend fun getByUserId(userId: UUID): List<SolarConfigResponse> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            SolarConfigs.selectAll()
                .where { SolarConfigs.userId eq userId }
                .orderBy(SolarConfigs.createdAt to SortOrder.DESC)
                .map { it.toSolarConfigResponse() }
        }

    suspend fun deleteById(userId: UUID, id: UUID): Boolean =
        newSuspendedTransaction(Dispatchers.IO, db) {
            SolarConfigs.deleteWhere { (SolarConfigs.id eq id) and (SolarConfigs.userId eq userId) } > 0
        }

    suspend fun update(userId: UUID, id: UUID, request: SolarConfigRequest): Boolean =
        newSuspendedTransaction(Dispatchers.IO, db) {
            SolarConfigs.update({ (SolarConfigs.id eq id) and (SolarConfigs.userId eq userId) }) {
                it[latitude] = request.latitude
                it[longitude] = request.longitude
                it[resolvedAddress] = request.resolvedAddress
                it[panelWattage] = request.panelWattage
                it[panelCount] = request.panelCount
                it[panelTilt] = request.panelTilt
                it[azimuth] = request.azimuth
            } > 0
        }

    private fun ResultRow.toSolarConfigResponse() = SolarConfigResponse(
        id = this[SolarConfigs.id].value.toString(),
        latitude = this[SolarConfigs.latitude],
        longitude = this[SolarConfigs.longitude],
        resolvedAddress = this[SolarConfigs.resolvedAddress],
        panelWattage = this[SolarConfigs.panelWattage],
        panelCount = this[SolarConfigs.panelCount],
        panelTilt = this[SolarConfigs.panelTilt],
        azimuth = this[SolarConfigs.azimuth],
        createdAt = this[SolarConfigs.createdAt].format(DateTimeFormatter.ISO_DATE_TIME)
    )
}
