package com.upisegregator.app.data.importer.deduplication

import com.upisegregator.app.data.importer.model.TransactionDirection
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic SHA-256 fingerprint generator for bank statement transactions.
 *
 * Guarantees cross-run stability by normalizing whitespace, case, date representations,
 * and canonical integer amounts. Prevents collisions between distinct transactions while
 * reliably matching genuine duplicates.
 */
object TransactionFingerprintGenerator {

    private val canonicalUtcDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Generates a stable SHA-256 hexadecimal string representing this transaction's unique signature.
     */
    fun generateFingerprint(
        userId: String,
        dateMillis: Long,
        amountPaise: Long,
        direction: TransactionDirection,
        description: String,
        recipientName: String,
        externalReference: String? = null
    ): String {
        val normalizedUserId = userId.trim().lowercase(Locale.US)
        val normalizedDate = canonicalUtcDateFormat.format(Date(dateMillis))
        val normalizedAmount = amountPaise.toString()
        val normalizedDirection = direction.name
        val normalizedDescription = normalizeText(description)
        val normalizedRecipient = normalizeText(recipientName)
        val normalizedReference = externalReference?.trim()?.uppercase(Locale.US) ?: ""

        val rawInput = buildString {
            append(normalizedUserId).append("|")
            append(normalizedDate).append("|")
            append(normalizedAmount).append("|")
            append(normalizedDirection).append("|")
            append(normalizedDescription).append("|")
            append(normalizedRecipient).append("|")
            append(normalizedReference)
        }

        return sha256(rawInput)
    }

    /**
     * Normalizes text by converting to lowercase, replacing non-alphanumeric characters
     * with single spaces, and trimming superfluous whitespace.
     */
    fun normalizeText(text: String): String {
        return text.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
