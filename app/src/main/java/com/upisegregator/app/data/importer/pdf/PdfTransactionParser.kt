package com.upisegregator.app.data.importer.pdf

import com.upisegregator.app.data.importer.csv.AmountParser
import com.upisegregator.app.data.importer.csv.DateFormats
import com.upisegregator.app.data.importer.csv.RecipientExtractor
import com.upisegregator.app.data.importer.csv.TransactionClassifier
import com.upisegregator.app.data.importer.deduplication.TransactionFingerprintGenerator
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.local.entity.TransactionDirection
import java.util.Locale

/**
 * Parses extracted positional PDF text lines into normalized [ImportableTransaction] objects.
 *
 * Reuses the existing [AmountParser], [DateFormats], [RecipientExtractor],
 * [TransactionClassifier], and [TransactionFingerprintGenerator].
 *
 * Handles:
 * - Table column alignment via [PdfLayoutMapping].
 * - Repeated page headers and footer/summary lines.
 * - Multi-line / wrapped transaction descriptions.
 * - Split Debit/Credit columns as well as single Amount + CR/DR indicator columns.
 * - In-file duplicate detection.
 * - Flagging ambiguous or incomplete rows as [RowValidationStatus.NEEDS_REVIEW].
 */
object PdfTransactionParser {

    private val FOOTER_PATTERNS = listOf(
        "page ", "closing balance", "opening balance", "statement summary",
        "total withdrawal", "total deposit", "registered office", "computer generated",
        "end of statement", "this is a system generated", "nomination registered"
    )

    private val UTR_REGEX = Regex("(?i)(?:UTR|RRN|REF|TXN(?:ID)?|UPI)[:\\s/]+([0-9]{12}|[0-9A-Z]{9,16})")

    /**
     * Interim mutable representation of a transaction row being reconstructed across wrapped lines.
     */
    private data class IntermediateRow(
        val rawRowIndex: Int,
        var rawDate: String = "",
        var rawDescription: String = "",
        var rawReference: String? = null,
        var rawDebit: String? = null,
        var rawCredit: String? = null,
        var rawAmount: String? = null,
        var rawIndicator: String? = null,
        var rawBalance: String? = null,
        var isUncertainDirection: Boolean = false,
        var parsingError: String? = null
    )

    fun parseTransactions(
        pages: List<PageTextResult>,
        mapping: PdfLayoutMapping,
        userId: String
    ): List<ImportableTransaction> {
        val intermediateRows = mutableListOf<IntermediateRow>()
        var currentRow: IntermediateRow? = null
        var rowIndexCounter = 1

        for (page in pages) {
            val lines = page.lines.sortedBy { it.y }

            for (line in lines) {
                // 1. Skip table header line on header page and repeated headers on subsequent pages
                if (page.pageNum == mapping.headerPageNum && line.y <= mapping.headerY + 2f) {
                    continue
                }
                if (PdfBankLayoutDetector.isHeaderLine(line, mapping)) {
                    continue
                }

                val lineLower = line.fullText.lowercase(Locale.US)
                if (FOOTER_PATTERNS.any { lineLower.contains(it) && !containsValidDate(line, mapping) }) {
                    continue
                }

                // 2. Map tokens of this line to recognized table columns
                val colMap = assignTokensToColumns(line, mapping)
                val dateText = colMap[PdfColumnType.DATE]?.trim().orEmpty()
                val descText = colMap[PdfColumnType.DESCRIPTION]?.trim().orEmpty()
                val debitText = colMap[PdfColumnType.DEBIT]?.trim()
                val creditText = colMap[PdfColumnType.CREDIT]?.trim()
                val amountText = colMap[PdfColumnType.AMOUNT]?.trim()
                val indText = colMap[PdfColumnType.TYPE_INDICATOR]?.trim()
                val refText = colMap[PdfColumnType.REFERENCE]?.trim()
                val balText = colMap[PdfColumnType.BALANCE]?.trim()

                val hasDate = dateText.isNotBlank() && DateFormats.parseDate(dateText) != null
                val hasFinancialValue = !debitText.isNullOrBlank() || !creditText.isNullOrBlank() || !amountText.isNullOrBlank()

                if (hasDate) {
                    // New transaction row begins
                    currentRow?.let { intermediateRows.add(it) }
                    currentRow = IntermediateRow(
                        rawRowIndex = rowIndexCounter++,
                        rawDate = dateText,
                        rawDescription = descText,
                        rawReference = refText?.takeIf { it.isNotBlank() },
                        rawDebit = debitText?.takeIf { it.isNotBlank() },
                        rawCredit = creditText?.takeIf { it.isNotBlank() },
                        rawAmount = amountText?.takeIf { it.isNotBlank() },
                        rawIndicator = indText?.takeIf { it.isNotBlank() },
                        rawBalance = balText?.takeIf { it.isNotBlank() }
                    )
                } else if (currentRow != null) {
                    // Wrapped continuation line for previous transaction description or reference
                    if (descText.isNotBlank()) {
                        currentRow.rawDescription = if (currentRow.rawDescription.isBlank()) {
                            descText
                        } else {
                            "${currentRow.rawDescription} $descText"
                        }
                    }
                    if (currentRow.rawReference.isNullOrBlank() && !refText.isNullOrBlank()) {
                        currentRow.rawReference = refText
                    }
                    if (currentRow.rawDebit.isNullOrBlank() && !debitText.isNullOrBlank()) {
                        currentRow.rawDebit = debitText
                    }
                    if (currentRow.rawCredit.isNullOrBlank() && !creditText.isNullOrBlank()) {
                        currentRow.rawCredit = creditText
                    }
                    if (currentRow.rawAmount.isNullOrBlank() && !amountText.isNullOrBlank()) {
                        currentRow.rawAmount = amountText
                    }
                    if (currentRow.rawIndicator.isNullOrBlank() && !indText.isNullOrBlank()) {
                        currentRow.rawIndicator = indText
                    }
                } else if (hasFinancialValue && descText.isNotBlank()) {
                    // Row with amounts but missing or unparseable date - keep for review
                    currentRow?.let { intermediateRows.add(it) }
                    currentRow = IntermediateRow(
                        rawRowIndex = rowIndexCounter++,
                        rawDate = dateText,
                        rawDescription = descText,
                        rawReference = refText?.takeIf { it.isNotBlank() },
                        rawDebit = debitText?.takeIf { it.isNotBlank() },
                        rawCredit = creditText?.takeIf { it.isNotBlank() },
                        rawAmount = amountText?.takeIf { it.isNotBlank() },
                        rawIndicator = indText?.takeIf { it.isNotBlank() },
                        rawBalance = balText?.takeIf { it.isNotBlank() },
                        parsingError = "Missing or unparseable date"
                    )
                }
            }
        }

        currentRow?.let { intermediateRows.add(it) }

        // 3. Normalize into ImportableTransaction and check duplicate status
        val seenFingerprints = mutableSetOf<String>()
        val result = mutableListOf<ImportableTransaction>()

        for (row in intermediateRows) {
            val tx = normalizeRow(row, mapping, userId)
            if (tx.validationStatus == RowValidationStatus.VALID && tx.fingerprint.isNotEmpty()) {
                if (seenFingerprints.contains(tx.fingerprint)) {
                    result.add(
                        tx.copy(
                            duplicateStatus = DuplicateStatus.DUPLICATE_IN_FILE,
                            duplicateReason = "Duplicate row within the same statement file",
                            isSelected = false
                        )
                    )
                } else {
                    seenFingerprints.add(tx.fingerprint)
                    result.add(tx)
                }
            } else {
                result.add(tx)
            }
        }

        return result
    }

    private fun containsValidDate(line: TextLine, mapping: PdfLayoutMapping): Boolean {
        val colMap = assignTokensToColumns(line, mapping)
        val dateText = colMap[PdfColumnType.DATE]?.trim().orEmpty()
        return dateText.isNotBlank() && DateFormats.parseDate(dateText) != null
    }

    private fun assignTokensToColumns(line: TextLine, mapping: PdfLayoutMapping): Map<PdfColumnType, String> {
        val cols = mapping.columns.sortedBy { it.startX }
        val map = mutableMapOf<PdfColumnType, StringBuilder>()

        for (token in line.tokens) {
            val matchedCol = findColumnForToken(token.x, cols)
            if (matchedCol != null) {
                val sb = map.getOrPut(matchedCol.type) { StringBuilder() }
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(token.text)
            }
        }

        return map.mapValues { it.value.toString().trim() }
    }

    private fun findColumnForToken(x: Float, sortedCols: List<PdfColumnDefinition>): PdfColumnDefinition? {
        if (sortedCols.isEmpty()) return null

        // Find best column interval
        for (i in sortedCols.indices) {
            val col = sortedCols[i]
            val nextColStart = if (i + 1 < sortedCols.size) sortedCols[i + 1].startX else Float.MAX_VALUE
            // Allow 15 pt tolerance left of column start
            if (x >= col.startX - 15f && x < nextColStart - 5f) {
                return col
            }
        }

        // Fallback: closest column
        return sortedCols.minByOrNull { kotlin.math.abs(it.startX - x) }
    }

    private fun normalizeRow(
        row: IntermediateRow,
        mapping: PdfLayoutMapping,
        userId: String
    ): ImportableTransaction {
        val datePair = DateFormats.parseDate(row.rawDate)
        val dateMillis = datePair?.first ?: 0L
        val dateFormatted = datePair?.second ?: row.rawDate.ifBlank { "Unknown Date" }

        val description = row.rawDescription.trim().ifBlank { "Bank Transaction" }
        val recipient = RecipientExtractor.extractRecipient(description)
        val category = TransactionClassifier.estimateCategory(recipient, description)
        val paymentMethod = TransactionClassifier.estimatePaymentMethod(description)

        // Resolve reference/UTR
        val resolvedRef = row.rawReference?.takeIf { it.isNotBlank() } ?: extractUtrFromDescription(description)

        // Resolve amount and direction
        val (amountPaise, direction, isDirectionUncertain) = resolveAmountAndDirection(row, mapping)

        var validationStatus = RowValidationStatus.VALID
        var validationError: String? = null

        if (datePair == null) {
            validationStatus = RowValidationStatus.NEEDS_REVIEW
            validationError = row.parsingError ?: "Could not parse transaction date: '${row.rawDate}'"
        } else if (amountPaise == null || amountPaise <= 0L) {
            validationStatus = RowValidationStatus.INVALID
            validationError = "Invalid or zero transaction amount"
        } else if (isDirectionUncertain) {
            validationStatus = RowValidationStatus.NEEDS_REVIEW
            validationError = "Transaction direction (Debit/Credit) is ambiguous"
        }

        val finalAmountPaise = amountPaise ?: 0L
        val finalDirection = direction ?: TransactionDirection.DEBIT

        val fingerprint = if (validationStatus == RowValidationStatus.VALID || validationStatus == RowValidationStatus.NEEDS_REVIEW) {
            TransactionFingerprintGenerator.generateFingerprint(
                userId = userId,
                dateMillis = dateMillis,
                amountPaise = finalAmountPaise,
                direction = finalDirection,
                description = description,
                recipientName = recipient,
                externalReference = resolvedRef
            )
        } else {
            ""
        }

        return ImportableTransaction(
            rawRowIndex = row.rawRowIndex,
            dateMillis = dateMillis,
            dateFormatted = dateFormatted,
            description = description,
            recipientName = recipient,
            amountPaise = finalAmountPaise,
            direction = finalDirection,
            paymentMethod = paymentMethod,
            category = category,
            externalReference = resolvedRef,
            fingerprint = fingerprint,
            duplicateStatus = DuplicateStatus.NONE,
            duplicateReason = null,
            validationStatus = validationStatus,
            validationError = validationError,
            isSelected = validationStatus == RowValidationStatus.VALID
        )
    }

    private fun resolveAmountAndDirection(
        row: IntermediateRow,
        mapping: PdfLayoutMapping
    ): Triple<Long?, TransactionDirection?, Boolean> {
        val debitPaise = AmountParser.parseToPaise(row.rawDebit)
        val creditPaise = AmountParser.parseToPaise(row.rawCredit)

        if (mapping.hasSplitDebitCredit) {
            if (debitPaise != null && debitPaise > 0L && (creditPaise == null || creditPaise == 0L)) {
                return Triple(debitPaise, TransactionDirection.DEBIT, false)
            }
            if (creditPaise != null && creditPaise > 0L && (debitPaise == null || debitPaise == 0L)) {
                return Triple(creditPaise, TransactionDirection.CREDIT, false)
            }
        }

        // Single amount column or fallback
        val amountCandidate = AmountParser.parseToPaise(row.rawAmount)
            ?: debitPaise
            ?: creditPaise

        if (amountCandidate != null && amountCandidate > 0L) {
            val ind = (row.rawIndicator.orEmpty() + " " + row.rawDescription).uppercase(Locale.US)
            val isDebit = ind.contains(" DR") || ind.contains("/DR") || ind.contains("DEBIT") || ind.contains("WITHDRAWAL")
            val isCredit = ind.contains(" CR") || ind.contains("/CR") || ind.contains("CREDIT") || ind.contains("DEPOSIT")

            return when {
                isDebit && !isCredit -> Triple(amountCandidate, TransactionDirection.DEBIT, false)
                isCredit && !isDebit -> Triple(amountCandidate, TransactionDirection.CREDIT, false)
                else -> Triple(amountCandidate, TransactionDirection.DEBIT, true) // Ambiguous direction -> NEEDS_REVIEW
            }
        }

        return Triple(null, null, false)
    }

    private fun extractUtrFromDescription(description: String): String? {
        val match = UTR_REGEX.find(description)
        return match?.groupValues?.get(1)?.trim()
    }
}
