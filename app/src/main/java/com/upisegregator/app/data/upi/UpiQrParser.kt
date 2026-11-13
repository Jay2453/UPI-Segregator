package com.upisegregator.app.data.upi

import com.upisegregator.app.util.PaiseUtils
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * NPCI UPI transaction payment classification.
 */
enum class UpiPaymentType {
    /** Person-to-Person payment (e.g. personal VPAs, no merchant attributes). */
    P2P,
    /** Person-to-Merchant payment (e.g. verified MCC, merchant identifiers). */
    P2M,
    /** Unclassified or ambiguous payment type (insufficient or conflicting evidence). */
    UNKNOWN
}

/**
 * Structured data extracted from a validated UPI payment QR code.
 */
data class UpiQrData(
    val upiId: String,
    val recipientName: String,
    val amountPaise: Long? = null,
    val amountString: String? = null,
    val currency: String = "INR",
    val note: String = "",
    val transactionRef: String? = null,
    val merchantCode: String? = null,
    val merchantCategoryName: String? = null,
    val mode: String? = null,
    val purpose: String? = null,
    val isAmountFixed: Boolean = false,
    val rawQrString: String = "",
    val paymentType: UpiPaymentType = UpiPaymentType.UNKNOWN,
    val terminalId: String? = null,
    val merchantId: String? = null,
    val orgId: String? = null,
    val url: String? = null,
    val sign: String? = null,
    val allParameters: Map<String, String> = emptyMap()
)

/**
 * Result of attempting to parse a scanned QR code.
 */
sealed class UpiQrParseResult {
    data class Success(val data: UpiQrData) : UpiQrParseResult()
    data class Invalid(val reason: String) : UpiQrParseResult()
}

/**
 * Robust, secure parser for UPI QR codes conforming to NPCI specifications.
 * Pure Kotlin — runs seamlessly on JVM and Android.
 */
object UpiQrParser {

    private val KNOWN_MCC = mapOf(
        "5411" to "Grocery Stores & Supermarkets",
        "5812" to "Restaurants & Dining",
        "5814" to "Fast Food & Cafes",
        "5912" to "Pharmacies & Medical",
        "5541" to "Fuel & Service Stations",
        "5542" to "Automated Fuel Dispensers",
        "5311" to "Department Stores",
        "5462" to "Bakeries",
        "5732" to "Electronics & Gadgets",
        "8220" to "Education & Colleges",
        "4111" to "Transportation & Metro",
        "4814" to "Telecom & Utility Services",
        "6011" to "Financial Services",
        "7298" to "Health & Beauty Spas",
        "7999" to "Recreation & Entertainment"
    )

    fun lookupMccDescription(code: String?): String? {
        if (code.isNullOrBlank()) return null
        return KNOWN_MCC[code.trim()] ?: "General Merchant ($code)"
    }

    /**
     * Classifies a transaction payload as P2P, P2M, or UNKNOWN based on reliable evidence.
     *
     * Rules:
     * - P2M: reliable merchant evidence (standard 4-digit numeric MCC other than "0000",
     *   or presence of merchant terminal/store identifiers: tid, mid, msid, mtid).
     * - P2P: standard personal transfer where mc and merchant identifiers are absent,
     *   and a valid payee UPI ID is present.
     * - UNKNOWN: ambiguous, unassigned, or malformed data (e.g. mc="0000", non-numeric MCC,
     *   or insufficient evidence).
     */
    fun classifyPaymentType(params: Map<String, String>): UpiPaymentType {
        val mc = params["mc"]?.trim()
        val mid = params["mid"]?.trim()
        val msid = params["msid"]?.trim()
        val mtid = params["mtid"]?.trim()
        val tid = params["tid"]?.trim()

        val hasMerchantIds = !mid.isNullOrBlank() || !msid.isNullOrBlank() ||
                !mtid.isNullOrBlank() || !tid.isNullOrBlank()

        if (!mc.isNullOrBlank()) {
            val isStandardNumericMcc = mc.length == 4 && mc.all { it.isDigit() }
            if (isStandardNumericMcc && mc != "0000") {
                return UpiPaymentType.P2M
            }
            if (hasMerchantIds) {
                return UpiPaymentType.P2M
            }
            // mc is "0000", non-numeric, or malformed without merchant IDs -> UNKNOWN
            return UpiPaymentType.UNKNOWN
        }

        if (hasMerchantIds) {
            return UpiPaymentType.P2M
        }

        val pa = params["pa"]?.trim()
        if (!pa.isNullOrBlank() && UpiIntentBuilder.validateUpiId(pa) is UpiIdValidation.Valid) {
            return UpiPaymentType.P2P
        }

        return UpiPaymentType.UNKNOWN
    }

    /**
     * Parses the raw string scanned from a QR code.
     * Returns [UpiQrParseResult.Success] if the QR code is a valid UPI payment QR code,
     * or [UpiQrParseResult.Invalid] with a descriptive reason otherwise.
     */
    fun parse(rawQr: String?): UpiQrParseResult {
        val trimmed = rawQr?.trim() ?: return UpiQrParseResult.Invalid("QR code is empty")
        if (trimmed.isBlank()) return UpiQrParseResult.Invalid("QR code is empty")

        // 1. Verify URI scheme and authority (case-insensitive)
        val schemeSplit = trimmed.split(":", limit = 2)
        if (schemeSplit.size != 2 || !schemeSplit[0].equals("upi", ignoreCase = true)) {
            return UpiQrParseResult.Invalid("Not a UPI QR code. Please scan a standard UPI payment QR.")
        }

        val afterScheme = schemeSplit[1] // e.g. "//pay?pa=... or pay?pa=..."
        val normalizedAfterScheme = afterScheme.removePrefix("//")
        val questionMarkIndex = normalizedAfterScheme.indexOf('?')
        if (questionMarkIndex == -1) {
            return UpiQrParseResult.Invalid("Invalid UPI QR code format: missing payment parameters.")
        }

        val authority = normalizedAfterScheme.substring(0, questionMarkIndex).trim()
        if (!authority.equals("pay", ignoreCase = true)) {
            return UpiQrParseResult.Invalid("Unsupported UPI operation: '$authority'. Only 'pay' QR codes are supported.")
        }

        val queryString = normalizedAfterScheme.substring(questionMarkIndex + 1).trim()
        if (queryString.isEmpty()) {
            return UpiQrParseResult.Invalid("UPI QR code contains no payment details.")
        }

        // 2. Parse query parameters and check for duplicate/conflicting keys
        val paramPairs = queryString.split("&")
        val params = mutableMapOf<String, String>()
        val multiValuedKeys = mutableMapOf<String, MutableList<String>>()

        for (pair in paramPairs) {
            if (pair.isBlank()) continue
            val parts = pair.split("=", limit = 2)
            if (parts.isEmpty()) continue
            val rawKey = parts[0].trim().lowercase()
            val rawVal = if (parts.size > 1) parts[1].trim() else ""

            val decodedVal = try {
                URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                rawVal
            }

            val list = multiValuedKeys.getOrPut(rawKey) { mutableListOf() }
            list.add(decodedVal)
            params[rawKey] = decodedVal
        }

        // Check for conflicting duplicate parameters
        for ((key, values) in multiValuedKeys) {
            val distinctValues = values.distinct()
            if (distinctValues.size > 1) {
                return UpiQrParseResult.Invalid("Malformed QR code: conflicting values for parameter '$key'.")
            }
        }

        // 3. Extract and validate Payee UPI ID (pa)
        val payeeUpiId = params["pa"]?.trim()
        if (payeeUpiId.isNullOrBlank()) {
            return UpiQrParseResult.Invalid("Missing payee UPI ID (pa) in QR code.")
        }

        when (val v = UpiIntentBuilder.validateUpiId(payeeUpiId)) {
            is UpiIdValidation.Valid -> { /* valid */ }
            is UpiIdValidation.Invalid -> {
                return UpiQrParseResult.Invalid("Invalid payee UPI address in QR code: ${v.reason}")
            }
        }

        // 4. Extract Payee Name (pn)
        val rawName = params["pn"]?.trim() ?: ""
        val recipientName = if (rawName.isNotBlank()) {
            rawName
        } else {
            // Default to identifier part of UPI ID if name not supplied
            payeeUpiId.substringBefore("@")
        }

        // 5. Extract and validate Currency (cu)
        val currency = params["cu"]?.trim()?.uppercase()
        if (!currency.isNullOrBlank() && currency != "INR") {
            return UpiQrParseResult.Invalid("Unsupported currency '$currency'. Only INR payments are supported.")
        }

        // 6. Extract and validate Amount (am) - SMART AMOUNT MODE
        val rawAmount = params["am"]?.trim()
        var amountPaise: Long? = null
        var amountString: String? = null
        var isAmountFixed = false

        if (!rawAmount.isNullOrBlank()) {
            val parsedPaise = PaiseUtils.rupeesToPaiseOrNull(rawAmount)
            if (parsedPaise == null || parsedPaise <= 0L) {
                return UpiQrParseResult.Invalid("Invalid amount format in QR code: '$rawAmount'.")
            }
            amountPaise = parsedPaise
            amountString = PaiseUtils.paiseToRupeesString(parsedPaise)
            isAmountFixed = true
        }

        // 7. Extract Note (tn), Reference (tr), MCC (mc), mode, purpose, and merchant identifiers
        val note = params["tn"]?.trim() ?: ""
        val transactionRef = params["tr"]?.trim()?.takeIf { it.isNotBlank() }
        val merchantCode = params["mc"]?.trim()?.takeIf { it.isNotBlank() }
        val merchantCategoryName = lookupMccDescription(merchantCode)
        val mode = params["mode"]?.trim()?.takeIf { it.isNotBlank() }
        val purpose = params["purpose"]?.trim()?.takeIf { it.isNotBlank() }
        val terminalId = params["tid"]?.trim()?.takeIf { it.isNotBlank() }
        val merchantId = params["mid"]?.trim()?.takeIf { it.isNotBlank() }
        val orgId = params["orgid"]?.trim()?.takeIf { it.isNotBlank() }
        val url = params["url"]?.trim()?.takeIf { it.isNotBlank() }
        val sign = params["sign"]?.trim()?.takeIf { it.isNotBlank() }
        val paymentType = classifyPaymentType(params)

        return UpiQrParseResult.Success(
            UpiQrData(
                upiId = payeeUpiId,
                recipientName = recipientName,
                amountPaise = amountPaise,
                amountString = amountString,
                currency = "INR",
                note = note.take(50),
                transactionRef = transactionRef,
                merchantCode = merchantCode,
                merchantCategoryName = merchantCategoryName,
                mode = mode,
                purpose = purpose,
                isAmountFixed = isAmountFixed,
                rawQrString = trimmed,
                paymentType = paymentType,
                terminalId = terminalId,
                merchantId = merchantId,
                orgId = orgId,
                url = url,
                sign = sign,
                allParameters = params
            )
        )
    }
}
