package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun insertCategory(category: CategoryEntity)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>
    suspend fun getCategoryById(categoryId: String): CategoryEntity?
    suspend fun getCategoryCount(): Int
}

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override suspend fun insertCategory(category: CategoryEntity) {
        categoryDao.insertCategory(category)
    }

    override suspend fun insertCategories(categories: List<CategoryEntity>) {
        categoryDao.insertCategories(categories)
    }

    override fun getAllCategoriesFlow(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategoriesFlow()
    }

    override suspend fun getCategoryById(categoryId: String): CategoryEntity? {
        return categoryDao.getCategoryById(categoryId)
    }

    override suspend fun getCategoryCount(): Int {
        return categoryDao.getCategoryCount()
    }
}
