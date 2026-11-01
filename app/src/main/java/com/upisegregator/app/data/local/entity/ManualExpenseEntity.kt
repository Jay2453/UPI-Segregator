package com.upisegregator.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a manually recorded expense (cash, offline, or non-UPI spending).
 *
 * Stored with integer paise precision [amountPaise] to avoid floating-point inaccuracies.
 * Kept strictly isolated by [userId] (or "demo_user" for Demo Mode).
 *
 * [expenseDate] represents the date the expense actually occurred (user-selectable).
 * [createdAt] records the physical creation timestamp.
 */
@Entity(
    tableName = "manual_expenses",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["expenseDate"])
    ]
)
data class ManualExpenseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val amountPaise: Long,
    val recipientName: String,
    val category: String,
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val expenseDate: Long,
    val userId: String = "demo_user",
    val createdAt: Long = System.currentTimeMillis()
)
