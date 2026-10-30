package com.upisegregator.app.data.demo

import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Coordinates demo data seeding, deduplication, and isolation.
 *
 * Rules:
 * - Demo records belong strictly to [DEMO_USER_ID] ("demo_user").
 * - Never seeds duplicate demo transactions if demo data already exists.
 * - Only confirmed SUCCESS demo transactions contribute to demo spending totals.
 * - Resetting demo data deletes ONLY records where `userId = DEMO_USER_ID`.
 * - Real user transactions are 100% isolated and never touched during demo operations.
 */
class DemoDataManager(
    private val transactionDao: TransactionDao,
    private val recipientDao: RecipientDao,
    private val categoryDao: CategoryDao
) {

    companion object {
        const val DEMO_USER_ID = "demo_user"
    }

    val demoTransactionsFlow: Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByUserFlow(DEMO_USER_ID)

    val demoSpendingTotalFlow: Flow<Long> =
        transactionDao.getTotalSpendingByUserFlow(DEMO_USER_ID)

    val demoTransactionCountFlow: Flow<Int> =
        transactionDao.countTransactionsByUserFlow(DEMO_USER_ID)

    /**
     * Seeds demo categories, recipients, and transactions only if they are not already present.
     * Prevents duplicate inserts across app restarts.
     */
    suspend fun seedDemoDataIfNotPresent(): Boolean = withContext(Dispatchers.IO) {
        val existingCount = transactionDao.countTransactionsByUser(DEMO_USER_ID)
        if (existingCount > 0) {
            return@withContext false // Already seeded, skip duplicate insertion
        }

        // Insert categories if empty
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategories(DemoDataGenerator.demoCategories)
        }

        // Insert demo recipients if not present for demo_user
        if (recipientDao.getRecipientCountByUser(DEMO_USER_ID) == 0) {
            recipientDao.insertRecipients(DemoDataGenerator.demoRecipients)
        }

        // Insert fresh demo transactions (all have userId = DEMO_USER_ID)
        val transactions = DemoDataGenerator.generateDemoTransactions()
        transactionDao.insertTransactions(transactions)
        return@withContext true
    }

    /**
     * Resets demo data by deleting only records belonging to DEMO_USER_ID.
     * Real user records remain completely untouched.
     */
    suspend fun resetDemoData(): Unit = withContext(Dispatchers.IO) {
        transactionDao.deleteTransactionsByUser(DEMO_USER_ID)
        recipientDao.deleteRecipientsByUser(DEMO_USER_ID)
        seedDemoDataIfNotPresent()
    }
}
