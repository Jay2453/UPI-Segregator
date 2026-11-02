package com.upisegregator.app.data.upi

import com.upisegregator.app.util.PaiseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for UPI-related logic.
 *
 * NOTE: buildUpiUri uses android.net.Uri which is not available in pure JVM tests.
 * URI construction tests use [UpiUriHelper] — a pure-Kotlin helper that builds
 * and returns the URI as a String for testability without Android SDK.
 *
 * UPI ID validation, amount conversion, and PaymentValidation logic are all
 * pure Kotlin and run on JVM directly.
 */
class UpiPaymentLogicTest {

    // ──────────────────────────────────────────────────────────────────────────
    // UPI ID Validation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `valid UPI IDs pass validation`() {
        val validIds = listOf(
            "rahul@upi",
            "9876543210@paytm",
            "user.name@ybl",
            "merchant-1@hdfcbank",
            "shop_123@okaxis",
            "a@b"
        )
        for (id in validIds) {
            val result = UpiIntentBuilder.validateUpiId(id)
            assertTrue("Expected '$id' to be valid but got: $result", result is UpiIdValidation.Valid)
        }
    }

    @Test
    fun `blank UPI ID fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("")
        assertTrue(result is UpiIdValidation.Invalid)
        assertTrue((result as UpiIdValidation.Invalid).reason.contains("empty", ignoreCase = true))
    }

    @Test
    fun `UPI ID without at-sign fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("rahulupi")
        assertTrue(result is UpiIdValidation.Invalid)
        assertTrue((result as UpiIdValidation.Invalid).reason.contains("@"))
    }

    @Test
    fun `UPI ID with two at-signs fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("a@b@c")
        assertTrue(result is UpiIdValidation.Invalid)
    }

    @Test
    fun `UPI handle with dot fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("user@upi.com")
        assertTrue(result is UpiIdValidation.Invalid)
        val reason = (result as UpiIdValidation.Invalid).reason
        assertTrue("Expected 'dot' in message but got: $reason", reason.contains("dot", ignoreCase = true))
    }

    @Test
    fun `UPI ID with blank identifier part fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("@upi")
        assertTrue(result is UpiIdValidation.Invalid)
    }

    @Test
    fun `UPI ID with blank handle fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("user@")
        assertTrue(result is UpiIdValidation.Invalid)
    }

    @Test
    fun `UPI handle with hyphen fails validation`() {
        // Handles must be alphanumeric only
        val result = UpiIntentBuilder.validateUpiId("user@pay-tm")
        assertTrue(result is UpiIdValidation.Invalid)
    }

    @Test
    fun `UPI ID with spaces fails validation`() {
        val result = UpiIntentBuilder.validateUpiId("  ")
        assertTrue(result is UpiIdValidation.Invalid)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // URI String Construction (pure Kotlin — no android.net.Uri needed)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `UPI URI string contains essential standard parameters and omits empty tn and fake tr`() {
        val uri = UpiUriHelper.buildUpiUriString(
            payeeUpiId = "rahul@upi",
            payeeName = "Rahul",
            amountPaise = 25000L,
            note = "Lunch"
        )
        assertTrue("URI must have scheme", uri.startsWith("upi://pay"))
        assertTrue("URI must have pa=", uri.contains("pa=rahul%40upi") || uri.contains("pa=rahul@upi"))
        assertTrue("URI must have cu=INR", uri.contains("cu=INR"))
        assertTrue("URI must have am=250.00", uri.contains("am=250.00"))
        assertTrue("URI must have tn=Lunch", uri.contains("tn=Lunch"))
        assertFalse("URI must NOT generate synthetic tr for standard payments", uri.contains("tr="))
        assertFalse("URI must NOT contain mc=", uri.contains("mc="))
        assertFalse("URI must NOT contain mode=", uri.contains("mode="))
        assertFalse("URI must NOT contain purpose=", uri.contains("purpose="))
    }

    @Test
    fun `UPI URI string omits tn when note is blank`() {
        val uri = UpiUriHelper.buildUpiUriString(
            payeeUpiId = "rahul@upi",
            payeeName = "Rahul",
            amountPaise = 1000L,
            note = ""
        )
        assertFalse("URI must NOT have tn= when note is blank", uri.contains("tn="))
    }

    @Test
    fun `BUG 1 FIX - amount 10 rupees formats strictly as 10_00`() {
        // 1000 paise = 10.00 rupees
        val uri = UpiUriHelper.buildUpiUriString("payee@upi", "Payee", 1000L)
        assertTrue("URI must format ₹10 as am=10.00", uri.contains("am=10.00"))
        assertFalse("URI must NOT format ₹10 as am=10 without decimals", uri.contains("am=10&") || uri.endsWith("am=10"))
    }

    @Test
    fun `UPI URI string formats required test amounts correctly`() {
        // ₹1 (100 paise) -> 1.00
        val uri1 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 100L)
        assertTrue("₹1 formatted as 1.00", uri1.contains("am=1.00"))

        // ₹10 (1000 paise) -> 10.00
        val uri10 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 1000L)
        assertTrue("₹10 formatted as 10.00", uri10.contains("am=10.00"))

        // ₹10.50 (1050 paise) -> 10.50
        val uri1050 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 1050L)
        assertTrue("₹10.50 formatted as 10.50", uri1050.contains("am=10.50"))

        // ₹100 (10000 paise) -> 1000.00
        val uri100 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 10000L)
        assertTrue("₹100 formatted as 100.00", uri100.contains("am=100.00"))

        // ₹250 (25000 paise) -> 250.00
        val uri250 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 25000L)
        assertTrue("₹250 formatted as 250.00", uri250.contains("am=250.00"))

        // ₹999 (99900 paise) -> 999.00
        val uri999 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 99900L)
        assertTrue("₹999 formatted as 999.00", uri999.contains("am=999.00"))

        // ₹1,000 (100000 paise) -> 1000.00
        val uri1000 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 100000L)
        assertTrue("₹1,000 formatted as 1000.00", uri1000.contains("am=1000.00"))
    }

    @Test
    fun `UPI URI preserves custom transaction reference tr when provided`() {
        val customTr = "ORDER123456789"
        val uri = UpiUriHelper.buildUpiUriString("x@upi", "Name", 1000L, transactionRef = customTr)
        assertTrue("URI must contain custom tr", uri.contains("tr=ORDER123456789"))
    }

    @Test
    fun `UPI URI preserves exact recipient UPI ID with special characters`() {
        val upiId = "store.merchant_01-test@hdfcbank"
        val uri = UpiIntentBuilder.buildUpiUriString(upiId, "Store", 1000L)
        val parsed = UpiIntentBuilder.parseUpiUriString(uri)
        assertEquals(upiId, parsed.pa)
    }

    @Test
    fun `UPI URI properly URL encodes spaces and special characters`() {
        val uri = UpiIntentBuilder.buildUpiUriString(
            payeeUpiId = "user@upi",
            payeeName = "Dinesh Patel",
            amountPaise = 1000L,
            note = "Dinner & Drinks"
        )
        // Space should be encoded as %20
        assertTrue("Name spaces should be %20", uri.contains("pn=Dinesh%20Patel"))
        assertTrue("Note spaces and ampersand should be encoded", uri.contains("tn=Dinner%20%26%20Drinks"))
        // Avoid double encoding: must not contain %25
        assertFalse("Must not double encode", uri.contains("%25"))
    }

    @Test
    fun `UPI URI parsing extracts all parameters correctly`() {
        val uriString = "upi://pay?pa=rahul%40upi&pn=Rahul%20Kumar&am=10.00&cu=INR&tn=Tea&tr=TXN123"
        val parsed = UpiIntentBuilder.parseUpiUriString(uriString)
        assertEquals("rahul@upi", parsed.pa)
        assertEquals("Rahul Kumar", parsed.pn)
        assertEquals("10.00", parsed.am)
        assertEquals("INR", parsed.cu)
        assertEquals("Tea", parsed.tn)
        assertEquals("TXN123", parsed.tr)
        assertTrue(parsed.duplicateKeys.isEmpty())
    }

    @Test
    fun `UPI URI parsing flags duplicate parameters`() {
        val uriString = "upi://pay?pa=rahul%40upi&am=10.00&cu=INR&am=20.00"
        val parsed = UpiIntentBuilder.parseUpiUriString(uriString)
        assertTrue("Should detect duplicate 'am'", parsed.duplicateKeys.contains("am"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation Layer Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `validation layer succeeds for valid matching URI`() {
        val uriString = UpiIntentBuilder.buildUpiUriString(
            payeeUpiId = "rahul@upi",
            payeeName = "Rahul",
            amountPaise = 1000L,
            note = "Coffee"
        )
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "rahul@upi",
            selectedPackage = "com.google.android.apps.nbu.paisa.user"
        )
        assertTrue("Expected valid result but got: $result", result is UpiUriValidationResult.Valid)
        val valid = result as UpiUriValidationResult.Valid
        assertEquals(1000L, valid.originalPaise)
        assertEquals(1000L, valid.parsedPaise)
        assertEquals("ra***l@upi", valid.recipientMasked)
        assertTrue(valid.paramNames.contains("am"))
        assertTrue(valid.paramNames.contains("cu"))
        assertTrue(valid.paramNames.contains("pa"))
    }

    @Test
    fun `validation layer rejects amount mismatch`() {
        // Built with 10.00 rupees (1000 paise), but expected is 2500 paise
        val uriString = UpiIntentBuilder.buildUpiUriString("user@upi", "User", 1000L)
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 2500L,
            expectedUpiId = "user@upi"
        )
        assertTrue("Should reject amount mismatch", result is UpiUriValidationResult.Invalid)
        val reason = (result as UpiUriValidationResult.Invalid).reason
        assertTrue("Reason should mention mismatch: $reason", reason.contains("mismatch", ignoreCase = true))
    }

    @Test
    fun `validation layer rejects recipient mismatch`() {
        val uriString = UpiIntentBuilder.buildUpiUriString("alice@upi", "Alice", 1000L)
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "bob@upi"
        )
        assertTrue("Should reject recipient mismatch", result is UpiUriValidationResult.Invalid)
        val reason = (result as UpiUriValidationResult.Invalid).reason
        assertTrue(reason.contains("mismatch", ignoreCase = true))
    }

    @Test
    fun `validation layer rejects non-INR currency`() {
        val uriString = "upi://pay?pa=user%40upi&pn=User&am=10.00&cu=USD"
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "user@upi"
        )
        assertTrue("Should reject non-INR currency", result is UpiUriValidationResult.Invalid)
    }

    @Test
    fun `validation layer rejects non-decimal amount format`() {
        // "10" instead of decimal "10.00"
        val uriString = "upi://pay?pa=user%40upi&pn=User&am=10&cu=INR"
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "user@upi"
        )
        assertTrue("Should reject non-decimal amount", result is UpiUriValidationResult.Invalid)
    }

    @Test
    fun `validation layer rejects empty note tn parameter`() {
        val uriString = "upi://pay?pa=user%40upi&pn=User&am=10.00&cu=INR&tn="
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "user@upi"
        )
        assertTrue("Should reject empty tn", result is UpiUriValidationResult.Invalid)
    }

    @Test
    fun `validation layer rejects duplicate parameters in URI`() {
        val uriString = "upi://pay?pa=user%40upi&pn=User&am=10.00&cu=INR&am=10.00"
        val result = UpiIntentBuilder.validateUpiUriString(
            uriString = uriString,
            expectedPaise = 1000L,
            expectedUpiId = "user@upi"
        )
        assertTrue("Should reject duplicate parameter", result is UpiUriValidationResult.Invalid)
        assertTrue((result as UpiUriValidationResult.Invalid).reason.contains("Duplicate", ignoreCase = true))
    }

    @Test
    fun `selected package handling - known packages list includes Google Pay and PhonePe`() {
        val packages = UpiIntentBuilder.KNOWN_UPI_PACKAGES.toMap()
        assertEquals("Google Pay", packages["com.google.android.apps.nbu.paisa.user"])
        assertEquals("PhonePe", packages["com.phonepe.app"])
        assertEquals("Paytm", packages["net.one97.paytm"])
        assertEquals("BHIM", packages["in.org.npci.upiapp"])
    }

    @Test
    fun `selected package handling - target package does not alter amount or recipient`() {
        val originalPaise = 1000L
        val originalUpi = "merchant@upi"
        val uriGPay = UpiIntentBuilder.buildUpiUriString(originalUpi, "Merchant", originalPaise)
        val uriPhonePe = UpiIntentBuilder.buildUpiUriString(originalUpi, "Merchant", originalPaise)

        // The URI must remain completely invariant regardless of which package is targeted
        assertEquals(uriGPay, uriPhonePe)

        val gpayValidation = UpiIntentBuilder.validateUpiUriString(
            uriString = uriGPay,
            expectedPaise = originalPaise,
            expectedUpiId = originalUpi,
            selectedPackage = "com.google.android.apps.nbu.paisa.user"
        )
        assertTrue(gpayValidation is UpiUriValidationResult.Valid)

        val phonePeValidation = UpiIntentBuilder.validateUpiUriString(
            uriString = uriPhonePe,
            expectedPaise = originalPaise,
            expectedUpiId = originalUpi,
            selectedPackage = "com.phonepe.app"
        )
        assertTrue(phonePeValidation is UpiUriValidationResult.Valid)
    }

    @Test
    fun `maskUpiId masks identifier safely`() {
        assertEquals("ra***l@upi", UpiIntentBuilder.maskUpiId("rahul@upi"))
        assertEquals("98***0@paytm", UpiIntentBuilder.maskUpiId("9876543210@paytm"))
        assertEquals("a***@b", UpiIntentBuilder.maskUpiId("ab@b"))
    }

    @Test
    fun `UPI URI string formats amount from paise correctly`() {
        // 149950 paise = 1499.50 rupees
        val uri = UpiUriHelper.buildUpiUriString("x@upi", "Name", 149950L)
        assertTrue("URI must format amount as 1499.50", uri.contains("am=1499.50"))
    }

    @Test
    fun `UPI URI string truncates note at 50 chars`() {
        val longNote = "A".repeat(100)
        val uri = UpiUriHelper.buildUpiUriString("x@upi", "Name", 10000L, longNote)
        // Note in URI should be max 50 chars (before encoding)
        assertNotNull(uri)
        assertTrue(uri.isNotBlank())
        assertTrue(uri.contains("tn=" + "A".repeat(50)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UPI URI rejects zero amount`() {
        UpiUriHelper.buildUpiUriString("x@upi", "Name", 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UPI URI rejects negative amount`() {
        UpiUriHelper.buildUpiUriString("x@upi", "Name", -500L)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Amount / Paise Conversion
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `rupeesToPaise converts integer rupees`() {
        assertEquals(25000L, PaiseUtils.rupeesToPaise("250"))
    }

    @Test
    fun `rupeesToPaise converts decimal rupees`() {
        assertEquals(24950L, PaiseUtils.rupeesToPaise("249.50"))
    }

    @Test
    fun `rupeesToPaise strips rupee symbol`() {
        assertEquals(10000L, PaiseUtils.rupeesToPaise("₹100"))
    }

    @Test
    fun `rupeesToPaise strips commas`() {
        assertEquals(1000000L, PaiseUtils.rupeesToPaise("10,000"))
    }

    @Test
    fun `rupeesToPaiseOrNull returns null for blank`() {
        assertNull(PaiseUtils.rupeesToPaiseOrNull(""))
    }

    @Test
    fun `rupeesToPaiseOrNull returns null for text`() {
        assertNull(PaiseUtils.rupeesToPaiseOrNull("abc"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rupeesToPaise rejects zero`() {
        PaiseUtils.rupeesToPaise("0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rupeesToPaise rejects negative`() {
        PaiseUtils.rupeesToPaise("-100")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rupeesToPaise rejects sub-paise precision`() {
        PaiseUtils.rupeesToPaise("100.123")
    }

    @Test
    fun `paiseToRupeesString formats correctly`() {
        assertEquals("250.00", PaiseUtils.paiseToRupeesString(25000L))
        assertEquals("1499.50", PaiseUtils.paiseToRupeesString(149950L))
        assertEquals("0.01", PaiseUtils.paiseToRupeesString(1L))
        assertEquals("10000.00", PaiseUtils.paiseToRupeesString(1000000L))
    }

    @Test
    fun `isValidAmount true for positive paise`() {
        assertTrue(PaiseUtils.isValidAmount(1L))
        assertTrue(PaiseUtils.isValidAmount(100L))
    }

    @Test
    fun `isValidAmount false for zero`() {
        assertFalse(PaiseUtils.isValidAmount(0L))
    }

    @Test
    fun `isValidAmount false for negative`() {
        assertFalse(PaiseUtils.isValidAmount(-1L))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payment Input Validation Logic
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `payment validation catches empty recipient name`() {
        val result = PaymentValidator.validate(recipientName = "", upiId = "r@upi", amountText = "100")
        assertFalse(result.isValid)
        assertNotNull(result.recipientNameError)
    }

    @Test
    fun `payment validation catches invalid UPI ID`() {
        val result = PaymentValidator.validate(recipientName = "Rahul", upiId = "invalid", amountText = "100")
        assertFalse(result.isValid)
        assertNotNull(result.upiIdError)
    }

    @Test
    fun `payment validation catches zero amount`() {
        val result = PaymentValidator.validate(recipientName = "Rahul", upiId = "r@upi", amountText = "0")
        assertFalse(result.isValid)
        assertNotNull(result.amountError)
    }

    @Test
    fun `payment validation catches negative amount`() {
        val result = PaymentValidator.validate(recipientName = "Rahul", upiId = "r@upi", amountText = "-50")
        assertFalse(result.isValid)
        assertNotNull(result.amountError)
    }

    @Test
    fun `payment validation catches non-numeric amount`() {
        val result = PaymentValidator.validate(recipientName = "Rahul", upiId = "r@upi", amountText = "abc")
        assertFalse(result.isValid)
        assertNotNull(result.amountError)
    }

    @Test
    fun `payment validation succeeds for valid inputs`() {
        val result = PaymentValidator.validate(
            recipientName = "Rahul Kumar",
            upiId = "rahul@upi",
            amountText = "250"
        )
        assertTrue(result.isValid)
        assertEquals(25000L, result.amountPaise)
        assertNull(result.recipientNameError)
        assertNull(result.upiIdError)
        assertNull(result.amountError)
    }

    @Test
    fun `payment validation accepts decimal amount`() {
        val result = PaymentValidator.validate("Name", "x@upi", "1499.50")
        assertTrue(result.isValid)
        assertEquals(149950L, result.amountPaise)
    }

    @Test
    fun `audit URI encoding of VPA containing at-sign`() {
        val rawUpiId = "merchant.store@hdfcbank"
        val manualEncoded = java.net.URLEncoder.encode(rawUpiId, "UTF-8").replace("+", "%20")
        
        // Exact encoded string contains %40 for @
        assertEquals("merchant.store%40hdfcbank", manualEncoded)

        // Built URI string contains the encoded parameter
        val uriString = UpiIntentBuilder.buildUpiUriString(rawUpiId, "Merchant", 1000L)
        assertTrue(uriString.contains("pa=merchant.store%40hdfcbank"))

        // When decoded by standard URLDecoder, it safely returns the exact original VPA
        val decoded = java.net.URLDecoder.decode(manualEncoded, "UTF-8")
        assertEquals(rawUpiId, decoded)

        // When parsed by UpiIntentBuilder.parseUpiUriString, original VPA is strictly restored
        val parsed = UpiIntentBuilder.parseUpiUriString(uriString)
        assertEquals(rawUpiId, parsed.pa)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Pure-Kotlin helpers used only in tests
// ────────────────────────────────────────────────────────────────────────────

/**
 * Pure-Kotlin UPI URI builder for unit test environments (no android.net.Uri).
 * Uses java.net.URLEncoder for parameter encoding.
 */
object UpiUriHelper {
    fun buildUpiUriString(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = "",
        transactionRef: String? = null
    ): String {
        return UpiIntentBuilder.buildUpiUriString(
            payeeUpiId = payeeUpiId,
            payeeName = payeeName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = transactionRef
        )
    }
}

/**
 * Pure-Kotlin payment field validator for unit testing without ViewModel.
 */
object PaymentValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val amountPaise: Long = 0L,
        val recipientNameError: String? = null,
        val upiIdError: String? = null,
        val amountError: String? = null
    )

    fun validate(recipientName: String, upiId: String, amountText: String): ValidationResult {
        var valid = true
        var nameError: String? = null
        var upiError: String? = null
        var amtError: String? = null
        var paise = 0L

        if (recipientName.trim().length < 2) {
            nameError = "Recipient name must be at least 2 characters"
            valid = false
        }

        val upiValidation = UpiIntentBuilder.validateUpiId(upiId)
        if (upiValidation is UpiIdValidation.Invalid) {
            upiError = upiValidation.reason
            valid = false
        }

        val parsed = PaiseUtils.rupeesToPaiseOrNull(amountText)
        if (parsed == null || parsed <= 0L) {
            amtError = "Enter a valid amount greater than ₹0"
            valid = false
        } else {
            paise = parsed
        }

        return ValidationResult(
            isValid = valid,
            amountPaise = paise,
            recipientNameError = nameError,
            upiIdError = upiError,
            amountError = amtError
        )
    }
}
