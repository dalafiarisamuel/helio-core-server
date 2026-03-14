package com.devtamuno.heliocore.features.auth.domain

import java.time.LocalDateTime
import java.util.UUID

/** Internal representation of a stored user. */
data class UserRecord(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val passwordHash: String,
    val createdAt: LocalDateTime
)
