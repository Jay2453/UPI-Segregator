package com.upisegregator.app.data.demo

import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Coordinates demo data seeding, deduplication, and isolation.
 *
 * Rules:
 * - Never seeds duplicate demo transactions if demo data already exists.
 * - Only confirmed SUCCESS demo transactions contribute to demo spending totals.
 * - Provides a clean reset operation to restore the demo environment.
 */
class DemoDataManager(
    private val transactionDao: TransactionDao,
    private val recipientDao: RecipientDao,
    private val categoryDao: CategoryDao
) {

    val demoTransactionsFlow: Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsBySourceFlow(TransactionSource.DEMO)

    val demoSpendingTotalFlow: Flow<Long> =
        transactionDao.getTotalSpendingBySourceFlow(TransactionSource.DEMO)

    val demoTransactionCountFlow: Flow<Int> =
        transactionDao.countTransactionsBySourceFlow(TransactionSource.DEMO)

    /**
     * Seeds demo categories, recipients, and transactions only if they are not already present.
     * Prevents duplicate inserts across app restarts.
     */
    suspend fun seedDemoDataIfNotPresent(): Boolean = withContext(Dispatchers.IO) {
        val existingCount = transactionDao.countTransactionsBySource(TransactionSource.DEMO)
        if (existingCount > 0) {
            return@withContext false // Already seeded, skip duplicate insertion
        }

        // Insert categories if empty
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategories(DemoDataGenerator.demoCategories)
        }

        // Insert recipients if empty
        if (recipientDao.getRecipientCount() == 0) {
            recipientDao.insertRecipients(DemoDataGenerator.demoRecipients)
        }

        // Insert fresh demo transactions
        val transactions = DemoDataGenerator.generateDemoTransactions()
        transactionDao.insertTransactions(transactions)
        return@withContext true
    }

    /**
     * Resets demo data by deleting only demo transactions, then re-seeding fresh data.
     * Real user transactions (MANUAL, UPI_INTENT, CSV_IMPORT) remain completely untouched.
     */
    suspend fun resetDemoData(): Unit = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionsBySource(TransactionSource.DEMO)
        seedDemoDataIfNotPresent()
    }
}
