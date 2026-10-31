package com.upisegregator.app.data.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UpiQrParser].
 *
 * Verifies parsing of NPCI UPI Deep Link / QR code specs:
 * - Scheme validation (case-insensitive)
 * - Required payee VPA (pa)
 * - Payee name (pn) and URL decoding
 * - Smart Amount Mode (am): fixed amount vs user-entered
 * - Amount validation (positive integer paise, rejecting zero, negative, malformed)
 * - Currency validation (cu=INR)
 * - Optional parameters: tn, tr, mc, mode, purpose
 * - Merchant Category Code (MCC) human-readable mapping
 * - Duplicate conflicting parameter rejection
 */
class UpiQrParserTest {

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Valid Standard UPI QRs
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse valid standard UPI QR without amount`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant%20Store&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue("Expected Success, got $result", result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("merchant@upi", data.upiId)
        assertEquals("Merchant Store", data.recipientName)
        assertFalse(data.isAmountFixed)
        assertNull(data.amountPaise)
        assertNull(data.amountString)
        assertEquals("INR", data.currency)
    }

    @Test
    fun `parse valid UPI QR with fixed amount`() {
        val qr = "upi://pay?pa=chaiwala@okaxis&pn=Chai%20Stall&am=15.00&cu=INR&tn=Cutting%20Chai"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("chaiwala@okaxis", data.upiId)
        assertEquals("Chai Stall", data.recipientName)
        assertTrue(data.isAmountFixed)
        assertEquals(1500L, data.amountPaise)
        assertEquals("15.00", data.amountString)
        assertEquals("Cutting Chai", data.note)
    }

    @Test
    fun `parse valid UPI QR with all NPCI parameters and MCC mapping`() {
        val qr = "upi://pay?pa=supermarket@hdfcbank&pn=Fresh%20Mart&am=249.50&cu=INR&tn=Groceries&mc=5411&tr=TXN987654321&mode=02&purpose=00"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("supermarket@hdfcbank", data.upiId)
        assertEquals("Fresh Mart", data.recipientName)
        assertTrue(data.isAmountFixed)
        assertEquals(24950L, data.amountPaise)
        assertEquals("249.50", data.amountString)
        assertEquals("Groceries", data.note)
        assertEquals("5411", data.merchantCode)
        assertEquals("Grocery Stores & Supermarkets", data.merchantCategoryName)
        assertEquals("TXN987654321", data.transactionRef)
        assertEquals("02", data.mode)
        assertEquals("00", data.purpose)
    }

    @Test
    fun `parse valid UPI QR case-insensitive scheme`() {
        val qr = "UPI://PAY?pa=retailer@ybl&pn=Retailer&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("retailer@ybl", data.upiId)
        assertEquals("Retailer", data.recipientName)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. URL-Decoding & Special Characters
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse properly URL-decodes payee name with spaces and ampersand`() {
        val qr = "upi://pay?pa=shree_sweets@icici&pn=Shree%20%26%20Sons%20Sweets&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("Shree & Sons Sweets", data.recipientName)
    }

    @Test
    fun `parse properly URL-decodes transaction note with punctuation`() {
        val qr = "upi://pay?pa=user@upi&pn=User&tn=Order%20%23492%3A%20Dinner%20%2B%20Tip&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals("Order #492: Dinner + Tip", data.note)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Scheme and Non-UPI Validation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `reject invalid scheme - https URL`() {
        val qr = "https://upi.example.com/pay?pa=test@upi"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("Not a UPI QR code", ignoreCase = true))
    }

    @Test
    fun `reject invalid scheme - plain text`() {
        val qr = "Payment for dinner: 500 rupees"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
    }

    @Test
    fun `reject blank QR text`() {
        val result = UpiQrParser.parse("   ")
        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("empty", ignoreCase = true))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Missing or Invalid Required Parameters
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `reject missing payee UPI ID (pa)`() {
        val qr = "upi://pay?pn=Merchant&am=100.00&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("Missing payee UPI ID", ignoreCase = true))
    }

    @Test
    fun `fallback payee name when pn is missing`() {
        val qr = "upi://pay?pa=9876543210@paytm&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        // If pn is omitted, parser should fallback to identifier part of UPI ID
        assertEquals("9876543210", data.recipientName)
    }

    @Test
    fun `reject invalid UPI ID format`() {
        val qr = "upi://pay?pa=not-a-valid-vpa&pn=Merchant&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("Invalid payee UPI address", ignoreCase = true))
    }

    @Test
    fun `reject unsupported currency`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&cu=USD"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("Unsupported currency 'USD'", ignoreCase = true))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Amount Parsing & Validation (Smart Amount Mode)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `reject non-numeric amount`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=one-hundred&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("Invalid amount format", ignoreCase = true))
    }

    @Test
    fun `reject zero amount`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=0&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
    }

    @Test
    fun `reject zero amount with decimals`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=0.00&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
    }

    @Test
    fun `reject negative amount`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=-100.00&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
    }

    @Test
    fun `reject excessive decimal precision in amount`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=10.123&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
    }

    @Test
    fun `parse amount formatted as single decimal digit`() {
        val qr = "upi://pay?pa=merchant@upi&pn=Merchant&am=10.5&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Success)
        val data = (result as UpiQrParseResult.Success).data
        assertEquals(1050L, data.amountPaise)
        assertEquals("10.50", data.amountString)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Conflicting Duplicate Query Parameters
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `reject conflicting duplicate payee parameters`() {
        val qr = "upi://pay?pa=first@upi&pn=Merchant&pa=second@upi&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("conflicting values for parameter 'pa'", ignoreCase = true))
    }

    @Test
    fun `reject conflicting duplicate amount parameters`() {
        val qr = "upi://pay?pa=first@upi&pn=Merchant&am=10.00&am=20.00&cu=INR"
        val result = UpiQrParser.parse(qr)

        assertTrue(result is UpiQrParseResult.Invalid)
        val res = result as UpiQrParseResult.Invalid
        assertTrue(res.reason.contains("conflicting values for parameter 'am'", ignoreCase = true))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. MCC Code Lookups
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `lookupKnownMcc returns expected descriptions`() {
        assertEquals("Grocery Stores & Supermarkets", UpiQrParser.lookupMccDescription("5411"))
        assertEquals("Restaurants & Dining", UpiQrParser.lookupMccDescription("5812"))
        assertEquals("Fast Food & Cafes", UpiQrParser.lookupMccDescription("5814"))
        assertEquals("Pharmacies & Medical", UpiQrParser.lookupMccDescription("5912"))
        assertEquals("Fuel & Service Stations", UpiQrParser.lookupMccDescription("5541"))
        assertEquals("General Merchant (9999)", UpiQrParser.lookupMccDescription("9999"))
        assertNull(UpiQrParser.lookupMccDescription(null))
        assertNull(UpiQrParser.lookupMccDescription(""))
    }
}
