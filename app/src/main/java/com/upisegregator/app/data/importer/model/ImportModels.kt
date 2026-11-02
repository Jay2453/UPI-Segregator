package com.upisegregator.app.data.importer.model

import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import java.util.UUID

/**
 * Direction of financial movement in bank statement rows.
 */
enum class TransactionDirection {
    DEBIT,
    CREDIT
}

/**
 * Duplicate status determined by the deduplication engine.
 */
enum class DuplicateStatus {
    NONE,
    EXACT_MATCH_DATABASE,
    DUPLICATE_IN_FILE
}

/**
 * Row validity status.
 */
enum class RowValidationStatus {
    VALID,
    INVALID,
    NEEDS_REVIEW
}

/**
 * Represents an individual transaction parsed from a bank statement,
 * normalized and ready for preview, review, and selection.
 */
data class ImportableTransaction(
    val id: String = UUID.randomUUID().toString(),
    val rawRowIndex: Int,
    val dateMillis: Long,
    val dateFormatted: String,
    val description: String,
    val recipientName: String,
    val amountPaise: Long,
    val direction: TransactionDirection,
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val category: String = "Other",
    val externalReference: String? = null,
    val fingerprint: String,
    val duplicateStatus: DuplicateStatus = DuplicateStatus.NONE,
    val duplicateReason: String? = null,
    val validationStatus: RowValidationStatus = RowValidationStatus.VALID,
    val validationError: String? = null,
    val isSelected: Boolean = true
) {
    /**
     * Converts this normalized candidate into a persisted [TransactionEntity].
     *
     * Note: In accordance with core financial invariants:
     * - Only confirmed SUCCESS transactions count toward spending.
     * - Only DEBITs represent expenses.
     */
    fun toEntity(userId: String): TransactionEntity {
        return TransactionEntity(
            transactionId = "csv_${UUID.randomUUID()}",
            amountPaise = amountPaise,
            recipientId = null,
            recipientName = recipientName,
            paymentMethod = paymentMethod,
            category = category,
            note = if (direction == TransactionDirection.CREDIT) "[CREDIT] $description" else description,
            transactionDate = dateMillis,
            transactionStatus = if (direction == TransactionDirection.DEBIT) TransactionStatus.SUCCESS else TransactionStatus.PENDING,
            transactionSource = TransactionSource.CSV_IMPORT,
            externalReference = externalReference?.takeIf { it.isNotBlank() },
            fingerprint = fingerprint,
            userId = userId,
            createdAt = System.currentTimeMillis()
        )
    }
}

/**
 * Summary result produced after statement parsing & import execution.
 */
data class StatementImportResult(
    val fileName: String,
    val totalRowsDetected: Int,
    val successfullyImported: Int,
    val duplicatesSkipped: Int,
    val invalidSkipped: Int,
    val reviewCount: Int,
    val totalDebitPaise: Long,
    val totalCreditPaise: Long
)
