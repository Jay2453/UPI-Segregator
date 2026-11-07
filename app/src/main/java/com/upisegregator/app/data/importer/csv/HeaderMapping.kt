package com.upisegregator.app.data.importer.csv

import java.util.Locale

/**
 * Encapsulates identified column positions in a bank statement CSV.
 */
data class HeaderMapping(
    val dateIndex: Int,
    val descriptionIndex: Int,
    val debitIndex: Int = -1,
    val creditIndex: Int = -1,
    val amountIndex: Int = -1,
    val typeIndicatorIndex: Int = -1,
    val referenceIndex: Int = -1,
    val recipientIndex: Int = -1,
    val balanceIndex: Int = -1
) {
    val hasSplitDebitCredit: Boolean get() = debitIndex != -1 && creditIndex != -1
    val hasSingleAmount: Boolean get() = amountIndex != -1
    val isValid: Boolean get() = dateIndex != -1 && descriptionIndex != -1 && (hasSplitDebitCredit || hasSingleAmount)

    companion object {

        private val DATE_HEADERS = listOf(
            "date", "txndate", "txn date", "tran date", "transaction date", "value date",
            "posting date", "trans date", "booking date", "entry date"
        )

        private val DESCRIPTION_HEADERS = listOf(
            "narration", "particulars", "description", "remarks", "transaction remarks",
            "transaction details", "details", "narrative", "transaction description", "memo"
        )

        private val DEBIT_HEADERS = listOf(
            "debit", "withdrawal", "dr", "dr.", "withdrawal amount", "debit amount",
            "dr amount", "withdrawals", "withdrawal (inr)", "debit (inr)", "withdrawal amt", "dr."
        )

        private val CREDIT_HEADERS = listOf(
            "credit", "deposit", "cr", "cr.", "deposit amount", "credit amount",
            "cr amount", "deposits", "deposit (inr)", "credit (inr)", "deposit amt"
        )

        private val AMOUNT_HEADERS = listOf(
            "amount", "txn amount", "transaction amount", "amount (inr)", "amt",
            "net amount", "total amount", "trans amount"
        )

        private val TYPE_INDICATOR_HEADERS = listOf(
            "type", "cr/dr", "dr/cr", "cr / dr", "dr / cr", "indicator", "txn type",
            "transaction type", "credit/debit", "debit/credit", "d/c"
        )

        private val REFERENCE_HEADERS = listOf(
            "chq/ref no", "chq / ref no", "chq/ref no.", "cheque/ref no", "ref no",
            "ref no.", "reference no", "reference number", "reference", "utr",
            "utr no", "utr number", "transaction id", "txn id", "ref/cheque no",
            "rrn", "cheque number", "cheque no", "chq no"
        )

        private val RECIPIENT_HEADERS = listOf(
            "beneficiary", "payee", "merchant", "recipient", "party name",
            "counterparty", "to / from", "to/from", "beneficiary name", "payee name"
        )

        private val BALANCE_HEADERS = listOf(
            "balance", "closing balance", "avail balance", "available balance", "bal"
        )

        /**
         * Examines a list of column headers and matches them to semantic financial columns.
         */
        fun fromHeaders(headers: List<String>): HeaderMapping? {
            val normalizedHeaders = headers.map { normalizeHeader(it) }

            var dateIdx = -1
            var descIdx = -1
            var debitIdx = -1
            var creditIdx = -1
            var amountIdx = -1
            var typeIdx = -1
            var refIdx = -1
            var recipIdx = -1
            var balanceIdx = -1

            for ((i, header) in normalizedHeaders.withIndex()) {
                when {
                    dateIdx == -1 && DATE_HEADERS.any { header == it || header.startsWith("$it ") || header.endsWith(" $it") } -> dateIdx = i
                    descIdx == -1 && DESCRIPTION_HEADERS.any { header == it || header.contains(it) } -> descIdx = i
                    debitIdx == -1 && DEBIT_HEADERS.any { header == it || header.contains("debit") || header.contains("withdrawal") } -> debitIdx = i
                    creditIdx == -1 && CREDIT_HEADERS.any { header == it || header.contains("credit") || header.contains("deposit") } -> creditIdx = i
                    amountIdx == -1 && AMOUNT_HEADERS.any { header == it || header == "amount" } -> amountIdx = i
                    typeIdx == -1 && TYPE_INDICATOR_HEADERS.any { header == it || header.contains("cr/dr") || header.contains("dr/cr") } -> typeIdx = i
                    refIdx == -1 && REFERENCE_HEADERS.any { header == it || header.contains("ref") || header.contains("utr") } -> refIdx = i
                    recipIdx == -1 && RECIPIENT_HEADERS.any { header == it || header.contains("beneficiary") || header.contains("payee") } -> recipIdx = i
                    balanceIdx == -1 && BALANCE_HEADERS.any { header == it || header.contains("balance") } -> balanceIdx = i
                }
            }

            val mapping = HeaderMapping(
                dateIndex = dateIdx,
                descriptionIndex = descIdx,
                debitIndex = debitIdx,
                creditIndex = creditIdx,
                amountIndex = amountIdx,
                typeIndicatorIndex = typeIdx,
                referenceIndex = refIdx,
                recipientIndex = recipIdx,
                balanceIndex = balanceIdx
            )

            return if (mapping.isValid) mapping else null
        }

        private fun normalizeHeader(header: String): String {
            return header.trim()
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9/\\s]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
