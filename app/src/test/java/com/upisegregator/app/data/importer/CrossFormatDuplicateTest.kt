package com.upisegregator.app.data.importer

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.pdf.SyntheticPdfFactory
import com.upisegregator.app.data.local.entity.TransactionDirection
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CrossFormatDuplicateTest {

    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var importRepository: StatementImportRepository

    private val testUserId = "user_alpha"
    private val otherUserId = "user_beta"

    private class FakeTransactionRepository : TransactionRepository {
        val databaseTransactions = mutableListOf<TransactionEntity>()

        override suspend fun insertTransaction(transaction: TransactionEntity) {
            databaseTransactions.add(transaction)
        }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            databaseTransactions.addAll(transactions)
        }

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            val idx = databaseTransactions.indexOfFirst { it.transactionId == transaction.transactionId }
            if (idx != -1) databaseTransactions[idx] = transaction
        }

        override suspend fun deleteTransaction(transaction: TransactionEntity) {
            databaseTransactions.removeAll { it.transactionId == transaction.transactionId }
        }

        override suspend fun deleteTransactionById(transactionId: String, userId: String) {
            databaseTransactions.removeAll { it.transactionId == transactionId && it.userId == userId }
        }

        override fun getTransactionsByUserFlow(userId: String): Flow<List<TransactionEntity>> {
            return flowOf(databaseTransactions.filter { it.userId == userId })
        }

        override suspend fun getTransactionById(transactionId: String, userId: String): TransactionEntity? {
            return databaseTransactions.find { it.transactionId == transactionId && it.userId == userId }
        }

        override suspend fun getTransactionByIdOnly(transactionId: String): TransactionEntity? {
            return databaseTransactions.find { it.transactionId == transactionId }
        }

        override fun getTransactionsByRecipientFlow(recipientId: String, userId: String): Flow<List<TransactionEntity>> {
            return flowOf(databaseTransactions.filter { it.recipientId == recipientId && it.userId == userId })
        }

        override fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long, userId: String): Flow<List<TransactionEntity>> {
            return flowOf(databaseTransactions.filter { it.userId == userId && it.transactionDate in startDate..endDate })
        }

        override fun getTotalSpendingByUserFlow(userId: String): Flow<Long> {
            val sum = databaseTransactions.filter {
                it.userId == userId && it.transactionStatus == TransactionStatus.SUCCESS && it.direction == TransactionDirection.DEBIT
            }.sumOf { it.amountPaise }
            return flowOf(sum)
        }

        override fun countTransactionsByUserFlow(userId: String): Flow<Int> {
            return flowOf(databaseTransactions.count { it.userId == userId })
        }

        override suspend fun countTransactionsByUser(userId: String): Int {
            return databaseTransactions.count { it.userId == userId }
        }

        override suspend fun deleteTransactionsByUser(userId: String) {
            databaseTransactions.removeAll { it.userId == userId }
        }

        override suspend fun getTransactionByExternalReference(externalReference: String, userId: String): TransactionEntity? {
            return databaseTransactions.find { it.userId == userId && it.externalReference == externalReference }
        }

        override suspend fun getTransactionByFingerprint(fingerprint: String, userId: String): TransactionEntity? {
            return databaseTransactions.find { it.userId == userId && it.fingerprint == fingerprint }
        }

        override suspend fun getTransactionsByExternalReferences(references: List<String>, userId: String): List<TransactionEntity> {
            return databaseTransactions.filter { it.userId == userId && it.externalReference in references }
        }

        override suspend fun getTransactionsByFingerprints(fingerprints: List<String>, userId: String): List<TransactionEntity> {
            return databaseTransactions.filter { it.userId == userId && it.fingerprint in fingerprints }
        }
    }

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        PDFBoxResourceLoader.init(context)
        fakeTransactionRepository = FakeTransactionRepository()
        importRepository = StatementImportRepositoryImpl(fakeTransactionRepository)
    }

    @Test
    fun testCrossFormatDuplicate_importCsvFirstThenPdf_detectedAsDuplicate() = runBlocking {
        // 1. Import transaction via CSV first
        val csvData = """
            Date,Narration,Chq/Ref No,Debit,Credit,Balance
            01/10/2026,UPI/DR/123456789012/Swiggy/swiggy@icici,123456789012,450.00,,14550.00
        """.trimIndent()

        val csvResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = csvData.toByteArray(Charsets.UTF_8),
            fileName = "hdfc_export.csv",
            mimeType = "text/csv",
            userId = testUserId
        )
        assertTrue(csvResult.isSuccess)
        assertEquals(1, csvResult.transactions.size)
        assertEquals(DuplicateStatus.NONE, csvResult.transactions[0].duplicateStatus)

        // Save CSV transaction to database
        val saveResult = importRepository.saveImportedTransactions(
            allCandidates = csvResult.transactions,
            userId = testUserId,
            fileName = "hdfc_export.csv"
        )
        assertEquals(1, saveResult.successfullyImported)
        assertEquals(TransactionSource.CSV_IMPORT, fakeTransactionRepository.databaseTransactions[0].transactionSource)

        // 2. Now import identical transaction through synthetic PDF
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val pdfResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = pdfBytes,
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            userId = testUserId
        )

        assertTrue(pdfResult.isSuccess)
        assertEquals(5, pdfResult.transactions.size)

        // Row 1 (Swiggy 450.00 with UTR 123456789012) must be flagged as duplicate in database!
        val swiggyRowInPdf = pdfResult.transactions.first { it.amountPaise == 45000L }
        assertEquals(DuplicateStatus.EXACT_MATCH_DATABASE, swiggyRowInPdf.duplicateStatus)
        assertFalse("Duplicate candidate must be deselected by default", swiggyRowInPdf.isSelected)
        assertTrue(swiggyRowInPdf.duplicateReason!!.contains("Already in database") || swiggyRowInPdf.duplicateReason!!.contains("Already imported"))

        // Other rows (Amazon, Salary, Uber, Zomato) are NOT duplicates
        val amazonRow = pdfResult.transactions.first { it.amountPaise == 129900L }
        assertEquals(DuplicateStatus.NONE, amazonRow.duplicateStatus)
        assertTrue(amazonRow.isSelected)
    }

    @Test
    fun testCrossFormatDuplicate_importPdfFirstThenCsv_detectedAsDuplicate() = runBlocking {
        // 1. Import synthetic PDF first
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val pdfResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = pdfBytes,
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            userId = testUserId
        )
        assertTrue(pdfResult.isSuccess)

        // Save all valid PDF transactions
        val pdfSaveResult = importRepository.saveImportedTransactions(
            allCandidates = pdfResult.transactions,
            userId = testUserId,
            fileName = "statement.pdf"
        )
        assertEquals(5, pdfSaveResult.successfullyImported)
        // Check source tag
        assertTrue(fakeTransactionRepository.databaseTransactions.all { it.transactionSource == TransactionSource.PDF_IMPORT })

        // 2. Now parse CSV containing the same transactions
        val csvData = """
            Date,Narration,Chq/Ref No,Debit,Credit,Balance
            01/10/2026,UPI/DR/123456789012/Swiggy/swiggy@icici,123456789012,450.00,,14550.00
            04-Oct-2026,UPI/DR/554433221100/Uber/uber@axis,554433221100,320.00,,62931.00
        """.trimIndent()

        val csvResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = csvData.toByteArray(Charsets.UTF_8),
            fileName = "statement.csv",
            mimeType = "text/csv",
            userId = testUserId
        )

        assertTrue(csvResult.isSuccess)
        assertEquals(2, csvResult.transactions.size)

        // Both rows must be flagged as duplicates
        assertEquals(DuplicateStatus.EXACT_MATCH_DATABASE, csvResult.transactions[0].duplicateStatus)
        assertFalse(csvResult.transactions[0].isSelected)

        assertEquals(DuplicateStatus.EXACT_MATCH_DATABASE, csvResult.transactions[1].duplicateStatus)
        assertFalse(csvResult.transactions[1].isSelected)
    }

    @Test
    fun testFinancialInvariants_creditsExcludedFromSpending_onlySuccessDebitsCount() = runBlocking {
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val pdfResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = pdfBytes,
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            userId = testUserId
        )

        // PDF has:
        // Debits: Swiggy (450), Amazon (1299), Uber (320) -> Total Debits = 2,069.00 (206900 Paise)
        // Credits: Salary (50000), Zomato refund (150) -> Total Credits = 50,150.00 (5015000 Paise)
        val saveResult = importRepository.saveImportedTransactions(
            allCandidates = pdfResult.transactions,
            userId = testUserId,
            fileName = "statement.pdf"
        )

        assertEquals(206900L, saveResult.totalDebitPaise)
        assertEquals(5015000L, saveResult.totalCreditPaise)

        // Spending calculation query MUST ONLY count SUCCESS + DEBIT:
        val totalSpending = fakeTransactionRepository.getTotalSpendingByUserFlow(testUserId).first()
        assertEquals("Total spending must equal sum of debits only", 206900L, totalSpending)
        assertFalse("Total spending must not include credits", totalSpending >= 5000000L)
    }

    @Test
    fun testDataIsolation_differentUsersDoNotCollide() = runBlocking {
        // User Alpha imports PDF
        val pdfBytes = SyntheticPdfFactory.createMultiPageStatementPdf()
        val alphaResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = pdfBytes,
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            userId = testUserId
        )
        importRepository.saveImportedTransactions(
            allCandidates = alphaResult.transactions,
            userId = testUserId,
            fileName = "statement.pdf"
        )

        // User Beta (or demo_user) imports the identical PDF
        val betaResult = importRepository.parseFileAndDetectDuplicates(
            fileBytes = pdfBytes,
            fileName = "statement.pdf",
            mimeType = "application/pdf",
            userId = otherUserId
        )

        // None of User Beta's transactions should be flagged as duplicates of User Alpha's transactions!
        assertTrue(betaResult.transactions.all { it.duplicateStatus == DuplicateStatus.NONE })
        assertTrue(betaResult.transactions.all { it.isSelected })
    }
}
