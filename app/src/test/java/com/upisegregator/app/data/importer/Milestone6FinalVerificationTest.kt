package com.upisegregator.app.data.importer

import com.upisegregator.app.data.importer.csv.AmountParser
import com.upisegregator.app.data.importer.csv.CsvStatementParser
import com.upisegregator.app.data.importer.csv.DateFormats
import com.upisegregator.app.data.importer.csv.HeaderMapping
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Comprehensive verification test suite for Milestone 6:
 * - FIX A: Transaction Direction (DEBIT vs CREDIT) and Spending Invariants
 * - FIX B: Concurrency Safety and Deduplication
 * - FIX C: Timezone Invariance across IST, UTC, and GMT-
 * - FIX D: Unknown / Ambiguous Direction Handling (NEEDS_REVIEW)
 * - Bank Format Compatibility: HDFC, SBI, ICICI, Axis, Kotak, Generic CSV
 */
class Milestone6FinalVerificationTest {

    private lateinit var fakeRepository: VerificationFakeTransactionRepository
    private lateinit var importRepository: StatementImportRepository

    private class VerificationFakeTransactionRepository : TransactionRepository {
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

        /**
         * Core financial invariant: only confirmed SUCCESS DEBIT transactions count towards spending totals.
         */
        override fun getTotalSpendingByUserFlow(userId: String): Flow<Long> {
            val sum = databaseTransactions
                .filter { it.userId == userId && it.transactionStatus == TransactionStatus.SUCCESS && it.direction == TransactionDirection.DEBIT }
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
        fakeRepository = VerificationFakeTransactionRepository()
        importRepository = StatementImportRepositoryImpl(fakeRepository)
    }

    // =========================================================================
    // 1. FIX A: TRANSACTION DIRECTION & SPENDING INVARIANTS
    // =========================================================================

    @Test
    fun direction_successfulDebit_countedInSpending() = runBlocking {
        val userId = "user_dir_1"
        fakeRepository.insertTransaction(
            TransactionEntity(
                transactionId = "tx_deb_success",
                amountPaise = 50000L, // ₹500.00
                recipientName = "Swiggy",
                paymentMethod = PaymentMethod.UPI,
                category = "Food",
                transactionDate = 1738368000000L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = userId,
                direction = TransactionDirection.DEBIT
            )
        )

        val spending = fakeRepository.getTotalSpendingByUserFlow(userId).first()
        assertEquals(50000L, spending)
    }

    @Test
    fun direction_successfulCredit_strictlyExcludedFromSpending() = runBlocking {
        val userId = "user_dir_2"
        fakeRepository.insertTransaction(
            TransactionEntity(
                transactionId = "tx_cred_success",
                amountPaise = 7500000L, // ₹75,000.00
                recipientName = "Employer Inc",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                category = "Income",
                transactionDate = 1738368000000L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.CSV_IMPORT,
                userId = userId,
                direction = TransactionDirection.CREDIT
            )
        )

        val spending = fakeRepository.getTotalSpendingByUserFlow(userId).first()
        assertEquals("Credits must never inflate spending totals", 0L, spending)
    }

    @Test
    fun direction_pendingDebit_excludedFromSpending() = runBlocking {
        val userId = "user_dir_3"
        fakeRepository.insertTransaction(
            TransactionEntity(
                transactionId = "tx_deb_pending",
                amountPaise = 30000L,
                recipientName = "Store",
                paymentMethod = PaymentMethod.UPI,
                category = "Shopping",
                transactionDate = 1738368000000L,
                transactionStatus = TransactionStatus.PENDING,
                transactionSource = TransactionSource.UPI_INTENT,
                userId = userId,
                direction = TransactionDirection.DEBIT
            )
        )

        val spending = fakeRepository.getTotalSpendingByUserFlow(userId).first()
        assertEquals(0L, spending)
    }

    @Test
    fun direction_failedDebit_excludedFromSpending() = runBlocking {
        val userId = "user_dir_4"
        fakeRepository.insertTransaction(
            TransactionEntity(
                transactionId = "tx_deb_failed",
                amountPaise = 20000L,
                recipientName = "Store",
                paymentMethod = PaymentMethod.UPI,
                category = "Shopping",
                transactionDate = 1738368000000L,
                transactionStatus = TransactionStatus.FAILED,
                transactionSource = TransactionSource.UPI_INTENT,
                userId = userId,
                direction = TransactionDirection.DEBIT
            )
        )

        val spending = fakeRepository.getTotalSpendingByUserFlow(userId).first()
        assertEquals(0L, spending)
    }

    @Test
    fun direction_mixedTransactions_onlySuccessfulDebitsAggregated() = runBlocking {
        val userId = "user_dir_mixed"
        fakeRepository.insertTransactions(
            listOf(
                TransactionEntity(
                    transactionId = "m_1",
                    amountPaise = 100000L, // ₹1,000 SUCCESS DEBIT -> counts
                    recipientName = "Grocery",
                    paymentMethod = PaymentMethod.CARD,
                    category = "Groceries",
                    transactionDate = 1L,
                    transactionStatus = TransactionStatus.SUCCESS,
                    transactionSource = TransactionSource.CSV_IMPORT,
                    userId = userId,
                    direction = TransactionDirection.DEBIT
                ),
                TransactionEntity(
                    transactionId = "m_2",
                    amountPaise = 5000000L, // ₹50,000 SUCCESS CREDIT -> MUST NOT count
                    recipientName = "Salary",
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    category = "Salary",
                    transactionDate = 2L,
                    transactionStatus = TransactionStatus.SUCCESS,
                    transactionSource = TransactionSource.CSV_IMPORT,
                    userId = userId,
                    direction = TransactionDirection.CREDIT
                ),
                TransactionEntity(
                    transactionId = "m_3",
                    amountPaise = 45000L, // ₹450 PENDING DEBIT -> MUST NOT count
                    recipientName = "Pending Tea",
                    paymentMethod = PaymentMethod.UPI,
                    category = "Food",
                    transactionDate = 3L,
                    transactionStatus = TransactionStatus.PENDING,
                    transactionSource = TransactionSource.MANUAL,
                    userId = userId,
                    direction = TransactionDirection.DEBIT
                ),
                TransactionEntity(
                    transactionId = "m_4",
                    amountPaise = 80000L, // ₹800 FAILED DEBIT -> MUST NOT count
                    recipientName = "Failed Booking",
                    paymentMethod = PaymentMethod.UPI,
                    category = "Travel",
                    transactionDate = 4L,
                    transactionStatus = TransactionStatus.FAILED,
                    transactionSource = TransactionSource.UPI_INTENT,
                    userId = userId,
                    direction = TransactionDirection.DEBIT
                )
            )
        )

        val totalSpending = fakeRepository.getTotalSpendingByUserFlow(userId).first()
        assertEquals("Only confirmed SUCCESS DEBITs count toward total spending", 100000L, totalSpending)
    }

    // =========================================================================
    // 2. FIX B: CONCURRENCY SAFETY & DEDUPLICATION
    // =========================================================================

    @Test
    fun concurrency_simultaneousImportOperations_preventDuplicateInsertion() = runBlocking {
        val userId = "user_concurrent_test"
        val csv = """
            Date,Narration,Ref No,Debit,Credit
            10/02/2026,UPI-Swiggy-401234567890,401234567890,450.00,
            10/02/2026,Uber Trip Ride,401234567891,220.00,
        """.trimIndent()

        // Two independent parsed batches with identical candidates
        val parseResult1 = importRepository.parseStringAndDetectDuplicates(csv, userId)
        val parseResult2 = importRepository.parseStringAndDetectDuplicates(csv, userId)

        // Launch concurrent saves simultaneously using async on Default dispatcher
        val deferred1 = async(Dispatchers.Default) {
            importRepository.saveImportedTransactions(parseResult1.transactions, userId, "statement_a.csv")
        }
        val deferred2 = async(Dispatchers.Default) {
            importRepository.saveImportedTransactions(parseResult2.transactions, userId, "statement_b.csv")
        }

        val results = awaitAll(deferred1, deferred2)
        val totalImported = results.sumOf { it.successfullyImported }
        val totalSkipped = results.sumOf { it.duplicatesSkipped }

        assertEquals("Exactly 2 unique transactions must be imported across both simultaneous requests", 2, totalImported)
        assertEquals("Exactly 2 duplicates must be skipped across both simultaneous requests", 2, totalSkipped)
        assertEquals("Database must contain exactly 2 unique records", 2, fakeRepository.countTransactionsByUser(userId))
    }

    @Test
    fun concurrency_repeatedImport_secondImportIdempotentlySkips() = runBlocking {
        val userId = "user_repeated_test"
        val csv = """
            Date,Description,Debit,Credit
            01/01/2026,Supermarket Grocery,1200.00,
            02/01/2026,Coffee Shop,150.00,
        """.trimIndent()

        val pass1 = importRepository.parseStringAndDetectDuplicates(csv, userId)
        val res1 = importRepository.saveImportedTransactions(pass1.transactions, userId, "jan.csv")
        assertEquals(2, res1.successfullyImported)
        assertEquals(0, res1.duplicatesSkipped)

        val pass2 = importRepository.parseStringAndDetectDuplicates(csv, userId)
        assertTrue(pass2.transactions.all { it.duplicateStatus == DuplicateStatus.EXACT_MATCH_DATABASE })
        assertTrue(pass2.transactions.none { it.isSelected })

        val res2 = importRepository.saveImportedTransactions(pass2.transactions, userId, "jan.csv")
        assertEquals(0, res2.successfullyImported)
        assertEquals(2, res2.duplicatesSkipped)
        assertEquals(2, fakeRepository.countTransactionsByUser(userId))
    }

    @Test
    fun concurrency_differentUsers_isolatedWithoutCollisions() = runBlocking {
        val csv = """
            Date,Description,Debit,Credit
            05/01/2026,Common Grocery Store,500.00,
        """.trimIndent()

        val parseUserA = importRepository.parseStringAndDetectDuplicates(csv, "user_A")
        val parseUserB = importRepository.parseStringAndDetectDuplicates(csv, "user_B")

        val resA = importRepository.saveImportedTransactions(parseUserA.transactions, "user_A", "stmt.csv")
        val resB = importRepository.saveImportedTransactions(parseUserB.transactions, "user_B", "stmt.csv")

        assertEquals(1, resA.successfullyImported)
        assertEquals(1, resB.successfullyImported)
        assertEquals(1, fakeRepository.countTransactionsByUser("user_A"))
        assertEquals(1, fakeRepository.countTransactionsByUser("user_B"))
    }

    // =========================================================================
    // 3. FIX C: TIMEZONE INVARIANCE
    // =========================================================================

    @Test
    fun timezone_identicalFingerprintAcrossISTandUTCandEST() {
        val defaultTz = TimeZone.getDefault()
        try {
            val dateStr = "15/01/2026"
            val userId = "user_tz"
            val amountPaise = 50000L
            val direction = TransactionDirection.DEBIT
            val desc = "Starbucks Indiranagar"
            val recipient = "Starbucks"

            // 1. In Asia/Kolkata (IST = UTC+05:30)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val parsedIST = DateFormats.parseDate(dateStr)
            assertNotNull(parsedIST)
            assertEquals("15 Jan 2026", parsedIST?.second)
            val fpIST = TransactionFingerprintGenerator.generateFingerprint(
                userId = userId,
                dateMillis = parsedIST!!.first,
                amountPaise = amountPaise,
                direction = direction,
                description = desc,
                recipientName = recipient
            )

            // 2. In UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val parsedUTC = DateFormats.parseDate(dateStr)
            assertNotNull(parsedUTC)
            assertEquals("15 Jan 2026", parsedUTC?.second)
            val fpUTC = TransactionFingerprintGenerator.generateFingerprint(
                userId = userId,
                dateMillis = parsedUTC!!.first,
                amountPaise = amountPaise,
                direction = direction,
                description = desc,
                recipientName = recipient
            )

            // 3. In America/New_York (EST = UTC-05:00)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val parsedEST = DateFormats.parseDate(dateStr)
            assertNotNull(parsedEST)
            assertEquals("15 Jan 2026", parsedEST?.second)
            val fpEST = TransactionFingerprintGenerator.generateFingerprint(
                userId = userId,
                dateMillis = parsedEST!!.first,
                amountPaise = amountPaise,
                direction = direction,
                description = desc,
                recipientName = recipient
            )

            // Assert exact equality
            assertEquals("Calendar date milliseconds must be identical across timezones", parsedIST.first, parsedUTC.first)
            assertEquals("Calendar date milliseconds must be identical across timezones", parsedUTC.first, parsedEST.first)
            assertEquals("Fingerprints generated in IST and UTC must be identical", fpIST, fpUTC)
            assertEquals("Fingerprints generated in UTC and EST must be identical", fpUTC, fpEST)
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun timezone_midnightBoundaryNeverShiftsToPreviousDay() {
        val defaultTz = TimeZone.getDefault()
        try {
            // Test specifically on Year boundary at midnight
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val parsedNewYear = DateFormats.parseDate("01/01/2026")
            assertNotNull(parsedNewYear)
            assertEquals("01 Jan 2026", parsedNewYear?.second)

            val fp = TransactionFingerprintGenerator.generateFingerprint(
                userId = "test_user",
                dateMillis = parsedNewYear!!.first,
                amountPaise = 10000L,
                direction = TransactionDirection.DEBIT,
                description = "New Year Lunch",
                recipientName = "Restaurant"
            )
            assertNotNull(fp)
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    // =========================================================================
    // 4. FIX D: UNKNOWN DIRECTION HANDLING (NEEDS_REVIEW)
    // =========================================================================

    @Test
    fun unknownDirection_explicitDebitSign_markedValidDebit() {
        val csv = """
            Date,Description,Amount
            15/02/2026,Amazon Shopping,-1499.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        val txn = result.transactions[0]
        assertEquals(TransactionDirection.DEBIT, txn.direction)
        assertEquals(RowValidationStatus.VALID, txn.validationStatus)
        assertTrue(txn.isSelected)
    }

    @Test
    fun unknownDirection_explicitCreditIndicator_markedValidCredit() {
        val csv = """
            Transaction Date,Particulars,Amount,Type
            15/02/2026,Cashback from PayTM,100.00,CR
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        val txn = result.transactions[0]
        assertEquals(TransactionDirection.CREDIT, txn.direction)
        assertEquals(RowValidationStatus.VALID, txn.validationStatus)
        assertFalse("Credit transactions must not be auto-selected", txn.isSelected)
    }

    @Test
    fun unknownDirection_ambiguousAmountWithoutTypeOrKeyword_markedNeedsReview() {
        val csv = """
            Date,Description,Amount
            20/02/2026,Transfer 12345678,5000.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        val txn = result.transactions[0]
        assertEquals(RowValidationStatus.NEEDS_REVIEW, txn.validationStatus)
        assertFalse("Ambiguous rows requiring review must never be auto-selected", txn.isSelected)
        assertTrue(txn.validationError?.contains("determined automatically") == true)
    }

    @Test
    fun unknownDirection_conflictingDebitAndCredit_markedInvalid() {
        val csv = """
            Date,Description,Debit,Credit
            25/02/2026,Corrupted Row,500.00,500.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        val txn = result.transactions[0]
        assertEquals(RowValidationStatus.INVALID, txn.validationStatus)
        assertFalse(txn.isSelected)
        assertTrue(txn.validationError?.contains("conflicting debit and credit") == true)
    }

    // =========================================================================
    // 5. BANK FORMAT COMPATIBILITY
    // =========================================================================

    @Test
    fun bankCompatibility_hdfcBankFormat() {
        val csv = """
            Date,Narration,Chq/Ref No,Value Date,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/03/2026,UPI-SWIGGY-swiggy@axis-403212345678,403212345678,01/03/2026,380.00,,50000.00
            02/03/2026,NEFT CR-SALARY,NEFT99887766,02/03/2026,,80000.00,130000.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_hdfc")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(38000L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(8000000L, result.transactions[1].amountPaise)
    }

    @Test
    fun bankCompatibility_sbiBankFormat() {
        val csv = """
            State Bank of India Account Statement
            Account No: 1234567890
            
            Txn Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            05/03/2026,UPI/DR/401234567890/Zomato/HDFC/Pay,401234567890,250.00,,25000.00
            06/03/2026,INTEREST CREDIT,,,125.00,25125.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_sbi")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(25000L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(12500L, result.transactions[1].amountPaise)
    }

    @Test
    fun bankCompatibility_iciciBankFormat() {
        val csv = """
            Transaction Date,Transaction Remarks,Cheque Number,Withdrawal Amount (INR ),Deposit Amount (INR ),Balance (INR )
            10/03/2026,POS 9988 SHELL FUEL,CHQ001,1500.00,,40000.00
            11/03/2026,DIVIDEND CREDIT,CHQ002,,250.00,40250.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_icici")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(150000L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(25000L, result.transactions[1].amountPaise)
    }

    @Test
    fun bankCompatibility_axisBankFormat() {
        val csv = """
            Tran Date,Particulars,CHQ NO,Debit,Credit,Balance
            12/03/2026,UPI-Uber-123456789012,123456789012,350.00,,30000.00
            13/03/2026,REFUND AIRLINE,REF001,,4500.00,34500.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_axis")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(35000L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(450000L, result.transactions[1].amountPaise)
    }

    @Test
    fun bankCompatibility_kotakBankFormat() {
        val csv = """
            Date,Transaction Details,Chq / Ref No.,Dr / Cr,Amount,Balance
            15/03/2026,Netflix Entertainment,REF8899,DR,649.00,18000.00
            16/03/2026,Cash Deposit,REF8900,CR,5000.00,23000.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_kotak")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(64900L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(500000L, result.transactions[1].amountPaise)
    }

    @Test
    fun bankCompatibility_genericCsvWithSeparateDebitCredit() {
        val csv = """
            Date,Description,Debit,Credit
            20/03/2026,Grocery Mart,1250.50,
            21/03/2026,Client Payment,,15000.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_generic")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)
        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(125050L, result.transactions[0].amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(1500000L, result.transactions[1].amountPaise)
    }
}
