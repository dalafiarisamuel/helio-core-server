package com.devtamuno.heliocore.features.auth.data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object RefreshTokens : Table("refresh_tokens") {
    val token = varchar("token", 255)
    val userId = uuid("user_id").references(Users.id)
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(token)
}
