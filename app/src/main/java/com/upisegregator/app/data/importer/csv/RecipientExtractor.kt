package com.upisegregator.app.data.importer.csv

import java.util.Locale

/**
 * Heuristics to extract clean recipient or merchant names from banking narration strings.
 */
object RecipientExtractor {

    private val UPI_SLASH_REGEX = Regex("(?i)UPI/(?:DR|CR|P2P|P2M)?/([0-9A-Z]+)/([^/]+)")
    private val UPI_HYPHEN_REGEX = Regex("(?i)UPI(?:-REV)?-([A-Za-z0-9\\s._]+)-")
    private val POS_REGEX = Regex("(?i)POS\\s+[0-9A-Z]+\\s+([^0-9]+)")
    private val NEFT_REGEX = Regex("(?i)NEFT[-/][0-9A-Z]+[-/]([^/-]+)")
    private val IMPS_REGEX = Regex("(?i)IMPS[-/][0-9A-Z]+[-/]([^/-]+)")

    /**
     * Extracts a clean recipient/merchant name from a bank narration string.
     */
    fun extractRecipient(narration: String, fallbackMerchant: String? = null): String {
        if (!fallbackMerchant.isNullOrBlank()) {
            return cleanName(fallbackMerchant)
        }

        val trimmed = narration.trim()
        if (trimmed.isEmpty()) return "Unknown Merchant"

        // 1. UPI slash format: UPI/123456789012/PAYTO/MerchantName/...
        val slashMatch = UPI_SLASH_REGEX.find(trimmed)
        if (slashMatch != null && slashMatch.groupValues.size >= 3) {
            val candidate = slashMatch.groupValues[2].trim()
            if (candidate.isNotBlank() && !candidate.equals("DR", true) && !candidate.equals("CR", true)) {
                return cleanName(candidate)
            }
        }

        // 2. UPI hyphen format: UPI-Swiggy-swiggy@axis-123456
        val hyphenMatch = UPI_HYPHEN_REGEX.find(trimmed)
        if (hyphenMatch != null && hyphenMatch.groupValues.size >= 2) {
            val candidate = hyphenMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) {
                return cleanName(candidate)
            }
        }

        // 3. POS: POS 456789 MCDONALDS BANGALORE
        val posMatch = POS_REGEX.find(trimmed)
        if (posMatch != null && posMatch.groupValues.size >= 2) {
            val candidate = posMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) {
                return cleanName(candidate)
            }
        }

        // 4. NEFT: NEFT-SBIN000123-John Doe-Rent
        val neftMatch = NEFT_REGEX.find(trimmed)
        if (neftMatch != null && neftMatch.groupValues.size >= 2) {
            val candidate = neftMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) {
                return cleanName(candidate)
            }
        }

        // 5. IMPS: IMPS-123456789012-Jane Smith
        val impsMatch = IMPS_REGEX.find(trimmed)
        if (impsMatch != null && impsMatch.groupValues.size >= 2) {
            val candidate = impsMatch.groupValues[1].trim()
            if (candidate.isNotBlank()) {
                return cleanName(candidate)
            }
        }

        // Fallback: take first part before slash or hyphen, up to 40 chars
        val firstSegment = trimmed.split("/", "-", ":").firstOrNull()?.trim()
        if (!firstSegment.isNullOrBlank() && firstSegment.length >= 3) {
            return cleanName(firstSegment.take(35))
        }

        return cleanName(trimmed.take(35))
    }

    private val UPI_SLASH_UTR_REGEX = Regex("(?i)UPI/(?:(?:DR|CR|P2P|P2M)/)?([0-9A-Za-z]{12})/")
    private val UPI_HYPHEN_UTR_REGEX = Regex("(?i)UPI(?:-REV)?-(?:[^-]+-)*([0-9A-Za-z]{12})\\b")
    private val EXPLICIT_REF_KEYWORD_REGEX = Regex("(?i)\\b(?:UTR|RRN|REF\\s*(?:NO)?|TXN\\s*(?:ID|NO)?|CHQ\\s*(?:NO)?)[/:\\s#-]*([0-9A-Za-z]{10,22})\\b")
    private val NON_UTR_PREFIX_PATTERN = Regex("(?i)\\b(?:A/C|ACC|ACCOUNT|ACCT|MOB|MOBILE|PH|PHONE|INV|INVOICE|CARD|LOAN)[/:\\s#-]*(\\d{10,18})\\b")

    /**
     * Extracts transaction reference or UTR from the narration string if available.
     *
     * Strict contextual extraction ensures account numbers, mobile numbers, card numbers,
     * and invoice numbers are never misclassified as UTRs.
     */
    fun extractReference(narration: String): String? {
        val trimmed = narration.trim()
        if (trimmed.isEmpty()) return null

        // 1. UPI slash format: UPI/DR/123456789012/PAYTO/... or UPI/123456789012/...
        val slashMatch = UPI_SLASH_UTR_REGEX.find(trimmed)
        if (slashMatch != null && slashMatch.groupValues.size >= 2) {
            return slashMatch.groupValues[1]
        }

        // 2. UPI hyphen format: UPI-Swiggy-swiggy@axis-123456789012
        val hyphenMatch = UPI_HYPHEN_UTR_REGEX.find(trimmed)
        if (hyphenMatch != null && hyphenMatch.groupValues.size >= 2) {
            return hyphenMatch.groupValues[1]
        }

        // 3. Explicit keywords: UTR: 123456789012, RRN 123456789012, REF NO 123456789012
        val explicitMatch = EXPLICIT_REF_KEYWORD_REGEX.find(trimmed)
        if (explicitMatch != null && explicitMatch.groupValues.size >= 2) {
            val candidate = explicitMatch.groupValues[1]
            // Verify candidate is not actually an account number in an adjacent pattern
            val nonUtrMatch = NON_UTR_PREFIX_PATTERN.find(trimmed)
            if (nonUtrMatch != null && nonUtrMatch.groupValues[1] == candidate) {
                return null
            }
            return candidate
        }

        return null
    }

    private fun cleanName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9\\s.&'-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.isBlank()) "Unknown Merchant" else cleaned
    }
}
