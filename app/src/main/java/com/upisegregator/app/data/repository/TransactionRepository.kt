package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun insertTransaction(transaction: TransactionEntity)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    suspend fun updateTransaction(transaction: TransactionEntity)
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun deleteTransactionById(transactionId: String)
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>
    suspend fun getTransactionById(transactionId: String): TransactionEntity?
    fun getTransactionsBySourceFlow(source: TransactionSource): Flow<List<TransactionEntity>>
    fun getTransactionsByRecipientFlow(recipientId: String): Flow<List<TransactionEntity>>
    fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long): Flow<List<TransactionEntity>>
    fun getTotalSpendingFlow(): Flow<Long>
    fun getTotalSpendingBySourceFlow(source: TransactionSource): Flow<Long>
    fun countTransactionsBySourceFlow(source: TransactionSource): Flow<Int>
    suspend fun countTransactionsBySource(source: TransactionSource): Int
    suspend fun deleteTransactionsBySource(source: TransactionSource)
}

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao
) : TransactionRepository {

    override suspend fun insertTransaction(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction)
    }

    override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        transactionDao.insertTransactions(transactions)
    }

    override suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    override suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    override suspend fun deleteTransactionById(transactionId: String) {
        transactionDao.deleteTransactionById(transactionId)
    }

    override fun getAllTransactionsFlow(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactionsFlow()
    }

    override suspend fun getTransactionById(transactionId: String): TransactionEntity? {
        return transactionDao.getTransactionById(transactionId)
    }

    override fun getTransactionsBySourceFlow(source: TransactionSource): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsBySourceFlow(source)
    }

    override fun getTransactionsByRecipientFlow(recipientId: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByRecipientFlow(recipientId)
    }

    override fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByDateRangeFlow(startDate, endDate)
    }

    override fun getTotalSpendingFlow(): Flow<Long> {
        return transactionDao.getTotalSpendingFlow()
    }

    override fun getTotalSpendingBySourceFlow(source: TransactionSource): Flow<Long> {
        return transactionDao.getTotalSpendingBySourceFlow(source)
    }

    override fun countTransactionsBySourceFlow(source: TransactionSource): Flow<Int> {
        return transactionDao.countTransactionsBySourceFlow(source)
    }

    override suspend fun countTransactionsBySource(source: TransactionSource): Int {
        return transactionDao.countTransactionsBySource(source)
    }

    override suspend fun deleteTransactionsBySource(source: TransactionSource) {
        transactionDao.deleteTransactionsBySource(source)
    }
}
