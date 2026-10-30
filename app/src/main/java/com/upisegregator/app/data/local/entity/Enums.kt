package com.upisegregator.app.data.local.entity

/**
 * Payment method used for a transaction.
 */
enum class PaymentMethod {
    UPI,
    CASH,
    CARD,
    BANK_TRANSFER,
    OTHER
}

/**
 * Transaction lifecycle status.
 *
 * CRITICAL RULE:
 * - A payment initiated through UPI Intent is never automatically SUCCESS.
 * - Only confirmed SUCCESS transactions count towards spending totals in V1.
 * - Never present user-reported outcomes as bank-verified.
 */
enum class TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    UNKNOWN
}

/**
 * Origin source of the transaction record.
 */
enum class TransactionSource {
    MANUAL,
    UPI_INTENT,
    CSV_IMPORT,
    PDF_IMPORT,
    DEMO
}
