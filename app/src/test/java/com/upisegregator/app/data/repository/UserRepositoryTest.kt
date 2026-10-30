package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.UserDao
import com.upisegregator.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying registration, login, duplicate email rejection, and password validation.
 */
class UserRepositoryTest {

    private lateinit var fakeUserDao: FakeUserDao
    private lateinit var userRepository: UserRepository

    private class FakeUserDao : UserDao {
        val users = mutableListOf<UserEntity>()

        override suspend fun insertUser(user: UserEntity) {
            users.add(user)
        }

        override suspend fun getUserByEmail(email: String): UserEntity? {
            return users.find { it.email.equals(email.trim(), ignoreCase = true) }
        }

        override suspend fun getUserById(userId: String): UserEntity? {
            return users.find { it.userId == userId }
        }

        override suspend fun getUserCount(): Int = users.size

        override fun getAllUsersFlow(): Flow<List<UserEntity>> = flowOf(users)
    }

    @Before
    fun setUp() {
        fakeUserDao = FakeUserDao()
        userRepository = UserRepositoryImpl(fakeUserDao)
    }

    @Test
    fun registerUser_successfulRegistration() = runBlocking {
        val result = userRepository.registerUser(
            fullName = "Dinesh Patel",
            email = "dinesh@example.com",
            passwordPlain = "SecurePass123"
        )

        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("Dinesh Patel", user?.fullName)
        assertEquals("dinesh@example.com", user?.email)
        assertNotNull(user?.passwordHash)
        assertNotNull(user?.passwordSalt)
        // Plaintext password must NOT be stored
        assertTrue(user?.passwordHash != "SecurePass123")
    }

    @Test
    fun registerUser_duplicateEmail_isRejected() = runBlocking {
        userRepository.registerUser("First User", "duplicate@test.com", "Password123")
        val duplicateResult = userRepository.registerUser("Second User", "duplicate@test.com", "OtherPassword456")

        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    @Test
    fun registerUser_duplicateEmailDifferentCase_isRejected() = runBlocking {
        userRepository.registerUser("First User", "case@test.com", "Password123")
        val duplicateResult = userRepository.registerUser("Second User", "CASE@TEST.COM", "OtherPassword456")

        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    @Test
    fun authenticateUser_successfulLogin() = runBlocking {
        userRepository.registerUser("Test User", "login@test.com", "MyCorrectPassword")

        val authResult = userRepository.authenticateUser("login@test.com", "MyCorrectPassword")
        assertTrue(authResult.isSuccess)
        assertEquals("login@test.com", authResult.getOrNull()?.email)
    }

    @Test
    fun authenticateUser_incorrectPassword_isRejected() = runBlocking {
        userRepository.registerUser("Test User", "login@test.com", "MyCorrectPassword")

        val authResult = userRepository.authenticateUser("login@test.com", "WrongPassword")
        assertTrue(authResult.isFailure)
        assertTrue(authResult.exceptionOrNull()?.message?.contains("password", ignoreCase = true) == true)
    }

    @Test
    fun authenticateUser_unknownEmail_isRejected() = runBlocking {
        val authResult = userRepository.authenticateUser("unknown@test.com", "SomePassword")
        assertTrue(authResult.isFailure)
        assertTrue(authResult.exceptionOrNull()?.message?.contains("No account found", ignoreCase = true) == true)
    }

    @Test
    fun registerUser_shortPassword_isRejected() = runBlocking {
        val result = userRepository.registerUser("Short Pass", "short@test.com", "123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("6 characters", ignoreCase = true) == true)
    }
}
