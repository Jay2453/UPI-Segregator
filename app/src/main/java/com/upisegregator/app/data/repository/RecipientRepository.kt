package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

interface RecipientRepository {
    suspend fun insertRecipient(recipient: RecipientEntity)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)
    suspend fun updateRecipient(recipient: RecipientEntity)
    suspend fun deleteRecipient(recipient: RecipientEntity)
    fun getRecipientsByUserFlow(userId: String): Flow<List<RecipientEntity>>
    suspend fun getRecipientById(recipientId: String, userId: String): RecipientEntity?
    suspend fun getRecipientCountByUser(userId: String): Int
    suspend fun deleteRecipientsByUser(userId: String)
}

class RecipientRepositoryImpl(
    private val recipientDao: RecipientDao
) : RecipientRepository {

    override suspend fun insertRecipient(recipient: RecipientEntity) {
        recipientDao.insertRecipient(recipient)
    }

    override suspend fun insertRecipients(recipients: List<RecipientEntity>) {
        recipientDao.insertRecipients(recipients)
    }

    override suspend fun updateRecipient(recipient: RecipientEntity) {
        recipientDao.updateRecipient(recipient)
    }

    override suspend fun deleteRecipient(recipient: RecipientEntity) {
        recipientDao.deleteRecipient(recipient)
    }

    override fun getRecipientsByUserFlow(userId: String): Flow<List<RecipientEntity>> {
        return recipientDao.getRecipientsByUserFlow(userId)
    }

    override suspend fun getRecipientById(recipientId: String, userId: String): RecipientEntity? {
        return recipientDao.getRecipientById(recipientId, userId)
    }

    override suspend fun getRecipientCountByUser(userId: String): Int {
        return recipientDao.getRecipientCountByUser(userId)
    }

    override suspend fun deleteRecipientsByUser(userId: String) {
        recipientDao.deleteRecipientsByUser(userId)
    }
}
