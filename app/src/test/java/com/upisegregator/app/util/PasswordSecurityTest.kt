package com.upisegregator.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for local salted PBKDF2 password security.
 */
class PasswordSecurityTest {

    @Test
    fun hashPassword_generatesValidSaltAndHash() {
        val result = PasswordSecurity.hashPassword("SuperSecret123!")
        assertNotNull(result.hashHex)
        assertNotNull(result.saltHex)
        assertTrue(result.hashHex.isNotBlank())
        assertTrue(result.saltHex.isNotBlank())
    }

    @Test
    fun hashPassword_producesUniqueSaltsAndHashesForSamePassword() {
        val hash1 = PasswordSecurity.hashPassword("MyPassword123")
        val hash2 = PasswordSecurity.hashPassword("MyPassword123")

        assertNotEquals("Each password hash must have a unique random salt", hash1.saltHex, hash2.saltHex)
        assertNotEquals("Different salts must produce different hashes", hash1.hashHex, hash2.hashHex)
    }

    @Test
    fun verifyPassword_correctPassword_returnsTrue() {
        val hashResult = PasswordSecurity.hashPassword("CorrectHorseBatteryStaple")
        val isValid = PasswordSecurity.verifyPassword(
            password = "CorrectHorseBatteryStaple",
            storedHashHex = hashResult.hashHex,
            storedSaltHex = hashResult.saltHex
        )
        assertTrue("Matching password must verify to true", isValid)
    }

    @Test
    fun verifyPassword_incorrectPassword_returnsFalse() {
        val hashResult = PasswordSecurity.hashPassword("CorrectHorseBatteryStaple")
        val isValid = PasswordSecurity.verifyPassword(
            password = "WrongPassword!",
            storedHashHex = hashResult.hashHex,
            storedSaltHex = hashResult.saltHex
        )
        assertFalse("Mismatched password must verify to false", isValid)
    }

    @Test
    fun verifyPassword_corruptedSalt_returnsFalseSafely() {
        val hashResult = PasswordSecurity.hashPassword("TestPassword")
        val isValid = PasswordSecurity.verifyPassword(
            password = "TestPassword",
            storedHashHex = hashResult.hashHex,
            storedSaltHex = "corrupted"
        )
        assertFalse("Invalid salt hex format must safely fail without crashing", isValid)
    }
}
