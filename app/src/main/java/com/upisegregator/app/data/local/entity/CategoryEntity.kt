package com.upisegregator.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a spending category with metadata for UI styling.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["categoryName"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long
)
