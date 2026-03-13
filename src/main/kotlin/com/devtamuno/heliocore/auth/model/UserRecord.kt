package com.devtamuno.heliocore.auth.model

import java.time.LocalDateTime
import java.util.UUID

/** Internal representation of a stored user. */
data class UserRecord(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val createdAt: LocalDateTime
)
