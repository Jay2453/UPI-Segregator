package com.upisegregator.app.data.importer.deduplication

import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.repository.TransactionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Checks candidate transactions against existing database records for the specified [userId].
 *
 * Priority:
 * 1. Transaction reference / UTR match (when available).
 * 2. SHA-256 fingerprint match.
 */
class DatabaseDuplicateDetector(
    private val transactionRepository: TransactionRepository
) {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    suspend fun detectDuplicates(
        candidates: List<ImportableTransaction>,
        userId: String
    ): List<ImportableTransaction> {
        if (candidates.isEmpty()) return emptyList()

        // 1. Gather all candidate UTRs and fingerprints
        val candidateUtrs = candidates.mapNotNull { it.externalReference?.takeIf { ref -> ref.isNotBlank() } }
        val candidateFingerprints = candidates.map { it.fingerprint }

        // 2. Fetch matches from repository
        val dbMatchesByUtr = if (candidateUtrs.isNotEmpty()) {
            transactionRepository.getTransactionsByExternalReferences(candidateUtrs, userId)
                .associateBy { it.externalReference ?: "" }
        } else {
            emptyMap()
        }

        val dbMatchesByFingerprint = if (candidateFingerprints.isNotEmpty()) {
            transactionRepository.getTransactionsByFingerprints(candidateFingerprints, userId)
                .associateBy { it.fingerprint ?: "" }
        } else {
            emptyMap()
        }

        // 3. Mark duplicate status on each candidate
        return candidates.map { candidate ->
            // If already marked as duplicate within the same file, keep that designation
            if (candidate.duplicateStatus == DuplicateStatus.DUPLICATE_IN_FILE) {
                return@map candidate.copy(isSelected = false)
            }

            // Priority 1: UTR / Reference match
            val utrMatch: TransactionEntity? = candidate.externalReference?.let { dbMatchesByUtr[it] }
            if (utrMatch != null) {
                return@map candidate.copy(
                    duplicateStatus = DuplicateStatus.EXACT_MATCH_DATABASE,
                    duplicateReason = "Already in database with UTR ${utrMatch.externalReference} (${dateFormat.format(Date(utrMatch.transactionDate))})",
                    isSelected = false
                )
            }

            // Priority 2: Fingerprint match
            val fingerprintMatch: TransactionEntity? = dbMatchesByFingerprint[candidate.fingerprint]
            if (fingerprintMatch != null) {
                return@map candidate.copy(
                    duplicateStatus = DuplicateStatus.EXACT_MATCH_DATABASE,
                    duplicateReason = "Already imported on ${dateFormat.format(Date(fingerprintMatch.transactionDate))} for ${fingerprintMatch.recipientName}",
                    isSelected = false
                )
            }

            // No duplicate detected
            candidate
        }
    }
}
