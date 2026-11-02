package com.upisegregator.app.data.importer

import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatementImportRepositoryTest {

    private lateinit var fakeTransactionRepository: FakeTransactionRepository
    private lateinit var importRepository: StatementImportRepository

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
            val sum = databaseTransactions.filter { it.userId == userId && it.transactionStatus == TransactionStatus.SUCCESS }.sumOf { it.amountPaise }
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
        fakeTransactionRepository = FakeTransactionRepository()
        importRepository = StatementImportRepositoryImpl(fakeTransactionRepository)
    }

    @Test
    fun databaseDeduplication_byUtr() = runBlocking {
        // Pre-populate database with an existing UPI payment having UTR 998877665544
        fakeTransactionRepository.insertTransaction(
            TransactionEntity(
                transactionId = "existing_1",
                amountPaise = 50000L,
                recipientName = "Swiggy",
                paymentMethod = PaymentMethod.UPI,
                category = "Food",
                transactionDate = 1738368000000L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.UPI_INTENT,
                externalReference = "998877665544",
                userId = "user_alpha"
            )
        )

        val csv = """
            Date,Narration,Ref No,Debit,Credit
            01/02/2026,UPI-Swiggy-998877665544,998877665544,500.00,
            01/02/2026,Zomato Order,112233445566,350.00,
        """.trimIndent()

        val parseResult = importRepository.parseStringAndDetectDuplicates(csv, "user_alpha")
        assertTrue(parseResult.isSuccess)
        assertEquals(2, parseResult.transactions.size)

        // Row 1 matches UTR in DB
        val matchUtr = parseResult.transactions[0]
        assertEquals(DuplicateStatus.EXACT_MATCH_DATABASE, matchUtr.duplicateStatus)
        assertFalse(matchUtr.isSelected)
        assertTrue(matchUtr.duplicateReason?.contains("UTR 998877665544") == true)

        // Row 2 is fresh
        val fresh = parseResult.transactions[1]
        assertEquals(DuplicateStatus.NONE, fresh.duplicateStatus)
        assertTrue(fresh.isSelected)
    }

    @Test
    fun repeatedFileImport_allMarkedAsDuplicatesOnSecondRun() = runBlocking {
        val csv = """
            Date,Description,Debit,Credit
            05/01/2026,Cafe Coffee Day,250.00,
            06/01/2026,Bookstore,600.00,
        """.trimIndent()

        // First parse and import
        val firstParse = importRepository.parseStringAndDetectDuplicates(csv, "user_beta")
        assertEquals(2, firstParse.transactions.size)
        assertTrue(firstParse.transactions.all { it.duplicateStatus == DuplicateStatus.NONE })

        val firstSummary = importRepository.saveImportedTransactions(
            allCandidates = firstParse.transactions,
            userId = "user_beta",
            fileName = "statement.csv"
        )
        assertEquals(2, firstSummary.successfullyImported)
        assertEquals(0, firstSummary.duplicatesSkipped)

        // Second parse of the exact same CSV
        val secondParse = importRepository.parseStringAndDetectDuplicates(csv, "user_beta")
        assertEquals(2, secondParse.transactions.size)

        // All items must now be flagged as database duplicates!
        assertTrue(secondParse.transactions.all { it.duplicateStatus == DuplicateStatus.EXACT_MATCH_DATABASE })
        assertTrue(secondParse.transactions.none { it.isSelected })

        // Second import saves 0
        val secondSummary = importRepository.saveImportedTransactions(
            allCandidates = secondParse.transactions,
            userId = "user_beta",
            fileName = "statement.csv"
        )
        assertEquals(0, secondSummary.successfullyImported)
        assertEquals(2, secondSummary.duplicatesSkipped)
    }

    @Test
    fun userIsolation_importsDoNotCollideAcrossUsers() = runBlocking {
        val csv = """
            Date,Narration,Ref No,Debit,Credit
            10/01/2026,Grocery Store,UTR777888,1000.00,
        """.trimIndent()

        // User 1 imports the transaction
        val user1Parse = importRepository.parseStringAndDetectDuplicates(csv, "user_1")
        importRepository.saveImportedTransactions(user1Parse.transactions, "user_1", "user1.csv")

        // User 2 imports the identical statement
        val user2Parse = importRepository.parseStringAndDetectDuplicates(csv, "user_2")

        // For User 2, this must NOT be a duplicate!
        assertEquals(DuplicateStatus.NONE, user2Parse.transactions[0].duplicateStatus)
        assertTrue(user2Parse.transactions[0].isSelected)

        importRepository.saveImportedTransactions(user2Parse.transactions, "user_2", "user2.csv")

        assertEquals(1, fakeTransactionRepository.countTransactionsByUser("user_1"))
        assertEquals(1, fakeTransactionRepository.countTransactionsByUser("user_2"))
    }

    @Test
    fun demoModeIsolation_demoResetDoesNotAffectRealUserImports() = runBlocking {
        // User 1 imports
        fakeTransactionRepository.insertTransaction(
            TransactionEntity(
                transactionId = "user_real_1",
                amountPaise = 50000L,
                recipientName = "Real Shop",
                paymentMethod = PaymentMethod.UPI,
                category = "Shopping",
                transactionDate = System.currentTimeMillis(),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = "real_user_id"
            )
        )

        // Demo user imports
        fakeTransactionRepository.insertTransaction(
            TransactionEntity(
                transactionId = "demo_csv_1",
                amountPaise = 10000L,
                recipientName = "Demo Shop",
                paymentMethod = PaymentMethod.UPI,
                category = "Food",
                transactionDate = System.currentTimeMillis(),
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = "demo_user"
            )
        )

        // Reset demo user
        fakeTransactionRepository.deleteTransactionsByUser("demo_user")

        // Real user's transaction must remain completely untouched
        assertEquals(0, fakeTransactionRepository.countTransactionsByUser("demo_user"))
        assertEquals(1, fakeTransactionRepository.countTransactionsByUser("real_user_id"))
    }

    @Test
    fun importSummary_calculatesCorrectTotals() = runBlocking {
        val csv = """
            Date,Description,Debit,Credit
            01/01/2026,Valid Expense 1,500.00,
            02/01/2026,Valid Expense 2,300.00,
            03/01/2026,Salary Credit,,25000.00
            not-a-date,Broken Row,100.00,
        """.trimIndent()

        val parseResult = importRepository.parseStringAndDetectDuplicates(csv, "user_summary")
        val summary = importRepository.saveImportedTransactions(
            allCandidates = parseResult.transactions,
            userId = "user_summary",
            fileName = "my_statement.csv"
        )

        assertEquals("my_statement.csv", summary.fileName)
        assertEquals(4, summary.totalRowsDetected)
        // 2 debits selected by default (credits and invalid are deselected)
        assertEquals(2, summary.successfullyImported)
        assertEquals(1, summary.invalidSkipped)
        assertEquals(80000L, summary.totalDebitPaise) // ₹500 + ₹300 = ₹800 = 80000 paise
    }
}
