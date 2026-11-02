package com.upisegregator.app.data.importer.deduplication

import com.upisegregator.app.data.importer.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFingerprintGeneratorTest {

    @Test
    fun fingerprint_deterministicOutput() {
        val hash1 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L, // 2025-01-01 UTC
            amountPaise = 50000L,
            direction = TransactionDirection.DEBIT,
            description = "Swiggy Order #1234",
            recipientName = "Swiggy",
            externalReference = "UTR123456789"
        )

        val hash2 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 50000L,
            direction = TransactionDirection.DEBIT,
            description = "Swiggy Order #1234",
            recipientName = "Swiggy",
            externalReference = "UTR123456789"
        )

        assertEquals("Same inputs must produce identical SHA-256 fingerprint", hash1, hash2)
        assertEquals(64, hash1.length) // 256 bits = 64 hex characters
    }

    @Test
    fun fingerprint_normalizesWhitespaceAndCase() {
        val hash1 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "USER_123",
            dateMillis = 1735689600000L,
            amountPaise = 50000L,
            direction = TransactionDirection.DEBIT,
            description = "  Swiggy   Order   #1234  ",
            recipientName = "SWIGGY",
            externalReference = "utr123456789"
        )

        val hash2 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 50000L,
            direction = TransactionDirection.DEBIT,
            description = "swiggy order 1234",
            recipientName = "swiggy",
            externalReference = "UTR123456789"
        )

        assertEquals("Case and spacing differences in descriptions should normalize cleanly", hash1, hash2)
    }

    @Test
    fun fingerprint_differentAmount_producesDifferentHash() {
        val hash1 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 50000L,
            direction = TransactionDirection.DEBIT,
            description = "Lunch",
            recipientName = "Cafe"
        )

        val hash2 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 50100L, // 1 rupee difference
            direction = TransactionDirection.DEBIT,
            description = "Lunch",
            recipientName = "Cafe"
        )

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun fingerprint_sameAmountAndDateDifferentMerchants_doNotCollide() {
        val hashSwiggy = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 25000L,
            direction = TransactionDirection.DEBIT,
            description = "Swiggy delivery",
            recipientName = "Swiggy"
        )

        val hashUber = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_123",
            dateMillis = 1735689600000L,
            amountPaise = 25000L,
            direction = TransactionDirection.DEBIT,
            description = "Uber trip",
            recipientName = "Uber"
        )

        assertNotEquals("Distinct transactions with same amount and date must not collide", hashSwiggy, hashUber)
    }

    @Test
    fun fingerprint_differentUsers_isolated() {
        val hashUser1 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_1",
            dateMillis = 1735689600000L,
            amountPaise = 25000L,
            direction = TransactionDirection.DEBIT,
            description = "Swiggy",
            recipientName = "Swiggy"
        )

        val hashUser2 = TransactionFingerprintGenerator.generateFingerprint(
            userId = "user_2",
            dateMillis = 1735689600000L,
            amountPaise = 25000L,
            direction = TransactionDirection.DEBIT,
            description = "Swiggy",
            recipientName = "Swiggy"
        )

        assertNotEquals("Different users must have distinct fingerprints to guarantee isolation", hashUser1, hashUser2)
    }
}
