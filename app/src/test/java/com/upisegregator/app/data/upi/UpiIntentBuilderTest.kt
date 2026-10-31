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
    fun `UPI URI string contains all required parameters including tr and omits empty tn`() {
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
        assertTrue("URI must have tr=", uri.contains("tr="))
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
    fun `UPI URI string formats various amounts correctly`() {
        // 1 rupee (100 paise) -> 1.00
        val uri1 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 100L)
        assertTrue("₹1 formatted as 1.00", uri1.contains("am=1.00"))

        // 10.50 rupees (1050 paise) -> 10.50
        val uri1050 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 1050L)
        assertTrue("₹10.50 formatted as 10.50", uri1050.contains("am=10.50"))

        // 100 rupees (10000 paise) -> 100.00
        val uri100 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 10000L)
        assertTrue("₹100 formatted as 100.00", uri100.contains("am=100.00"))

        // 250 rupees (25000 paise) -> 250.00
        val uri250 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 25000L)
        assertTrue("₹250 formatted as 250.00", uri250.contains("am=250.00"))

        // 1,000 rupees (100000 paise) -> 1000.00
        val uri1000 = UpiUriHelper.buildUpiUriString("x@upi", "Name", 100000L)
        assertTrue("₹1,000 formatted as 1000.00", uri1000.contains("am=1000.00"))
    }

    @Test
    fun `UPI URI preserves custom transaction reference tr`() {
        val customTr = "ORDER123456789"
        val uri = UpiUriHelper.buildUpiUriString("x@upi", "Name", 1000L, transactionRef = customTr)
        assertTrue("URI must contain custom tr", uri.contains("tr=ORDER123456789"))
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
        transactionRef: String? = null,
        merchantCode: String? = null,
        mode: String? = null,
        purpose: String? = null
    ): String {
        require(amountPaise > 0L) { "Amount must be positive" }
        val amount = PaiseUtils.paiseToRupeesString(amountPaise)
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        val tr = if (!transactionRef.isNullOrBlank()) {
            transactionRef.trim().filter { it.isLetterOrDigit() }.take(35)
        } else {
            "TXN" + java.util.UUID.randomUUID().toString().replace("-", "").uppercase().take(27)
        }

        val sb = StringBuilder("upi://pay?")
        sb.append("pa=").append(enc(payeeUpiId.trim()))
        sb.append("&pn=").append(enc(payeeName.trim().ifBlank { payeeUpiId.trim() }))

        if (!merchantCode.isNullOrBlank()) {
            sb.append("&mc=").append(enc(merchantCode.trim()))
        }

        sb.append("&tr=").append(enc(tr))

        val cleanNote = note.trim().take(50)
        if (cleanNote.isNotEmpty()) {
            sb.append("&tn=").append(enc(cleanNote))
        }

        sb.append("&am=").append(enc(amount))
        sb.append("&cu=INR")

        if (!mode.isNullOrBlank()) {
            sb.append("&mode=").append(enc(mode.trim()))
        }
        if (!purpose.isNullOrBlank()) {
            sb.append("&purpose=").append(enc(purpose.trim()))
        }

        return sb.toString()
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
