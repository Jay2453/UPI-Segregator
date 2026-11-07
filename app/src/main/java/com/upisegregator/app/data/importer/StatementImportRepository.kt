package com.upisegregator.app.data.importer

import com.upisegregator.app.data.importer.csv.CsvParseResult
import com.upisegregator.app.data.importer.csv.CsvStatementParser
import com.upisegregator.app.data.importer.deduplication.DatabaseDuplicateDetector
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.StatementImportResult
import com.upisegregator.app.data.importer.model.TransactionDirection
import com.upisegregator.app.data.repository.TransactionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream

interface StatementImportRepository {
    suspend fun parseAndDetectDuplicates(
        inputStream: InputStream,
        userId: String
    ): CsvParseResult

    suspend fun parseStringAndDetectDuplicates(
        csvContent: String,
        userId: String
    ): CsvParseResult

    suspend fun saveImportedTransactions(
        allCandidates: List<ImportableTransaction>,
        userId: String,
        fileName: String
    ): StatementImportResult
}

class StatementImportRepositoryImpl(
    private val transactionRepository: TransactionRepository
) : StatementImportRepository {

    private val importMutex = Mutex()
    private val duplicateDetector = DatabaseDuplicateDetector(transactionRepository)

    override suspend fun parseAndDetectDuplicates(
        inputStream: InputStream,
        userId: String
    ): CsvParseResult {
        val initialResult = CsvStatementParser.parse(inputStream, userId)
        if (!initialResult.isSuccess || initialResult.transactions.isEmpty()) {
            return initialResult
        }

        // Deduplicate against the persistent database records for this user
        val enriched = duplicateDetector.detectDuplicates(initialResult.transactions, userId)

        return initialResult.copy(transactions = enriched)
    }

    override suspend fun parseStringAndDetectDuplicates(
        csvContent: String,
        userId: String
    ): CsvParseResult {
        return parseAndDetectDuplicates(csvContent.byteInputStream(Charsets.UTF_8), userId)
    }

    override suspend fun saveImportedTransactions(
        allCandidates: List<ImportableTransaction>,
        userId: String,
        fileName: String
    ): StatementImportResult {
        return importMutex.withLock {
            val selectedValid = allCandidates.filter {
                it.isSelected &&
                        it.validationStatus == RowValidationStatus.VALID &&
                        it.duplicateStatus == DuplicateStatus.NONE
            }

            // Concurrency guard: Re-check fingerprints and UTRs against persistent storage
            // to prevent race conditions or repeated save invocations.
            val candidateFingerprints = selectedValid.map { it.fingerprint }
            val candidateUtrs = selectedValid.mapNotNull { it.externalReference?.takeIf { ref -> ref.isNotBlank() } }

            val existingFingerprints = if (candidateFingerprints.isNotEmpty()) {
                transactionRepository.getTransactionsByFingerprints(candidateFingerprints, userId)
                    .mapNotNull { it.fingerprint }
                    .toSet()
            } else emptySet()

            val existingUtrs = if (candidateUtrs.isNotEmpty()) {
                transactionRepository.getTransactionsByExternalReferences(candidateUtrs, userId)
                    .mapNotNull { it.externalReference }
                    .toSet()
            } else emptySet()

            val seenInThisBatchFp = mutableSetOf<String>()
            val seenInThisBatchUtr = mutableSetOf<String>()
            val safeToInsert = mutableListOf<ImportableTransaction>()

            for (candidate in selectedValid) {
                val fpConflict = candidate.fingerprint in existingFingerprints || candidate.fingerprint in seenInThisBatchFp
                val utrConflict = candidate.externalReference != null && (
                    candidate.externalReference in existingUtrs || candidate.externalReference in seenInThisBatchUtr
                )
                if (!fpConflict && !utrConflict) {
                    safeToInsert.add(candidate)
                    seenInThisBatchFp.add(candidate.fingerprint)
                    candidate.externalReference?.let { seenInThisBatchUtr.add(it) }
                }
            }

            val concurrencyDuplicatesSkipped = selectedValid.size - safeToInsert.size
            val totalDuplicatesSkipped = allCandidates.count {
                it.duplicateStatus != DuplicateStatus.NONE
            } + concurrencyDuplicatesSkipped

            val invalidSkipped = allCandidates.count {
                it.validationStatus == RowValidationStatus.INVALID
            }

            val reviewCount = allCandidates.count {
                it.validationStatus == RowValidationStatus.NEEDS_REVIEW
            }

            var totalDebitPaise = 0L
            var totalCreditPaise = 0L

            val entitiesToSave = safeToInsert.map { item ->
                if (item.direction == TransactionDirection.DEBIT) {
                    totalDebitPaise += item.amountPaise
                } else {
                    totalCreditPaise += item.amountPaise
                }
                item.toEntity(userId)
            }

            if (entitiesToSave.isNotEmpty()) {
                transactionRepository.insertTransactions(entitiesToSave)
            }

            StatementImportResult(
                fileName = fileName,
                totalRowsDetected = allCandidates.size,
                successfullyImported = entitiesToSave.size,
                duplicatesSkipped = totalDuplicatesSkipped,
                invalidSkipped = invalidSkipped,
                reviewCount = reviewCount,
                totalDebitPaise = totalDebitPaise,
                totalCreditPaise = totalCreditPaise
            )
        }
    }
}
