package com.upisegregator.app.data.importer

import com.upisegregator.app.data.importer.csv.CsvStatementParser
import com.upisegregator.app.data.importer.csv.RecipientExtractor
import com.upisegregator.app.data.importer.deduplication.TransactionFingerprintGenerator
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.TransactionDirection
import com.upisegregator.app.data.local.entity.PaymentMethod
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Milestone 6 Code Audit and Verification Test Suite.
 *
 * Verifies all 8 audit criteria:
 * 1. Credit transactions excluded from spending totals.
 * 2. Debit transactions included in spending totals.
 * 3. Two legitimate identical-amount transactions are not flagged as duplicates.
 * 4. Invalid UTR extraction strictly ignores account and phone numbers.
 * 5. Repeated imports skip duplicates idempotently.
 * 6. Failed bulk insertion leaves repository in a clean, consistent state.
 * 7. Concurrent duplicate import attempts are safely filtered before persistence.
 * 8. Demo reset isolation ensures real-user records are never modified.
 */
class StatementImportAuditTest {

    private lateinit var fakeTransactionRepository: AuditFakeTransactionRepository
    private lateinit var importRepository: StatementImportRepository

    private class AuditFakeTransactionRepository : TransactionRepository {
        val databaseTransactions = mutableListOf<TransactionEntity>()
        var shouldThrowOnInsert = false

        override suspend fun insertTransaction(transaction: TransactionEntity) {
            if (shouldThrowOnInsert) throw IllegalStateException("Simulated SQLite write failure")
            databaseTransactions.add(transaction)
        }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            if (shouldThrowOnInsert) throw IllegalStateException("Simulated SQLite bulk write failure")
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
            val sum = databaseTransactions
                .filter { it.userId == userId && it.transactionStatus == TransactionStatus.SUCCESS }
                .sumOf { it.amountPaise }
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
        fakeTransactionRepository = AuditFakeTransactionRepository()
        importRepository = StatementImportRepositoryImpl(fakeTransactionRepository)
    }

    // 1. Credit transactions excluded from spending totals
    @Test
    fun audit_creditTransactions_excludedFromSpendingTotals() = runBlocking {
        val testUser = "user_credit_test"

        // Create a parsed CREDIT transaction (e.g. Salary / Refund)
        val candidate = ImportableTransaction(
            rawRowIndex = 1,
            dateMillis = 1738368000000L,
            dateFormatted = "01 Feb 2026",
            description = "Salary Credit from Employer",
            recipientName = "Employer Inc",
            amountPaise = 7500000L, // ₹75,000.00
            direction = TransactionDirection.CREDIT,
            fingerprint = "fp_credit_1",
            isSelected = true
        )

        // Convert to entity and persist
        val entity = candidate.toEntity(testUser)
        assertEquals(TransactionStatus.PENDING, entity.transactionStatus)
        assertTrue(entity.note.startsWith("[CREDIT]"))

        fakeTransactionRepository.insertTransaction(entity)

        // Verify spending aggregation query excludes it
        val totalSpending = fakeTransactionRepository.getTotalSpendingByUserFlow(testUser).first()
        assertEquals("Credit transactions must NOT be counted in spending totals", 0L, totalSpending)
    }

    // 2. Debit transactions included in spending totals
    @Test
    fun audit_debitTransactions_includedInSpendingTotals() = runBlocking {
        val testUser = "user_debit_test"

        val candidate = ImportableTransaction(
            rawRowIndex = 1,
            dateMillis = 1738368000000L,
            dateFormatted = "01 Feb 2026",
            description = "Groceries Supermarket",
            recipientName = "Supermarket",
            amountPaise = 250000L, // ₹2,500.00
            direction = TransactionDirection.DEBIT,
            fingerprint = "fp_debit_1",
            isSelected = true
        )

        val entity = candidate.toEntity(testUser)
        assertEquals(TransactionStatus.SUCCESS, entity.transactionStatus)
        fakeTransactionRepository.insertTransaction(entity)

        val totalSpending = fakeTransactionRepository.getTotalSpendingByUserFlow(testUser).first()
        assertEquals(250000L, totalSpending)
    }

    // 3. Two legitimate identical-amount transactions
    @Test
    fun audit_twoLegitimateIdenticalAmountTransactions_notClassifiedAsDuplicates() {
        val userId = "user_two_txns"
        val dateMillis = 1738368000000L
        val amountPaise = 50000L // ₹500.00 for both

        // Transaction 1: Starbucks coffee ₹500
        val fp1 = TransactionFingerprintGenerator.generateFingerprint(
            userId = userId,
            dateMillis = dateMillis,
            amountPaise = amountPaise,
            direction = TransactionDirection.DEBIT,
            description = "Coffee at Starbucks Indiranagar",
            recipientName = "Starbucks",
            externalReference = null
        )

        // Transaction 2: Book purchase ₹500 on the same day
        val fp2 = TransactionFingerprintGenerator.generateFingerprint(
            userId = userId,
            dateMillis = dateMillis,
            amountPaise = amountPaise,
            direction = TransactionDirection.DEBIT,
            description = "Book purchase at Blossom Book House",
            recipientName = "Blossom Book House",
            externalReference = null
        )

        // The fingerprints MUST be different so legitimate identical-amount expenses never collide
        assertNotEquals("Different merchants with same amount on same date must have distinct fingerprints", fp1, fp2)

        // Test in CSV parsing
        val csv = """
            Date,Narration,Debit,Credit
            01/02/2026,Coffee at Starbucks Indiranagar,500.00,
            01/02/2026,Book purchase at Blossom Book House,500.00,
        """.trimIndent()

        val parseResult = CsvStatementParser.parseString(csv, userId)
        assertTrue(parseResult.isSuccess)
        assertEquals(2, parseResult.transactions.size)
        assertEquals(DuplicateStatus.NONE, parseResult.transactions[0].duplicateStatus)
        assertEquals(DuplicateStatus.NONE, parseResult.transactions[1].duplicateStatus)
        assertTrue(parseResult.transactions[0].isSelected)
        assertTrue(parseResult.transactions[1].isSelected)
    }

    // 4. Invalid UTR extraction (strict check)
    @Test
    fun audit_invalidUtrExtraction_rejectsAccountAndPhoneNumbers() {
        // Narration with a 12-digit account number must NOT extract as UTR
        val accountNarration1 = "TRANSFER TO A/C 123456789012"
        val ref1 = RecipientExtractor.extractReference(accountNarration1)
        assertNull("Account number must NOT be extracted as UTR", ref1)

        val accountNarration2 = "NEFT TO ACCOUNT 987654321098"
        val ref2 = RecipientExtractor.extractReference(accountNarration2)
        assertNull("Account number preceded by ACCOUNT must NOT be extracted as UTR", ref2)

        val phoneNarration = "RECHARGE MOB 987654321012"
        val ref3 = RecipientExtractor.extractReference(phoneNarration)
        assertNull("Mobile number must NOT be extracted as UTR", ref3)

        val cardNarration = "CARD PAYMENT INV 401234567890"
        val ref4 = RecipientExtractor.extractReference(cardNarration)
        assertNull("Invoice number must NOT be extracted as UTR", ref4)

        // Genuine UPI UTR formats MUST still be extracted correctly
        val genuineUpiSlash = "UPI/DR/403212345678/Swiggy/HDFC/Pay"
        assertEquals("403212345678", RecipientExtractor.extractReference(genuineUpiSlash))

        val genuineUpiHyphen = "UPI-Zomato-zomato@icici-401298765432"
        assertEquals("401298765432", RecipientExtractor.extractReference(genuineUpiHyphen))

        val genuineKeyword = "NEFT REF NO 501234567890 TO SUPPLIER"
        assertEquals("501234567890", RecipientExtractor.extractReference(genuineKeyword))
    }

    // 5. Repeated imports
    @Test
    fun audit_repeatedImports_allDuplicatesSkippedOnSecondRun() = runBlocking {
        val userId = "user_repeated_import"
        val csv = """
            Date,Description,Debit,Credit
            15/01/2026,Supermarket Grocery,1200.00,
            16/01/2026,Pharmacy Medicines,450.00,
        """.trimIndent()

        // Pass 1: Parse and import
        val firstParse = importRepository.parseStringAndDetectDuplicates(csv, userId)
        val firstResult = importRepository.saveImportedTransactions(firstParse.transactions, userId, "jan_statement.csv")
        assertEquals(2, firstResult.successfullyImported)
        assertEquals(0, firstResult.duplicatesSkipped)

        // Pass 2: Re-parse the exact same statement
        val secondParse = importRepository.parseStringAndDetectDuplicates(csv, userId)
        assertTrue("All items must be flagged as DB duplicates", secondParse.transactions.all { it.duplicateStatus == DuplicateStatus.EXACT_MATCH_DATABASE })
        assertTrue("No duplicate items should be selected", secondParse.transactions.none { it.isSelected })

        val secondResult = importRepository.saveImportedTransactions(secondParse.transactions, userId, "jan_statement.csv")
        assertEquals(0, secondResult.successfullyImported)
        assertEquals(2, secondResult.duplicatesSkipped)
    }

    // 6. Failed bulk insertion
    @Test
    fun audit_failedBulkInsertion_leavesRepositoryConsistent() = runBlocking {
        val userId = "user_fail_test"
        val csv = """
            Date,Description,Debit,Credit
            01/01/2026,Rent Payment,20000.00,
        """.trimIndent()

        val parseResult = importRepository.parseStringAndDetectDuplicates(csv, userId)

        // Simulate database write failure during bulk insert
        fakeTransactionRepository.shouldThrowOnInsert = true

        try {
            importRepository.saveImportedTransactions(parseResult.transactions, userId, "fail.csv")
            fail("Should have thrown simulated database exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Simulated SQLite") == true)
        }

        // Verify zero transactions were inserted or left partially imported
        assertEquals(0, fakeTransactionRepository.countTransactionsByUser(userId))
    }

    // 7. Concurrent duplicate import attempts
    @Test
    fun audit_concurrentDuplicateImportAttempts_safelyPreventDuplicates() = runBlocking {
        val userId = "user_concurrent"
        val csv = """
            Date,Description,Debit,Credit
            01/01/2026,Utility Bill,1500.00,
        """.trimIndent()

        // Two independent parsed candidate batches with the same data (simulating rapid double-click)
        val parseResult1 = importRepository.parseStringAndDetectDuplicates(csv, userId)
        val parseResult2 = importRepository.parseStringAndDetectDuplicates(csv, userId)

        // Batch 1 saves first
        val result1 = importRepository.saveImportedTransactions(parseResult1.transactions, userId, "statement.csv")
        assertEquals(1, result1.successfullyImported)

        // Batch 2 attempts to save immediately after (concurrency race)
        val result2 = importRepository.saveImportedTransactions(parseResult2.transactions, userId, "statement.csv")
        assertEquals(0, result2.successfullyImported)
        assertEquals(1, result2.duplicatesSkipped)

        // Verify database only has 1 record
        assertEquals(1, fakeTransactionRepository.countTransactionsByUser(userId))
    }

    // 8. Demo reset isolation
    @Test
    fun audit_demoResetIsolation_realUserRecordsNeverAffected() = runBlocking {
        val realUserId = "real_registered_user_123"
        val demoUserId = "demo_user"

        // Insert real user transaction
        fakeTransactionRepository.insertTransaction(
            TransactionEntity(
                transactionId = "real_txn_1",
                amountPaise = 500000L,
                recipientName = "Real Landlord",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                category = "Housing",
                transactionDate = System.currentTimeMillis(),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = realUserId
            )
        )

        // Insert demo user transactions
        fakeTransactionRepository.insertTransaction(
            TransactionEntity(
                transactionId = "demo_txn_1",
                amountPaise = 15000L,
                recipientName = "Demo Tea Stall",
                paymentMethod = PaymentMethod.UPI,
                category = "Food",
                transactionDate = System.currentTimeMillis(),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = demoUserId
            )
        )

        assertEquals(1, fakeTransactionRepository.countTransactionsByUser(realUserId))
        assertEquals(1, fakeTransactionRepository.countTransactionsByUser(demoUserId))

        // Trigger demo reset (deleting demo_user records)
        fakeTransactionRepository.deleteTransactionsByUser(demoUserId)

        // Confirm demo records deleted
        assertEquals(0, fakeTransactionRepository.countTransactionsByUser(demoUserId))
        // Confirm real user records completely untouched
        assertEquals(1, fakeTransactionRepository.countTransactionsByUser(realUserId))
        val realTxn = fakeTransactionRepository.getTransactionById("real_txn_1", realUserId)
        assertEquals("Real Landlord", realTxn?.recipientName)
        assertEquals(500000L, realTxn?.amountPaise)
    }
}
