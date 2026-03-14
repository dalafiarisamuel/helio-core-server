package com.devtamuno.heliocore.tables

import com.devtamuno.heliocore.auth.tables.Users
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

/** Exposed table definition for solar configuration records. */
object SolarConfigs : UUIDTable("solar_configs") {
    val userId = reference("user_id", Users)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val resolvedAddress = varchar("resolved_address", 255).nullable()
    val panelWattage = double("panel_wattage")
    val panelCount = integer("panel_count")
    val panelTilt = double("panel_tilt")
    val azimuth = double("azimuth")
    val createdAt = datetime("created_at")
}
