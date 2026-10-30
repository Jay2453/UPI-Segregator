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

    @Query("DELETE FROM transactions WHERE transactionId = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionSource = :source ORDER BY transactionDate DESC")
    fun getTransactionsBySourceFlow(source: TransactionSource): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE recipientId = :recipientId ORDER BY transactionDate DESC")
    fun getTransactionsByRecipientFlow(recipientId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionDate BETWEEN :startDate AND :endDate ORDER BY transactionDate DESC")
    fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    /**
     * CRITICAL INVARIANT: Only confirmed SUCCESS transactions count towards spending totals in V1.
     * PENDING, FAILED, CANCELLED, and UNKNOWN transactions are strictly excluded from spending calculations.
     */
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE transactionStatus = 'SUCCESS'")
    fun getTotalSpendingFlow(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE transactionStatus = 'SUCCESS' AND transactionSource = :source")
    fun getTotalSpendingBySourceFlow(source: TransactionSource): Flow<Long>

    @Query("SELECT COUNT(*) FROM transactions WHERE transactionSource = :source")
    fun countTransactionsBySourceFlow(source: TransactionSource): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE transactionSource = :source")
    suspend fun countTransactionsBySource(source: TransactionSource): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun countAllTransactionsFlow(): Flow<Int>

    @Query("DELETE FROM transactions WHERE transactionSource = :source")
    suspend fun deleteTransactionsBySource(source: TransactionSource)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
