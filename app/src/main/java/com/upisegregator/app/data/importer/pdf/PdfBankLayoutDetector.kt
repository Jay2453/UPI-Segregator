package com.upisegregator.app.data.importer.pdf

import java.util.Locale

/**
 * Semantic column types identified in bank statement PDF tables.
 */
enum class PdfColumnType {
    DATE,
    DESCRIPTION,
    REFERENCE,
    DEBIT,
    CREDIT,
    AMOUNT,
    TYPE_INDICATOR, // CR / DR
    BALANCE
}

/**
 * A resolved column boundary on a PDF page.
 *
 * @param type       The recognized semantic type of column.
 * @param headerText The raw text matched from the header.
 * @param startX     Approximate X coordinate where the column starts.
 * @param endX       Approximate X coordinate where the column ends (or where next column starts).
 */
data class PdfColumnDefinition(
    val type: PdfColumnType,
    val headerText: String,
    val startX: Float,
    val endX: Float
)

/**
 * Represents the detected table structure and bank identity in a PDF document.
 */
data class PdfLayoutMapping(
    val detectedBank: String?,
    val columns: List<PdfColumnDefinition>,
    val headerY: Float,
    val headerPageNum: Int
) {
    val dateColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.DATE }
    val descriptionColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.DESCRIPTION }
    val debitColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.DEBIT }
    val creditColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.CREDIT }
    val amountColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.AMOUNT }
    val typeIndicatorColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.TYPE_INDICATOR }
    val referenceColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.REFERENCE }
    val balanceColumn: PdfColumnDefinition? get() = columns.find { it.type == PdfColumnType.BALANCE }

    val hasSplitDebitCredit: Boolean get() = debitColumn != null && creditColumn != null
    val hasSingleAmount: Boolean get() = amountColumn != null

    val isValid: Boolean get() = dateColumn != null && descriptionColumn != null && (hasSplitDebitCredit || hasSingleAmount)
}

/**
 * Detects bank layouts and column boundaries from extracted text lines.
 */
object PdfBankLayoutDetector {

    private val BANK_SIGNATURES = mapOf(
        "HDFC Bank" to listOf("hdfc bank", "hdfc", "housing development finance corporation"),
        "State Bank of India" to listOf("state bank of india", "sbi", "statebank"),
        "ICICI Bank" to listOf("icici bank", "icici"),
        "Axis Bank" to listOf("axis bank", "uti bank"),
        "Kotak Mahindra Bank" to listOf("kotak mahindra", "kotak bank", "kotak"),
        "Punjab National Bank" to listOf("punjab national bank", "pnb"),
        "Bank of Baroda" to listOf("bank of baroda", "bob"),
        "Canara Bank" to listOf("canara bank"),
        "Union Bank of India" to listOf("union bank of india", "union bank"),
        "IndusInd Bank" to listOf("indusind bank", "indusind"),
        "Yes Bank" to listOf("yes bank"),
        "IDFC FIRST Bank" to listOf("idfc first", "idfc bank")
    )

    private val DATE_HEADER_PATTERNS = listOf(
        "date", "txndate", "txn date", "tran date", "transaction date", "value date", "post date", "entry date"
    )

    private val DESC_HEADER_PATTERNS = listOf(
        "narration", "particulars", "description", "remarks", "transaction remarks",
        "transaction details", "details", "narrative", "memo"
    )

    private val DEBIT_HEADER_PATTERNS = listOf(
        "debit", "withdrawal", "dr", "dr.", "withdrawal amt", "withdrawal amount", "dr amount", "withdrawals"
    )

    private val CREDIT_HEADER_PATTERNS = listOf(
        "credit", "deposit", "cr", "cr.", "deposit amt", "deposit amount", "cr amount", "deposits"
    )

    private val AMOUNT_HEADER_PATTERNS = listOf(
        "amount", "txn amount", "transaction amount", "amt", "net amount", "trans amount"
    )

    private val TYPE_HEADER_PATTERNS = listOf(
        "type", "cr/dr", "dr/cr", "cr / dr", "dr / cr", "indicator", "txn type", "d/c"
    )

    private val REF_HEADER_PATTERNS = listOf(
        "chq/ref no", "chq / ref no", "chq/ref", "ref no", "reference no", "reference",
        "utr", "txn id", "cheque no", "chq no", "ref/cheque no", "rrn"
    )

    private val BALANCE_HEADER_PATTERNS = listOf(
        "balance", "closing balance", "avail balance", "available balance", "bal"
    )

    /**
     * Inspects text across pages to detect the bank name.
     */
    fun detectBank(pages: List<PageTextResult>): String? {
        val sampleText = pages.take(2).flatMap { it.lines }
            .take(40)
            .joinToString(" ") { it.fullText }
            .lowercase(Locale.US)

        for ((bankName, patterns) in BANK_SIGNATURES) {
            if (patterns.any { sampleText.contains(it) }) {
                return bankName
            }
        }
        return null
    }

    /**
     * Scans pages for a table header line and constructs a [PdfLayoutMapping].
     */
    fun detectLayout(pages: List<PageTextResult>): PdfLayoutMapping? {
        val detectedBank = detectBank(pages)

        for (page in pages) {
            for (line in page.lines) {
                val mapping = tryParseHeaderLine(line, page.pageNum, detectedBank)
                if (mapping != null && mapping.isValid) {
                    return mapping
                }
            }
        }
        return null
    }

    /**
     * Checks if a line matches repeated table headers (e.g., across subsequent pages).
     */
    fun isHeaderLine(line: TextLine, mapping: PdfLayoutMapping): Boolean {
        val lower = line.fullText.lowercase(Locale.US)
        val hasDate = DATE_HEADER_PATTERNS.any { lower.contains(it) }
        val hasDesc = DESC_HEADER_PATTERNS.any { lower.contains(it) }
        val hasAmountOrDebit = (DEBIT_HEADER_PATTERNS + AMOUNT_HEADER_PATTERNS).any { lower.contains(it) }
        return hasDate && hasDesc && hasAmountOrDebit
    }

    private fun tryParseHeaderLine(line: TextLine, pageNum: Int, detectedBank: String?): PdfLayoutMapping? {
        val tokens = line.tokens
        if (tokens.size < 3) return null

        val lowerTokens = tokens.map { it.text.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9/\\s]"), "") }
        val lineText = lowerTokens.joinToString(" ")

        // Line must contain at least date, description, and an amount/debit/credit indicator
        val hasDate = DATE_HEADER_PATTERNS.any { lineText.contains(it) }
        val hasDesc = DESC_HEADER_PATTERNS.any { lineText.contains(it) }
        val hasDebitCreditOrAmount = DEBIT_HEADER_PATTERNS.any { lineText.contains(it) } ||
                AMOUNT_HEADER_PATTERNS.any { lineText.contains(it) }

        if (!hasDate || !hasDesc || !hasDebitCreditOrAmount) return null

        val recognizedColumns = mutableListOf<PdfColumnDefinition>()

        for (i in tokens.indices) {
            val token = tokens[i]
            val text = lowerTokens[i]
            val nextX = if (i + 1 < tokens.size) tokens[i + 1].x else token.x + 100f

            val type = classifyToken(text)
            if (type != null) {
                // Avoid duplicate column definitions for the same category
                if (recognizedColumns.none { it.type == type }) {
                    recognizedColumns.add(
                        PdfColumnDefinition(
                            type = type,
                            headerText = token.text,
                            startX = token.x,
                            endX = nextX
                        )
                    )
                }
            }
        }

        // Adjust column endX to be the startX of the next column
        val sortedColumns = recognizedColumns.sortedBy { it.startX }
        val adjustedColumns = sortedColumns.mapIndexed { idx, col ->
            val endX = if (idx + 1 < sortedColumns.size) {
                sortedColumns[idx + 1].startX
            } else {
                col.startX + 200f
            }
            col.copy(endX = endX)
        }

        val mapping = PdfLayoutMapping(
            detectedBank = detectedBank,
            columns = adjustedColumns,
            headerY = line.y,
            headerPageNum = pageNum
        )

        return if (mapping.isValid) mapping else null
    }

    private fun classifyToken(text: String): PdfColumnType? {
        return when {
            DATE_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.DATE
            DESC_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.DESCRIPTION
            DEBIT_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.DEBIT
            CREDIT_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.CREDIT
            AMOUNT_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.AMOUNT
            TYPE_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.TYPE_INDICATOR
            REF_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.REFERENCE
            BALANCE_HEADER_PATTERNS.any { text == it || text.contains(it) } -> PdfColumnType.BALANCE
            else -> null
        }
    }
}
