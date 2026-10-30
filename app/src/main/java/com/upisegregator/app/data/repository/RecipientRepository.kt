package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

interface RecipientRepository {
    suspend fun insertRecipient(recipient: RecipientEntity)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)
    suspend fun updateRecipient(recipient: RecipientEntity)
    suspend fun deleteRecipient(recipient: RecipientEntity)
    fun getAllRecipientsFlow(): Flow<List<RecipientEntity>>
    suspend fun getRecipientById(recipientId: String): RecipientEntity?
    suspend fun getRecipientCount(): Int
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

    override fun getAllRecipientsFlow(): Flow<List<RecipientEntity>> {
        return recipientDao.getAllRecipientsFlow()
    }

    override suspend fun getRecipientById(recipientId: String): RecipientEntity? {
        return recipientDao.getRecipientById(recipientId)
    }

    override suspend fun getRecipientCount(): Int {
        return recipientDao.getRecipientCount()
    }
}
