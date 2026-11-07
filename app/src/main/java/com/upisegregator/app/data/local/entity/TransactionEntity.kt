package com.upisegregator.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core transaction entity storing all financial and metadata fields.
 *
 * Each record belongs to a specific [userId] (or "demo_user" for Demo Mode).
 * All monetary amounts are stored in [amountPaise] as Long integer paise.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = RecipientEntity::class,
            parentColumns = ["recipientId"],
            childColumns = ["recipientId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["recipientId"]),
        Index(value = ["transactionDate"]),
        Index(value = ["transactionStatus"]),
        Index(value = ["transactionSource"]),
        Index(value = ["fingerprint"]),
        Index(value = ["direction"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val transactionId: String,
    val amountPaise: Long,
    val recipientId: String? = null,
    val recipientName: String,
    val paymentMethod: PaymentMethod,
    val category: String,
    val note: String = "",
    val transactionDate: Long,
    val transactionStatus: TransactionStatus,
    val transactionSource: TransactionSource,
    val externalReference: String? = null,
    val fingerprint: String? = null,
    val userId: String = "demo_user",
    val createdAt: Long = System.currentTimeMillis(),
    val direction: TransactionDirection = TransactionDirection.DEBIT
)
