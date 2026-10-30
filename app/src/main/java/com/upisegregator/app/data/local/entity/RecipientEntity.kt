package com.upisegregator.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a payment recipient (person, merchant, or contact) owned by a user or demo profile.
 */
@Entity(
    tableName = "recipients",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["recipientName"]),
        Index(value = ["upiId"])
    ]
)
data class RecipientEntity(
    @PrimaryKey
    val recipientId: String,
    val recipientName: String,
    val upiId: String? = null,
    val phoneNumber: String? = null,
    val userId: String = "demo_user",
    val createdAt: Long = System.currentTimeMillis()
)
