package com.upisegregator.app.data.upi

import android.app.Activity
import android.content.Intent

/**
 * Normalized payment status reported by an external UPI application.
 */
enum class UpiResponseStatus {
    /**
     * The UPI application reported a successful payment outcome.
     * NOTE: This is client-side intent data; it is not independently bank-verified.
     */
    SUCCESS,

    /**
     * The UPI application reported a failed transaction (e.g. incorrect PIN, bank decline).
     */
    FAILURE,

    /**
     * The transaction was submitted to the banking network and is currently pending/processing.
     */
    SUBMITTED,

    /**
     * The user cancelled or exited the UPI application without completing the payment.
     */
    CANCELLED,

    /**
     * No conclusive status could be parsed from the returned intent data.
     */
    UNKNOWN
}

/**
 * Structured outcome parsed from an external UPI application's Activity Result.
 *
 * @property status Normalized status enum.
 * @property rawResponse The raw query string or message returned by the app, if any.
 * @property txnId The UPI transaction ID returned by the UPI app / bank.
 * @property responseCode Response code from the UPI network (e.g. "00" for success, "ZM" for invalid MPIN).
 * @property approvalRefNo Bank Reference / RRN (Retrieval Reference Number) if available.
 * @property txnRef Merchant / initiator transaction reference if available.
 * @property message User-friendly explanation of the detected outcome.
 * @property isBankVerified Always false for client-side UPI intent handoff.
 */
data class UpiPaymentResponse(
    val status: UpiResponseStatus,
    val rawResponse: String? = null,
    val txnId: String? = null,
    val responseCode: String? = null,
    val approvalRefNo: String? = null,
    val txnRef: String? = null,
    val message: String,
    val isBankVerified: Boolean = false
)

/**
 * Defensively parses the intent result returned by external UPI applications
 * according to NPCI UPI Intent specifications.
 *
 * Handles:
 * - URL-encoded / query-string format in Intent extra "response" (GPay, PhonePe, Paytm standard).
 * - Individual extras (fallback).
 * - Empty / null responses (user pressed Back or cancelled).
 * - RESULT_CANCELED vs RESULT_OK activity codes.
 * - Case-insensitive parameter matching.
 */
object UpiResponseParser {

    /**
     * Parses the response from an Android Activity Result.
     */
    fun parse(resultCode: Int, data: Intent?): UpiPaymentResponse {
        if (data == null) {
            return if (resultCode == Activity.RESULT_CANCELED) {
                UpiPaymentResponse(
                    status = UpiResponseStatus.CANCELLED,
                    message = "Payment was cancelled or returned without completing in the UPI app."
                )
            } else {
                UpiPaymentResponse(
                    status = UpiResponseStatus.UNKNOWN,
                    message = "No response data was returned by the UPI application."
                )
            }
        }

        // Standard NPCI response format: packed into "response" extra as query-string
        val rawResponse = data.getStringExtra("response")
            ?: data.getStringExtra("status")
            ?: data.dataString

        if (!rawResponse.isNullOrBlank()) {
            val parsed = parseResponseString(rawResponse, resultCode)
            if (parsed.status != UpiResponseStatus.UNKNOWN || parsed.txnId != null || parsed.approvalRefNo != null) {
                return parsed
            }
        }

        // Fallback: check individual intent extras
        val statusExtra = data.getStringExtra("Status") ?: data.getStringExtra("status")
        val txnIdExtra = data.getStringExtra("txnId") ?: data.getStringExtra("txnid")
        val approvalRefExtra = data.getStringExtra("ApprovalRefNo") ?: data.getStringExtra("approvalRefNo")
        val responseCodeExtra = data.getStringExtra("responseCode") ?: data.getStringExtra("responsecode")
        val txnRefExtra = data.getStringExtra("txnRef") ?: data.getStringExtra("txnref")

        if (statusExtra != null || txnIdExtra != null || approvalRefExtra != null) {
            val status = mapStatus(statusExtra, resultCode)
            return UpiPaymentResponse(
                status = status,
                rawResponse = statusExtra,
                txnId = txnIdExtra,
                responseCode = responseCodeExtra,
                approvalRefNo = approvalRefExtra,
                txnRef = txnRefExtra,
                message = statusToMessage(status)
            )
        }

        // No recognized extras
        return if (resultCode == Activity.RESULT_CANCELED) {
            UpiPaymentResponse(
                status = UpiResponseStatus.CANCELLED,
                message = "Payment was cancelled or returned without completing in the UPI app."
            )
        } else {
            UpiPaymentResponse(
                status = UpiResponseStatus.UNKNOWN,
                message = "We couldn't confirm the payment status. Please check your UPI app or bank transaction history."
            )
        }
    }

    /**
     * Parses a raw query-string response (pure Kotlin, easily unit-testable on JVM).
     * Format: "Status=SUCCESS&ApprovalRefNo=123456&txnId=UPI12345&responseCode=00&txnRef=..."
     */
    fun parseResponseString(raw: String, resultCode: Int = Activity.RESULT_OK): UpiPaymentResponse {
        val params = mutableMapOf<String, String>()

        // Split key-value pairs separated by '&'
        val pairs = raw.split("&")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()
                if (key.isNotEmpty()) {
                    params[key] = value
                }
            } else if (parts.size == 1 && pair.isNotBlank()) {
                // Lone keyword e.g. "SUCCESS" or "FAILURE"
                val lone = pair.trim()
                if (params.isEmpty() && isKnownStatusWord(lone)) {
                    params["status"] = lone
                }
            }
        }

        val statusRaw = params["status"]
        val status = mapStatus(statusRaw, resultCode)
        val txnId = params["txnid"] ?: params["txn_id"]
        val approvalRefNo = params["approvalrefno"] ?: params["approval_ref_no"] ?: params["rrn"]
        val responseCode = params["responsecode"] ?: params["response_code"]
        val txnRef = params["txnref"] ?: params["txn_ref"]

        return UpiPaymentResponse(
            status = status,
            rawResponse = raw,
            txnId = txnId,
            responseCode = responseCode,
            approvalRefNo = approvalRefNo,
            txnRef = txnRef,
            message = statusToMessage(status)
        )
    }

    private fun mapStatus(raw: String?, resultCode: Int): UpiResponseStatus {
        if (raw == null) {
            return if (resultCode == Activity.RESULT_CANCELED) {
                UpiResponseStatus.CANCELLED
            } else {
                UpiResponseStatus.UNKNOWN
            }
        }

        return when (raw.trim().uppercase()) {
            "SUCCESS", "S", "SUCCESSFUL" -> UpiResponseStatus.SUCCESS
            "FAILURE", "FAILED", "FAIL", "F" -> UpiResponseStatus.FAILURE
            "SUBMITTED", "PENDING" -> UpiResponseStatus.SUBMITTED
            "CANCEL", "CANCELLED", "CANCELED", "USER_CANCELLED", "USER_DROPPED" -> UpiResponseStatus.CANCELLED
            else -> {
                if (resultCode == Activity.RESULT_CANCELED) {
                    UpiResponseStatus.CANCELLED
                } else {
                    UpiResponseStatus.UNKNOWN
                }
            }
        }
    }

    private fun isKnownStatusWord(word: String): Boolean {
        return when (word.trim().uppercase()) {
            "SUCCESS", "FAILURE", "FAILED", "SUBMITTED", "PENDING", "CANCELLED", "CANCELED" -> true
            else -> false
        }
    }

    private fun statusToMessage(status: UpiResponseStatus): String {
        return when (status) {
            UpiResponseStatus.SUCCESS -> "UPI application reported payment success."
            UpiResponseStatus.FAILURE -> "UPI application reported that the payment failed."
            UpiResponseStatus.SUBMITTED -> "Payment submitted to bank and is currently processing."
            UpiResponseStatus.CANCELLED -> "Payment was cancelled or returned without completing in the UPI app."
            UpiResponseStatus.UNKNOWN -> "We couldn't confirm the payment status. Please check your UPI app or bank transaction history."
        }
    }
}
