package com.upisegregator.app.data

import android.content.Context
import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.local.database.AppDatabase
import com.upisegregator.app.data.repository.CategoryRepository
import com.upisegregator.app.data.repository.CategoryRepositoryImpl
import com.upisegregator.app.data.repository.RecipientRepository
import com.upisegregator.app.data.repository.RecipientRepositoryImpl
import com.upisegregator.app.data.repository.TransactionRepository
import com.upisegregator.app.data.repository.TransactionRepositoryImpl

/**
 * Dependency injection container for application-level singletons.
 */
interface AppContainer {
    val database: AppDatabase
    val transactionRepository: TransactionRepository
    val recipientRepository: RecipientRepository
    val categoryRepository: CategoryRepository
    val demoDataManager: DemoDataManager
}

class AppContainerImpl(context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(database.transactionDao())
    }

    override val recipientRepository: RecipientRepository by lazy {
        RecipientRepositoryImpl(database.recipientDao())
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val demoDataManager: DemoDataManager by lazy {
        DemoDataManager(
            transactionDao = database.transactionDao(),
            recipientDao = database.recipientDao(),
            categoryDao = database.categoryDao()
        )
    }
}
