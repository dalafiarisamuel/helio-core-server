package com.devtamuno.heliocore.auth

import com.devtamuno.heliocore.auth.model.UserRecord
import com.devtamuno.heliocore.auth.tables.RefreshTokens
import com.devtamuno.heliocore.auth.tables.Users
import com.devtamuno.heliocore.domain.ValidationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.UUID

class UserRepository(private val db: Database) {

    suspend fun ensureSchema() {
        newSuspendedTransaction(Dispatchers.IO, db) {
            SchemaUtils.createMissingTablesAndColumns(Users, RefreshTokens)
        }
    }

    suspend fun createUser(email: String, passwordHash: String): UserRecord {
        val normalizedEmail = email.trim().lowercase()
        val existing = findByEmail(normalizedEmail)
        if (existing != null) throw ValidationException("Email already registered")

        return newSuspendedTransaction(Dispatchers.IO, db) {
            val id = UUID.randomUUID()
            val now = LocalDateTime.now()
            Users.insert {
                it[Users.id] = id
                it[Users.email] = normalizedEmail
                it[Users.passwordHash] = passwordHash
                it[Users.createdAt] = now
            }
            UserRecord(id, normalizedEmail, passwordHash, now)
        }
    }

    suspend fun findByEmail(email: String): UserRecord? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.selectAll().where { Users.email eq email.trim().lowercase() }
                .limit(1)
                .firstOrNull()
                ?.toUserRecord()
        }

    suspend fun findById(id: UUID): UserRecord? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            Users.selectAll().where { Users.id eq id }
                .limit(1)
                .firstOrNull()
                ?.toUserRecord()
        }

    suspend fun saveRefreshToken(token: String, userId: UUID, expiresAt: LocalDateTime) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            RefreshTokens.insert {
                it[RefreshTokens.token] = token
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.expiresAt] = expiresAt
            }
        }
    }

    suspend fun findRefreshToken(token: String): Pair<String, UUID>? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            RefreshTokens.selectAll()
                .where { RefreshTokens.token eq token and (RefreshTokens.expiresAt greater LocalDateTime.now()) }
                .map { it[RefreshTokens.token] to it[RefreshTokens.userId] }
                .firstOrNull()
        }

    suspend fun deleteRefreshToken(token: String) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            RefreshTokens.deleteWhere { RefreshTokens.token eq token }
        }
    }

    suspend fun deleteUserRefreshTokens(userId: UUID) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        }
    }

    private fun ResultRow.toUserRecord() = UserRecord(
        id = this[Users.id].value,
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        createdAt = this[Users.createdAt]
    )
}
