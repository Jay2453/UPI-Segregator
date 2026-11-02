package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun insertTransaction(transaction: TransactionEntity)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    suspend fun updateTransaction(transaction: TransactionEntity)
    suspend fun deleteTransaction(transaction: TransactionEntity)
    suspend fun deleteTransactionById(transactionId: String, userId: String)
    fun getTransactionsByUserFlow(userId: String): Flow<List<TransactionEntity>>
    suspend fun getTransactionById(transactionId: String, userId: String): TransactionEntity?
    suspend fun getTransactionByIdOnly(transactionId: String): TransactionEntity?
    fun getTransactionsByRecipientFlow(recipientId: String, userId: String): Flow<List<TransactionEntity>>
    fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long, userId: String): Flow<List<TransactionEntity>>
    fun getTotalSpendingByUserFlow(userId: String): Flow<Long>
    fun countTransactionsByUserFlow(userId: String): Flow<Int>
    suspend fun countTransactionsByUser(userId: String): Int
    suspend fun deleteTransactionsByUser(userId: String)
    suspend fun getTransactionByExternalReference(externalReference: String, userId: String): TransactionEntity?
    suspend fun getTransactionByFingerprint(fingerprint: String, userId: String): TransactionEntity?
    suspend fun getTransactionsByExternalReferences(references: List<String>, userId: String): List<TransactionEntity>
    suspend fun getTransactionsByFingerprints(fingerprints: List<String>, userId: String): List<TransactionEntity>
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

    override suspend fun deleteTransactionById(transactionId: String, userId: String) {
        transactionDao.deleteTransactionById(transactionId, userId)
    }

    override fun getTransactionsByUserFlow(userId: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByUserFlow(userId)
    }

    override suspend fun getTransactionById(transactionId: String, userId: String): TransactionEntity? {
        return transactionDao.getTransactionById(transactionId, userId)
    }

    override suspend fun getTransactionByIdOnly(transactionId: String): TransactionEntity? {
        return transactionDao.getTransactionByIdOnly(transactionId)
    }

    override fun getTransactionsByRecipientFlow(recipientId: String, userId: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByRecipientFlow(recipientId, userId)
    }

    override fun getTransactionsByDateRangeFlow(startDate: Long, endDate: Long, userId: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByDateRangeFlow(startDate, endDate, userId)
    }

    override fun getTotalSpendingByUserFlow(userId: String): Flow<Long> {
        return transactionDao.getTotalSpendingByUserFlow(userId)
    }

    override fun countTransactionsByUserFlow(userId: String): Flow<Int> {
        return transactionDao.countTransactionsByUserFlow(userId)
    }

    override suspend fun countTransactionsByUser(userId: String): Int {
        return transactionDao.countTransactionsByUser(userId)
    }

    override suspend fun deleteTransactionsByUser(userId: String) {
        transactionDao.deleteTransactionsByUser(userId)
    }

    override suspend fun getTransactionByExternalReference(externalReference: String, userId: String): TransactionEntity? {
        if (externalReference.isBlank()) return null
        return transactionDao.getTransactionByExternalReference(externalReference, userId)
    }

    override suspend fun getTransactionByFingerprint(fingerprint: String, userId: String): TransactionEntity? {
        if (fingerprint.isBlank()) return null
        return transactionDao.getTransactionByFingerprint(fingerprint, userId)
    }

    override suspend fun getTransactionsByExternalReferences(references: List<String>, userId: String): List<TransactionEntity> {
        val nonBlank = references.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()
        return transactionDao.getTransactionsByExternalReferences(nonBlank, userId)
    }

    override suspend fun getTransactionsByFingerprints(fingerprints: List<String>, userId: String): List<TransactionEntity> {
        val nonBlank = fingerprints.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()
        return transactionDao.getTransactionsByFingerprints(nonBlank, userId)
    }
}
