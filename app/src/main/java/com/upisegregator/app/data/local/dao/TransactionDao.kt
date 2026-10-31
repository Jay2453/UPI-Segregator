package com.upisegregator.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId AND userId = :userId")
    suspend fun deleteTransactionById(transactionId: String, userId: String)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY transactionDate DESC")
    fun getTransactionsByUserFlow(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId AND userId = :userId LIMIT 1")
    suspend fun getTransactionById(transactionId: String, userId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransactionByIdOnly(transactionId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE recipientId = :recipientId AND userId = :userId ORDER BY transactionDate DESC")
    fun getTransactionsByRecipientFlow(recipientId: String, userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND transactionDate BETWEEN :startDate AND :endDate ORDER BY transactionDate DESC")
    fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long, userId: String): Flow<List<TransactionEntity>>

    /**
     * CRITICAL INVARIANT: Only confirmed SUCCESS transactions count towards spending totals in V1.
     * Filtered strictly by userId to guarantee isolation between Demo Mode and registered users.
     */
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE userId = :userId AND transactionStatus = 'SUCCESS'")
    fun getTotalSpendingByUserFlow(userId: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    fun countTransactionsByUserFlow(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId")
    suspend fun countTransactionsByUser(userId: String): Int

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteTransactionsByUser(userId: String)

    // Legacy / fallback helpers
    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE transactionSource = :source")
    suspend fun deleteTransactionsBySource(source: TransactionSource)

    @Query("SELECT COUNT(*) FROM transactions WHERE transactionSource = :source")
    suspend fun countTransactionsBySource(source: TransactionSource): Int

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE transactionStatus = 'SUCCESS'")
    fun getTotalSpendingFlow(): Flow<Long>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
