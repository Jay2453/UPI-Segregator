package com.upisegregator.app.util

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local-first password hashing engine using salted PBKDF2 with HMAC-SHA256.
 *
 * Security Invariants:
 * - Passwords are NEVER stored as plaintext.
 * - Each account has a unique 16-byte cryptographically random salt.
 * - 10,000 PBKDF2 iterations are applied to prevent rainbow table attacks.
 * - Password verification uses constant-time byte comparison to prevent timing attacks.
 * - This provides offline-device security; accounts are stored locally and do not sync to any cloud.
 */
object PasswordSecurity {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    data class HashResult(
        val hashHex: String,
        val saltHex: String
    )

    /**
     * Generates a unique 16-byte salt and computes the PBKDF2 hash of the given password.
     */
    fun hashPassword(password: String): HashResult {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val saltHex = bytesToHex(salt)
        val hash = pbkdf2(password.toCharArray(), salt)
        val hashHex = bytesToHex(hash)

        return HashResult(hashHex = hashHex, saltHex = saltHex)
    }

    /**
     * Verifies an input password against a stored hash and salt using constant-time comparison.
     */
    fun verifyPassword(password: String, storedHashHex: String, storedSaltHex: String): Boolean {
        return try {
            val salt = hexToBytes(storedSaltHex)
            val expectedHash = hexToBytes(storedHashHex)
            val computedHash = pbkdf2(password.toCharArray(), salt)

            MessageDigest.isEqual(expectedHash, computedHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val factory = try {
            SecretKeyFactory.getInstance(ALGORITHM)
        } catch (_: NoSuchAlgorithmException) {
            // Fallback for older devices/environments if SHA256 not present
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        return factory.generateSecret(spec).encoded
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Invalid hex string length" }
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
