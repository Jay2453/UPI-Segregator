package com.upisegregator.app.data.importer.csv

import com.upisegregator.app.data.importer.deduplication.TransactionFingerprintGenerator
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.TransactionDirection
import java.io.InputStream
import java.util.Locale

/**
 * Result returned by [CsvStatementParser].
 */
data class CsvParseResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val headerMapping: HeaderMapping? = null,
    val transactions: List<ImportableTransaction> = emptyList(),
    val totalRowsInFile: Int = 0
)

/**
 * Modular parser for converting CSV bank statements into normalized, validated transaction candidates.
 */
object CsvStatementParser {

    private const val MAX_HEADER_SCAN_LINES = 30

    /**
     * Parses an input stream of CSV content for a specific [userId].
     */
    fun parse(inputStream: InputStream, userId: String): CsvParseResult {
        val rows = CsvTokenizer.parse(inputStream)
        if (rows.isEmpty()) {
            return CsvParseResult(
                isSuccess = false,
                errorMessage = "The selected file is empty."
            )
        }

        // 1. Locate the header row within the first MAX_HEADER_SCAN_LINES
        var headerRowIndex = -1
        var headerMapping: HeaderMapping? = null

        val scanLimit = minOf(rows.size, MAX_HEADER_SCAN_LINES)
        for (i in 0 until scanLimit) {
            val candidate = HeaderMapping.fromHeaders(rows[i])
            if (candidate != null) {
                headerRowIndex = i
                headerMapping = candidate
                break
            }
        }

        if (headerMapping == null || headerRowIndex == -1) {
            return CsvParseResult(
                isSuccess = false,
                errorMessage = "Could not identify standard bank statement column headers (Date, Description, and Debit/Credit amounts)."
            )
        }

        val dataRows = rows.subList(headerRowIndex + 1, rows.size)
        if (dataRows.isEmpty()) {
            return CsvParseResult(
                isSuccess = false,
                errorMessage = "No transaction data rows found after the header."
            )
        }

        val candidates = mutableListOf<ImportableTransaction>()
        val seenFileUtrs = mutableSetOf<String>()
        val seenFileFingerprints = mutableSetOf<String>()

        for ((index, row) in dataRows.withIndex()) {
            val rowIndex = headerRowIndex + 1 + index

            // Skip trailing summary rows (e.g. "Total Debit", "Closing Balance", etc.)
            if (isSummaryOrFooterRow(row, headerMapping)) continue

            val candidate = parseRow(
                row = row,
                rowIndex = rowIndex,
                mapping = headerMapping,
                userId = userId,
                seenUtrs = seenFileUtrs,
                seenFingerprints = seenFileFingerprints
            )

            if (candidate != null) {
                candidates.add(candidate)
            }
        }

        if (candidates.isEmpty()) {
            return CsvParseResult(
                isSuccess = false,
                errorMessage = "No valid transaction rows could be extracted from this statement."
            )
        }

        return CsvParseResult(
            isSuccess = true,
            headerMapping = headerMapping,
            transactions = candidates,
            totalRowsInFile = dataRows.size
        )
    }

    /**
     * Parses a raw CSV string for testing or direct content parsing.
     */
    fun parseString(csvContent: String, userId: String): CsvParseResult {
        return parse(csvContent.byteInputStream(Charsets.UTF_8), userId)
    }

    private fun parseRow(
        row: List<String>,
        rowIndex: Int,
        mapping: HeaderMapping,
        userId: String,
        seenUtrs: MutableSet<String>,
        seenFingerprints: MutableSet<String>
    ): ImportableTransaction? {
        val rawDate = row.getOrNull(mapping.dateIndex)?.trim() ?: ""
        val rawDesc = row.getOrNull(mapping.descriptionIndex)?.trim() ?: ""

        // If date and description are both blank, skip silently (empty/spacer line)
        if (rawDate.isBlank() && rawDesc.isBlank()) return null

        var validationStatus = RowValidationStatus.VALID
        var validationError: String? = null

        // 1. Parse Date
        val parsedDate = DateFormats.parseDate(rawDate)
        val dateMillis: Long
        val dateFormatted: String
        if (parsedDate != null) {
            dateMillis = parsedDate.first
            dateFormatted = parsedDate.second
        } else {
            dateMillis = System.currentTimeMillis()
            dateFormatted = rawDate.ifBlank { "Unknown Date" }
            validationStatus = RowValidationStatus.INVALID
            validationError = "Unrecognized date format: '$rawDate'"
        }

        // 2. Parse Description & Recipient
        val description = rawDesc.ifBlank { "Unspecified Transaction" }
        val rawRecipient = if (mapping.recipientIndex != -1) row.getOrNull(mapping.recipientIndex) else null
        val recipientName = RecipientExtractor.extractRecipient(description, rawRecipient)

        // 3. Parse Reference / UTR
        val extractedRefFromDesc = RecipientExtractor.extractReference(description)
        val columnRef = if (mapping.referenceIndex != -1) row.getOrNull(mapping.referenceIndex)?.trim() else null
        val externalReference = columnRef?.takeIf { it.isNotBlank() } ?: extractedRefFromDesc

        // 4. Parse Amount & Direction
        var direction = TransactionDirection.DEBIT
        var amountPaise = 0L

        if (mapping.hasSplitDebitCredit) {
            val debitPaise = AmountParser.parseToPaise(row.getOrNull(mapping.debitIndex))
            val creditPaise = AmountParser.parseToPaise(row.getOrNull(mapping.creditIndex))

            val hasDebit = debitPaise != null && debitPaise > 0L
            val hasCredit = creditPaise != null && creditPaise > 0L

            when {
                hasDebit && hasCredit -> {
                    // Conflicting row: both debit and credit columns contain positive amounts
                    validationStatus = RowValidationStatus.INVALID
                    validationError = "Row has conflicting debit and credit amounts"
                    amountPaise = debitPaise ?: 0L
                    direction = TransactionDirection.DEBIT
                }
                hasDebit -> {
                    direction = TransactionDirection.DEBIT
                    amountPaise = debitPaise!!
                }
                hasCredit -> {
                    direction = TransactionDirection.CREDIT
                    amountPaise = creditPaise!!
                }
                else -> {
                    validationStatus = RowValidationStatus.INVALID
                    validationError = "Row has no valid positive debit or credit amount"
                }
            }
        } else if (mapping.hasSingleAmount) {
            val rawAmtStr = row.getOrNull(mapping.amountIndex)?.trim() ?: ""
            val isExplicitDebit = rawAmtStr.startsWith("-") || rawAmtStr.contains("DR", true)
            val isExplicitCredit = rawAmtStr.startsWith("+") || rawAmtStr.contains("CR", true)
            val parsedPaise = AmountParser.parseToPaise(rawAmtStr)

            if (parsedPaise != null && parsedPaise > 0L) {
                amountPaise = parsedPaise
                val rawType = if (mapping.typeIndicatorIndex != -1) row.getOrNull(mapping.typeIndicatorIndex)?.trim() else null

                val lower = description.lowercase(Locale.US)
                val hasCreditKeyword = lower.contains("salary") || lower.contains("refund") ||
                    lower.contains("interest credited") || lower.contains("interest received") ||
                    lower.contains("dividend") || lower.contains("cashback") || lower.contains("reversal") ||
                    lower.contains("deposit") || lower.contains("inward")

                val hasDebitKeyword = lower.contains("withdrawal") || lower.contains("pos ") ||
                    lower.contains("debit") || lower.contains("bill payment") || lower.contains("paid to") ||
                    lower.contains("recharge") || lower.contains("fee") || lower.contains("charge") ||
                    lower.contains("tax") || lower.contains("purchase")

                when {
                    rawType != null && (rawType.equals("CR", true) || rawType.contains("CREDIT", true) || rawType.contains("DEP", true)) -> {
                        direction = TransactionDirection.CREDIT
                    }
                    rawType != null && (rawType.equals("DR", true) || rawType.contains("DEBIT", true) || rawType.contains("W/D", true)) -> {
                        direction = TransactionDirection.DEBIT
                    }
                    isExplicitCredit -> {
                        direction = TransactionDirection.CREDIT
                    }
                    isExplicitDebit -> {
                        direction = TransactionDirection.DEBIT
                    }
                    hasCreditKeyword && !hasDebitKeyword -> {
                        direction = TransactionDirection.CREDIT
                    }
                    hasDebitKeyword && !hasCreditKeyword -> {
                        direction = TransactionDirection.DEBIT
                    }
                    else -> {
                        // FIX D: When direction cannot be reliably determined, do NOT guess DEBIT!
                        // Flag row as NEEDS_REVIEW, clear automatic selection, provide clear explanation.
                        direction = TransactionDirection.DEBIT
                        validationStatus = RowValidationStatus.NEEDS_REVIEW
                        validationError = "Transaction direction (Debit/Credit) could not be determined automatically. Requires review."
                    }
                }
            } else {
                validationStatus = RowValidationStatus.INVALID
                validationError = "Invalid or zero amount"
            }
        }

        // 5. Categorization and Payment Method
        val category = TransactionClassifier.estimateCategory(recipientName, description)
        val paymentMethod = TransactionClassifier.estimatePaymentMethod(description)

        // 6. Deterministic Fingerprint
        val fingerprint = TransactionFingerprintGenerator.generateFingerprint(
            userId = userId,
            dateMillis = dateMillis,
            amountPaise = amountPaise,
            direction = direction,
            description = description,
            recipientName = recipientName,
            externalReference = externalReference
        )

        // 7. Check In-File Duplicates
        var duplicateStatus = DuplicateStatus.NONE
        var duplicateReason: String? = null

        val hasDuplicateUtr = !externalReference.isNullOrBlank() && seenUtrs.contains(externalReference)
        val hasDuplicateFingerprint = seenFingerprints.contains(fingerprint)

        if (hasDuplicateUtr || hasDuplicateFingerprint) {
            duplicateStatus = DuplicateStatus.DUPLICATE_IN_FILE
            duplicateReason = if (hasDuplicateUtr) {
                "Duplicate UTR ($externalReference) within this statement"
            } else {
                "Identical duplicate transaction within this statement"
            }
        } else {
            if (!externalReference.isNullOrBlank()) {
                seenUtrs.add(externalReference)
            }
            seenFingerprints.add(fingerprint)
        }

        // 8. Selection default:
        // Valid DEBITs that are not duplicates are selected by default.
        // Credits are deselected by default (to avoid skewing expense totals unless user explicitly chooses).
        val isSelected = validationStatus == RowValidationStatus.VALID &&
                duplicateStatus == DuplicateStatus.NONE &&
                direction == TransactionDirection.DEBIT

        return ImportableTransaction(
            rawRowIndex = rowIndex,
            dateMillis = dateMillis,
            dateFormatted = dateFormatted,
            description = description,
            recipientName = recipientName,
            amountPaise = amountPaise,
            direction = direction,
            paymentMethod = paymentMethod,
            category = category,
            externalReference = externalReference,
            fingerprint = fingerprint,
            duplicateStatus = duplicateStatus,
            duplicateReason = duplicateReason,
            validationStatus = validationStatus,
            validationError = validationError,
            isSelected = isSelected
        )
    }

    private fun isSummaryOrFooterRow(row: List<String>, mapping: HeaderMapping): Boolean {
        val firstCell = row.firstOrNull()?.trim()?.lowercase(Locale.US) ?: ""
        val descCell = row.getOrNull(mapping.descriptionIndex)?.trim()?.lowercase(Locale.US) ?: ""
        val combined = "$firstCell $descCell"

        return combined.startsWith("total") ||
                combined.startsWith("closing bal") ||
                combined.startsWith("opening bal") ||
                combined.startsWith("statement generated") ||
                combined.contains("end of statement")
    }
}
