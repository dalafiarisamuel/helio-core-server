package com.devtamuno.heliocore.auth.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

/** Exposed table definition for application users. */
object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = datetime("created_at")
}
