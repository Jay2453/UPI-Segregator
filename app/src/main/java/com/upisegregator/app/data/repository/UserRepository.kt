package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.UserDao
import com.upisegregator.app.data.local.entity.UserEntity
import com.upisegregator.app.util.PasswordSecurity
import java.util.UUID

interface UserRepository {
    suspend fun registerUser(fullName: String, email: String, passwordPlain: String): Result<UserEntity>
    suspend fun authenticateUser(email: String, passwordPlain: String): Result<UserEntity>
    suspend fun getUserById(userId: String): UserEntity?
    suspend fun getUserByEmail(email: String): UserEntity?
}

class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun registerUser(
        fullName: String,
        email: String,
        passwordPlain: String
    ): Result<UserEntity> {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase()

        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Name must be at least 2 characters"))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (passwordPlain.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        // Duplicate email rejection
        val existing = userDao.getUserByEmail(cleanEmail)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("An account with this email already exists"))
        }

        // Salted PBKDF2 hashing
        val hashResult = PasswordSecurity.hashPassword(passwordPlain)
        val user = UserEntity(
            userId = UUID.randomUUID().toString(),
            fullName = cleanName,
            email = cleanEmail,
            passwordHash = hashResult.hashHex,
            passwordSalt = hashResult.saltHex,
            createdAt = System.currentTimeMillis()
        )

        userDao.insertUser(user)
        return Result.success(user)
    }

    override suspend fun authenticateUser(
        email: String,
        passwordPlain: String
    ): Result<UserEntity> {
        val cleanEmail = email.trim().lowercase()
        val user = userDao.getUserByEmail(cleanEmail)
            ?: return Result.failure(IllegalArgumentException("No account found with this email"))

        val isPasswordValid = PasswordSecurity.verifyPassword(
            password = passwordPlain,
            storedHashHex = user.passwordHash,
            storedSaltHex = user.passwordSalt
        )

        return if (isPasswordValid) {
            Result.success(user)
        } else {
            Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }
    }

    override suspend fun getUserById(userId: String): UserEntity? {
        return userDao.getUserById(userId)
    }

    override suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email.trim().lowercase())
    }
}
