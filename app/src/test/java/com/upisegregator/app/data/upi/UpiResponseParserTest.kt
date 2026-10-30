package com.upisegregator.app.data.upi

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit tests for NPCI UPI Intent response parsing.
 * Runs on pure JVM without Android framework dependencies.
 */
class UpiResponseParserTest {

    @Test
    fun `successful response string is parsed correctly`() {
        val raw = "txnId=UPI123456789&responseCode=00&ApprovalRefNo=987654321012&Status=SUCCESS&txnRef=order_abc_123"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.SUCCESS, response.status)
        assertEquals("UPI123456789", response.txnId)
        assertEquals("00", response.responseCode)
        assertEquals("987654321012", response.approvalRefNo)
        assertEquals("order_abc_123", response.txnRef)
        assertFalse("Client-side response must never be marked bank-verified", response.isBankVerified)
        assertTrue(response.message.contains("success", ignoreCase = true))
    }

    @Test
    fun `failed response string is parsed correctly`() {
        val raw = "txnId=UPI999&responseCode=ZM&Status=FAILURE&txnRef=test_fail"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.FAILURE, response.status)
        assertEquals("ZM", response.responseCode)
        assertEquals("UPI999", response.txnId)
        assertFalse(response.isBankVerified)
        assertTrue(response.message.contains("failed", ignoreCase = true))
    }

    @Test
    fun `cancelled response string is parsed correctly`() {
        val raw = "Status=CANCELLED&txnRef=tx_123"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.CANCELLED, response.status)
        assertTrue(response.message.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun `submitted pending response string is parsed correctly`() {
        val raw = "Status=SUBMITTED&ApprovalRefNo=SUB123&responseCode=01"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.SUBMITTED, response.status)
        assertEquals("SUB123", response.approvalRefNo)
        assertEquals("01", response.responseCode)
        assertTrue(response.message.contains("processing", ignoreCase = true) || response.message.contains("submitted", ignoreCase = true))
    }

    @Test
    fun `empty and blank response with RESULT_CANCELED returns CANCELLED`() {
        val responseEmpty = UpiResponseParser.parseResponseString("", resultCode = Activity.RESULT_CANCELED)
        assertEquals(UpiResponseStatus.CANCELLED, responseEmpty.status)

        val responseBlank = UpiResponseParser.parseResponseString("   ", resultCode = Activity.RESULT_CANCELED)
        assertEquals(UpiResponseStatus.CANCELLED, responseBlank.status)
    }

    @Test
    fun `empty response with RESULT_OK returns UNKNOWN`() {
        val response = UpiResponseParser.parseResponseString("", resultCode = Activity.RESULT_OK)
        assertEquals(UpiResponseStatus.UNKNOWN, response.status)
    }

    @Test
    fun `malformed response string handled gracefully without crash`() {
        val malformed = "===&&&invalid_garbage=&=&something"
        val response = UpiResponseParser.parseResponseString(malformed)

        // Must not crash, should yield UNKNOWN
        assertEquals(UpiResponseStatus.UNKNOWN, response.status)
    }

    @Test
    fun `case-insensitive status and parameter parsing`() {
        val raw = "status=success&approvalrefno=999888&txnid=T123"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.SUCCESS, response.status)
        assertEquals("999888", response.approvalRefNo)
        assertEquals("T123", response.txnId)
    }

    @Test
    fun `alternative parameter keys rrn and txn_id are parsed`() {
        val raw = "Status=SUCCESS&rrn=BANK_RRN_777&txn_id=TXN_456"
        val response = UpiResponseParser.parseResponseString(raw)

        assertEquals(UpiResponseStatus.SUCCESS, response.status)
        assertEquals("BANK_RRN_777", response.approvalRefNo)
        assertEquals("TXN_456", response.txnId)
    }

    @Test
    fun `user dropped or cancelled variants map to CANCELLED`() {
        val variants = listOf("CANCEL", "CANCELLED", "CANCELED", "USER_CANCELLED", "USER_DROPPED")
        for (v in variants) {
            val response = UpiResponseParser.parseResponseString("Status=$v")
            assertEquals("Expected $v to map to CANCELLED", UpiResponseStatus.CANCELLED, response.status)
        }
    }

    @Test
    fun `all parsed responses have isBankVerified set to false`() {
        val inputs = listOf(
            "Status=SUCCESS",
            "Status=FAILURE",
            "Status=SUBMITTED",
            "Status=CANCELLED",
            "random=data"
        )
        for (input in inputs) {
            val res = UpiResponseParser.parseResponseString(input)
            assertFalse(res.isBankVerified)
        }
    }
}
