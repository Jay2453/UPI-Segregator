package com.upisegregator.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a local-only user profile stored on this device.
 *
 * Passwords are never stored as plaintext; only the salted PBKDF2 hash and salt are stored.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long = System.currentTimeMillis()
)
