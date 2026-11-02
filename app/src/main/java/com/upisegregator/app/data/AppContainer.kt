package com.upisegregator.app.data

import android.content.Context
import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.local.database.AppDatabase
import com.upisegregator.app.data.repository.CategoryRepository
import com.upisegregator.app.data.repository.CategoryRepositoryImpl
import com.upisegregator.app.data.repository.ManualExpenseRepository
import com.upisegregator.app.data.repository.ManualExpenseRepositoryImpl
import com.upisegregator.app.data.repository.RecipientRepository
import com.upisegregator.app.data.repository.RecipientRepositoryImpl
import com.upisegregator.app.data.repository.TransactionRepository
import com.upisegregator.app.data.repository.TransactionRepositoryImpl
import com.upisegregator.app.data.repository.UserRepository
import com.upisegregator.app.data.repository.UserRepositoryImpl
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.SessionManagerImpl
import com.upisegregator.app.data.upi.UpiAppPreferenceManager

/**
 * Dependency injection container for application-level singletons.
 */
interface AppContainer {
    val database: AppDatabase
    val transactionRepository: TransactionRepository
    val recipientRepository: RecipientRepository
    val categoryRepository: CategoryRepository
    val userRepository: UserRepository
    val manualExpenseRepository: ManualExpenseRepository
    val statementImportRepository: com.upisegregator.app.data.importer.StatementImportRepository
    val sessionManager: SessionManager
    val demoDataManager: DemoDataManager
    val upiAppPreferenceManager: UpiAppPreferenceManager
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

    override val manualExpenseRepository: ManualExpenseRepository by lazy {
        ManualExpenseRepositoryImpl(database.manualExpenseDao())
    }

    override val statementImportRepository: com.upisegregator.app.data.importer.StatementImportRepository by lazy {
        com.upisegregator.app.data.importer.StatementImportRepositoryImpl(transactionRepository)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val userRepository: UserRepository by lazy {
        UserRepositoryImpl(database.userDao())
    }

    override val sessionManager: SessionManager by lazy {
        SessionManagerImpl(context)
    }

    override val demoDataManager: DemoDataManager by lazy {
        DemoDataManager(
            transactionDao = database.transactionDao(),
            recipientDao = database.recipientDao(),
            categoryDao = database.categoryDao()
        )
    }

    override val upiAppPreferenceManager: UpiAppPreferenceManager by lazy {
        UpiAppPreferenceManager(context)
    }
}

