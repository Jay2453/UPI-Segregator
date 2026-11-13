package com.upisegregator.app.data.upi

import com.upisegregator.app.util.PaiseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

/**
 * Comprehensive unit tests for the updated UPI payment flow:
 * - Internal QR payload regeneration with parameter preservation
 * - Strict preservation of merchant metadata (mc, tid, mid, mode, purpose, orgid)
 * - Strict non-conversion of P2P payments to P2M (no synthetic mc or tr injected)
 * - Fixed-amount QR preservation & tampering prevention
 * - Reliable P2P vs P2M vs UNKNOWN classification
 * - BigDecimal amount validation with decimal precision and INR currency checks
 * - Parser round-tripping verification
 * - Side-by-side comparison between old and new URI generation
 */
class UpiPayloadRegenerationTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 1. P2P vs P2M vs UNKNOWN Payment Classification Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `classifyPaymentType classifies valid MCC as P2M`() {
        val params = mapOf("pa" to "merchant@upi", "mc" to "5411")
        val result = UpiQrParser.classifyPaymentType(params)
        assertEquals(UpiPaymentType.P2M, result)
    }

    @Test
    fun `classifyPaymentType classifies merchant terminal identifiers as P2M even without MCC`() {
        val withTid = mapOf("pa" to "shop@upi", "tid" to "POS_TERM_01")
        assertEquals(UpiPaymentType.P2M, UpiQrParser.classifyPaymentType(withTid))

        val withMid = mapOf("pa" to "shop@upi", "mid" to "MERCHANT_123")
        assertEquals(UpiPaymentType.P2M, UpiQrParser.classifyPaymentType(withMid))

        val withMsid = mapOf("pa" to "shop@upi", "msid" to "STORE_456")
        assertEquals(UpiPaymentType.P2M, UpiQrParser.classifyPaymentType(withMsid))

        val withMtid = mapOf("pa" to "shop@upi", "mtid" to "TERM_789")
        assertEquals(UpiPaymentType.P2M, UpiQrParser.classifyPaymentType(withMtid))
    }

    @Test
    fun `classifyPaymentType classifies standard personal QR without merchant tags as P2P`() {
        val params = mapOf("pa" to "rahul@upi", "pn" to "Rahul Sharma")
        val result = UpiQrParser.classifyPaymentType(params)
        assertEquals(UpiPaymentType.P2P, result)
    }

    @Test
    fun `classifyPaymentType classifies generic 0000 MCC as UNKNOWN`() {
        val params = mapOf("pa" to "entity@upi", "mc" to "0000")
        val result = UpiQrParser.classifyPaymentType(params)
        assertEquals(UpiPaymentType.UNKNOWN, result)
    }

    @Test
    fun `classifyPaymentType classifies malformed non-numeric MCC as UNKNOWN`() {
        val paramsAlpha = mapOf("pa" to "entity@upi", "mc" to "ABCD")
        assertEquals(UpiPaymentType.UNKNOWN, UpiQrParser.classifyPaymentType(paramsAlpha))

        val paramsShort = mapOf("pa" to "entity@upi", "mc" to "12")
        assertEquals(UpiPaymentType.UNKNOWN, UpiQrParser.classifyPaymentType(paramsShort))
    }

    @Test
    fun `classifyPaymentType returns UNKNOWN if UPI ID is invalid`() {
        val params = mapOf("pa" to "invalid_upi_id")
        val result = UpiQrParser.classifyPaymentType(params)
        assertEquals(UpiPaymentType.UNKNOWN, result)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Amount Validation with BigDecimal Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `validateRupeeAmount succeeds for positive amounts with valid decimal precision`() {
        val v10 = PaiseUtils.validateRupeeAmount("10")
        assertTrue(v10 is PaiseUtils.AmountValidationResult.Valid)
        assertEquals(1000L, (v10 as PaiseUtils.AmountValidationResult.Valid).amountPaise)
        assertEquals("10.00", v10.formattedRupees)

        val v10_50 = PaiseUtils.validateRupeeAmount("10.50")
        assertTrue(v10_50 is PaiseUtils.AmountValidationResult.Valid)
        assertEquals(1050L, (v10_50 as PaiseUtils.AmountValidationResult.Valid).amountPaise)
        assertEquals("10.50", v10_50.formattedRupees)

        val v1_25 = PaiseUtils.validateRupeeAmount("1.25")
        assertTrue(v1_25 is PaiseUtils.AmountValidationResult.Valid)
        assertEquals(125L, (v1_25 as PaiseUtils.AmountValidationResult.Valid).amountPaise)
        assertEquals("1.25", v1_25.formattedRupees)

        val vWithComma = PaiseUtils.validateRupeeAmount("1,500.75")
        assertTrue(vWithComma is PaiseUtils.AmountValidationResult.Valid)
        assertEquals(150075L, (vWithComma as PaiseUtils.AmountValidationResult.Valid).amountPaise)
        assertEquals("1500.75", vWithComma.formattedRupees)
    }

    @Test
    fun `validateRupeeAmount rejects sub-paise with more than 2 decimal places`() {
        val v = PaiseUtils.validateRupeeAmount("10.555")
        assertTrue(v is PaiseUtils.AmountValidationResult.Invalid)
        val reason = (v as PaiseUtils.AmountValidationResult.Invalid).reason
        assertTrue(reason.contains("more than 2 decimal places", ignoreCase = true))
    }

    @Test
    fun `validateRupeeAmount rejects zero or negative amounts`() {
        val zero = PaiseUtils.validateRupeeAmount("0")
        assertTrue(zero is PaiseUtils.AmountValidationResult.Invalid)

        val zeroDecimal = PaiseUtils.validateRupeeAmount("0.00")
        assertTrue(zeroDecimal is PaiseUtils.AmountValidationResult.Invalid)

        val negative = PaiseUtils.validateRupeeAmount("-15.00")
        assertTrue(negative is PaiseUtils.AmountValidationResult.Invalid)
    }

    @Test
    fun `validateRupeeAmount rejects non-INR currencies`() {
        val usd = PaiseUtils.validateRupeeAmount("100.00", currency = "USD")
        assertTrue(usd is PaiseUtils.AmountValidationResult.Invalid)
        val reason = (usd as PaiseUtils.AmountValidationResult.Invalid).reason
        assertTrue(reason.contains("Only INR payments are supported", ignoreCase = true))

        val inr = PaiseUtils.validateRupeeAmount("100.00", currency = "INR")
        assertTrue(inr is PaiseUtils.AmountValidationResult.Valid)
    }

    @Test
    fun `validateRupeeAmount rejects empty or non-numeric strings`() {
        assertTrue(PaiseUtils.validateRupeeAmount("") is PaiseUtils.AmountValidationResult.Invalid)
        assertTrue(PaiseUtils.validateRupeeAmount("   ") is PaiseUtils.AmountValidationResult.Invalid)
        assertTrue(PaiseUtils.validateRupeeAmount("abc") is PaiseUtils.AmountValidationResult.Invalid)
        assertTrue(PaiseUtils.validateRupeeAmount("10..00") is PaiseUtils.AmountValidationResult.Invalid)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Merchant Payload Regeneration & Parameter Preservation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `regenerateUpiUriString preserves all merchant metadata and parameters`() {
        val originalQr = "upi://pay?pa=freshmart@hdfcbank&pn=Fresh%20Mart&mc=5411&tid=TERM001&mid=MID999&mode=02&purpose=00&orgid=180001&tr=TXN_ORDER_101"
        val parseResult = UpiQrParser.parse(originalQr)
        assertTrue(parseResult is UpiQrParseResult.Success)
        val originalData = (parseResult as UpiQrParseResult.Success).data

        assertEquals(UpiPaymentType.P2M, originalData.paymentType)
        assertFalse(originalData.isAmountFixed)

        // User enters ₹250.75
        val amountPaise = 25075L
        val regeneratedUri = UpiIntentBuilder.regenerateUpiUriString(originalData, amountPaise)

        // Verify generated parameters
        assertTrue("Scheme must be upi://pay?", regeneratedUri.startsWith("upi://pay?"))
        assertTrue("Recipient must be preserved", regeneratedUri.contains("pa=freshmart%40hdfcbank") || regeneratedUri.contains("pa=freshmart@hdfcbank"))
        assertTrue("Name must be preserved", regeneratedUri.contains("pn=Fresh%20Mart"))
        assertTrue("Merchant Code must be preserved", regeneratedUri.contains("mc=5411"))
        assertTrue("Terminal ID must be preserved", regeneratedUri.contains("tid=TERM001"))
        assertTrue("Merchant ID must be preserved", regeneratedUri.contains("mid=MID999"))
        assertTrue("Mode must be preserved", regeneratedUri.contains("mode=02"))
        assertTrue("Purpose must be preserved", regeneratedUri.contains("purpose=00"))
        assertTrue("Org ID must be preserved", regeneratedUri.contains("orgid=180001"))
        assertTrue("Transaction Ref must be preserved", regeneratedUri.contains("tr=TXN_ORDER_101"))
        assertTrue("Amount must be prefilled as 250.75", regeneratedUri.contains("am=250.75"))
        assertTrue("Currency must be INR", regeneratedUri.contains("cu=INR"))

        // Re-parse and validate
        val validationResult = UpiIntentBuilder.validateRegeneratedPayload(
            regeneratedUriString = regeneratedUri,
            expectedPaise = amountPaise,
            expectedUpiId = "freshmart@hdfcbank",
            originalData = originalData
        )
        assertTrue("Validation must pass: $validationResult", validationResult is UpiUriValidationResult.Valid)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. P2P Payment Payload Regeneration (No Accidental P2M Conversion)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `regenerateUpiUriString does not add merchant code or synthetic parameters to P2P QR`() {
        val originalQr = "upi://pay?pa=rahul@upi&pn=Rahul%20Sharma&cu=INR"
        val parseResult = UpiQrParser.parse(originalQr)
        assertTrue(parseResult is UpiQrParseResult.Success)
        val originalData = (parseResult as UpiQrParseResult.Success).data

        assertEquals(UpiPaymentType.P2P, originalData.paymentType)

        // User enters ₹500.00
        val amountPaise = 50000L
        val regeneratedUri = UpiIntentBuilder.regenerateUpiUriString(originalData, amountPaise, overrideNote = "Dinner")

        // Must NOT contain merchant parameters or synthetic tr
        assertFalse("P2P must NOT contain mc=", regeneratedUri.contains("mc="))
        assertFalse("P2P must NOT contain tid=", regeneratedUri.contains("tid="))
        assertFalse("P2P must NOT contain mid=", regeneratedUri.contains("mid="))
        assertFalse("P2P must NOT contain synthetic tr=", regeneratedUri.contains("tr="))
        assertTrue("Must contain amount 500.00", regeneratedUri.contains("am=500.00"))
        assertTrue("Must contain tn=Dinner", regeneratedUri.contains("tn=Dinner"))

        // Validate payload
        val validationResult = UpiIntentBuilder.validateRegeneratedPayload(
            regeneratedUriString = regeneratedUri,
            expectedPaise = amountPaise,
            expectedUpiId = "rahul@upi",
            originalData = originalData
        )
        assertTrue("Validation must succeed", validationResult is UpiUriValidationResult.Valid)

        // Re-parsing through UpiQrParser must classify as P2P
        val reparsed = UpiQrParser.parse(regeneratedUri)
        assertTrue(reparsed is UpiQrParseResult.Success)
        assertEquals(UpiPaymentType.P2P, (reparsed as UpiQrParseResult.Success).data.paymentType)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Fixed-Amount QR Preservation & Modification Prevention
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `regenerateUpiUriString preserves fixed amount and prevents amount alteration`() {
        val originalQr = "upi://pay?pa=chai@upi&pn=Chai%20Stall&am=15.00&cu=INR&tn=Cutting%20Chai"
        val parseResult = UpiQrParser.parse(originalQr)
        assertTrue(parseResult is UpiQrParseResult.Success)
        val originalData = (parseResult as UpiQrParseResult.Success).data

        assertTrue("Must be fixed amount", originalData.isAmountFixed)
        assertEquals(1500L, originalData.amountPaise)

        // Regenerating with exact amount succeeds
        val regenerated = UpiIntentBuilder.regenerateUpiUriString(originalData, 1500L)
        assertTrue(regenerated.contains("am=15.00"))

        val validation = UpiIntentBuilder.validateRegeneratedPayload(
            regeneratedUriString = regenerated,
            expectedPaise = 1500L,
            expectedUpiId = "chai@upi",
            originalData = originalData
        )
        assertTrue(validation is UpiUriValidationResult.Valid)

        // Attempting to alter amount on fixed-amount QR must fail immediately
        try {
            UpiIntentBuilder.regenerateUpiUriString(originalData, 2000L)
            fail("Expected IllegalArgumentException when trying to modify fixed amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cannot alter amount for a fixed-amount QR code") == true)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Parser Round-Tripping Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parser round-trip preserves all query parameters faithfully`() {
        val rawQr = "upi://pay?pa=bookstore@icici&pn=Book%20Depot&mc=5942&mode=04&orgid=180002&tid=T007&custom_param=xyz123"
        val firstPass = UpiQrParser.parse(rawQr)
        assertTrue(firstPass is UpiQrParseResult.Success)
        val firstData = (firstPass as UpiQrParseResult.Success).data

        // User enters ₹799.00
        val regeneratedUri = UpiIntentBuilder.regenerateUpiUriString(firstData, 79900L, overrideNote = "Novel")

        // Pass regenerated URI back through UpiQrParser
        val secondPass = UpiQrParser.parse(regeneratedUri)
        assertTrue(secondPass is UpiQrParseResult.Success)
        val secondData = (secondPass as UpiQrParseResult.Success).data

        // Compare all attributes
        assertEquals(firstData.upiId, secondData.upiId)
        assertEquals(firstData.recipientName, secondData.recipientName)
        assertEquals(79900L, secondData.amountPaise)
        assertEquals("799.00", secondData.amountString)
        assertEquals("INR", secondData.currency)
        assertEquals("Novel", secondData.note)
        assertEquals(firstData.merchantCode, secondData.merchantCode)
        assertEquals(firstData.mode, secondData.mode)
        assertEquals(firstData.terminalId, secondData.terminalId)
        assertEquals(firstData.orgId, secondData.orgId)
        assertEquals(firstData.paymentType, secondData.paymentType)
        assertEquals("xyz123", secondData.allParameters["custom_param"])
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. Old vs New URI Behavior Comparison
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `compare old versus regenerated URI parameters for a merchant QR`() {
        val scannedQr = "upi://pay?pa=restaurant@axis&pn=Biryani%20House&mc=5812&tid=POS_58&orgid=180000&mode=02&tr=ORDER_88"
        val parseResult = UpiQrParser.parse(scannedQr)
        assertTrue(parseResult is UpiQrParseResult.Success)
        val qrData = (parseResult as UpiQrParseResult.Success).data

        val amountPaise = 45000L // ₹450.00

        // OLD approach (buildUpiUriString): discarded mc, tid, orgid, mode
        val oldUri = UpiIntentBuilder.buildUpiUriString(
            payeeUpiId = qrData.upiId,
            payeeName = qrData.recipientName,
            amountPaise = amountPaise,
            note = qrData.note,
            transactionRef = qrData.transactionRef
        )

        // NEW approach (regenerateUpiUriString): preserves original merchant metadata
        val newUri = UpiIntentBuilder.regenerateUpiUriString(
            originalData = qrData,
            amountPaise = amountPaise
        )

        // VERIFY OLD BEHAVIOR DEFICIENCY:
        assertFalse("Old URI dropped merchant code (mc)", oldUri.contains("mc="))
        assertFalse("Old URI dropped terminal ID (tid)", oldUri.contains("tid="))
        assertFalse("Old URI dropped mode", oldUri.contains("mode="))
        assertFalse("Old URI dropped orgid", oldUri.contains("orgid="))

        // VERIFY NEW BEHAVIOR PRESERVATION:
        assertTrue("New URI retains merchant code (mc)", newUri.contains("mc=5812"))
        assertTrue("New URI retains terminal ID (tid)", newUri.contains("tid=POS_58"))
        assertTrue("New URI retains mode", newUri.contains("mode=02"))
        assertTrue("New URI retains orgid", newUri.contains("orgid=180000"))
        assertTrue("New URI retains transaction reference (tr)", newUri.contains("tr=ORDER_88"))
        assertTrue("New URI prefills amount", newUri.contains("am=450.00"))
        assertTrue("New URI sets currency to INR", newUri.contains("cu=INR"))
    }
}
