package com.upisegregator.app.data.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.upisegregator.app.util.PaiseUtils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Data class representing a UPI-capable application discovered on the device.
 */
data class UpiApp(
    val packageName: String,
    val activityName: String,
    val displayName: String
)

/**
 * Validation result for a UPI ID.
 */
sealed class UpiIdValidation {
    data object Valid : UpiIdValidation()
    data class Invalid(val reason: String) : UpiIdValidation()
}

/**
 * Result of attempting to launch a UPI payment intent.
 */
sealed class UpiLaunchResult {
    data class Success(val intent: Intent) : UpiLaunchResult()
    data class Fallback(val chooserIntent: Intent) : UpiLaunchResult()
    data object NoAppAvailable : UpiLaunchResult()
    data class Error(val message: String) : UpiLaunchResult()
}

/**
 * Result of validating a generated UPI payment URI against expected financial parameters.
 */
sealed class UpiUriValidationResult {
    data class Valid(
        val uriString: String,
        val originalPaise: Long,
        val parsedPaise: Long,
        val recipientMasked: String,
        val paramNames: List<String>
    ) : UpiUriValidationResult()

    data class Invalid(val reason: String) : UpiUriValidationResult()
}

/**
 * Result of preparing a complete UPI payment redirection.
 */
sealed class PaymentRedirectionResult {
    data class Success(val intent: Intent, val uri: Uri) : PaymentRedirectionResult()
    data class ValidationFailed(val reason: String) : PaymentRedirectionResult()
    data class AppUnavailable(val packageName: String) : PaymentRedirectionResult()
    data object NoAppAvailable : PaymentRedirectionResult()
    data class Error(val message: String) : PaymentRedirectionResult()
}

/**
 * Structured query parameters extracted from a parsed UPI URI string.
 */
data class ParsedUpiParams(
    val pa: String?,
    val pn: String?,
    val am: String?,
    val cu: String?,
    val tn: String?,
    val tr: String?,
    val allParamNames: List<String>,
    val duplicateKeys: Set<String>
)

/**
 * Builds and validates UPI payment intents.
 *
 * SECURITY RULES (never break these):
 * - Never include a UPI PIN, OTP, or banking password.
 * - Never attempt to directly process a transaction.
 * - All amounts are formatted from integer paise — never Float or Double.
 * - All URI parameters are properly encoded.
 */
object UpiIntentBuilder {

    /**
     * Validates a UPI ID for basic structural correctness.
     * Format: <identifier>@<handle> where handle has no dot.
     * Examples: rahul@upi, user@ybl, 9876543210@paytm
     */
    fun validateUpiId(upiId: String): UpiIdValidation {
        val trimmed = upiId.trim()
        if (trimmed.isBlank()) return UpiIdValidation.Invalid("UPI ID cannot be empty")
        if (!trimmed.contains("@")) return UpiIdValidation.Invalid("UPI ID must contain '@' (e.g. name@upi)")
        val parts = trimmed.split("@")
        if (parts.size != 2) return UpiIdValidation.Invalid("UPI ID must have exactly one '@'")
        val (identifier, handle) = parts
        if (identifier.isBlank()) return UpiIdValidation.Invalid("The part before '@' cannot be empty")
        if (handle.isBlank()) return UpiIdValidation.Invalid("The part after '@' cannot be empty (e.g. upi, ybl, paytm)")
        if (handle.contains(".")) return UpiIdValidation.Invalid("UPI handle should not contain a dot (e.g. use '@upi' not '@upi.com')")
        if (identifier.length > 256 || handle.length > 256) return UpiIdValidation.Invalid("UPI ID is too long")
        val identifierRegex = Regex("^[a-zA-Z0-9._\\-]+$")
        if (!identifierRegex.matches(identifier)) return UpiIdValidation.Invalid("UPI ID contains invalid characters")
        val handleRegex = Regex("^[a-zA-Z0-9]+$")
        if (!handleRegex.matches(handle)) return UpiIdValidation.Invalid("UPI handle must be alphanumeric (e.g. upi, ybl, paytm)")
        return UpiIdValidation.Valid
    }

    /**
     * Masks a UPI ID for safe logging.
     * Example: rahul@upi -> r***l@upi, 9876543210@paytm -> 98***0@paytm.
     */
    fun maskUpiId(upiId: String): String {
        val trimmed = upiId.trim()
        val atIndex = trimmed.indexOf('@')
        if (atIndex <= 1) return "***" + trimmed.substring(atIndex.coerceAtLeast(0))
        val identifier = trimmed.substring(0, atIndex)
        val handle = trimmed.substring(atIndex)
        val maskedIdentifier = if (identifier.length <= 3) {
            "${identifier.first()}***"
        } else {
            "${identifier.take(2)}***${identifier.takeLast(1)}"
        }
        return "$maskedIdentifier$handle"
    }

    /**
     * Builds a pure, minimal, standard-compliant UPI payment URI string.
     *
     * Essential standard UPI parameters generated:
     * - pa: Recipient UPI ID (exact, trimmed, URL-encoded)
     * - pn: Recipient name (trimmed, fallback to UPI ID, URL-encoded)
     * - am: Amount in decimal rupees (e.g. 1000L paise -> "10.00")
     * - cu: Currency code (always "INR")
     * - tn: Optional note (appended only if non-blank, max 50 chars, URL-encoded)
     * - tr: Optional transaction reference (appended only if explicitly provided, alphanumeric, max 35 chars)
     *
     * STRICT OMISSIONS:
     * - Does NOT append empty "tn=" parameters.
     * - Does NOT append unnecessary "mc" parameters.
     * - Does NOT append unnecessary "mode" parameters.
     * - Does NOT append unnecessary "purpose" parameters.
     * - Does NOT append duplicate parameters.
     * - Does NOT generate synthetic/fake "tr" parameters for P2P payments.
     */
    fun buildUpiUriString(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = "",
        transactionRef: String? = null
    ): String {
        require(amountPaise > 0L) { "Amount must be strictly positive" }
        val trimmedUpiId = payeeUpiId.trim()
        val validation = validateUpiId(trimmedUpiId)
        require(validation is UpiIdValidation.Valid) {
            (validation as UpiIdValidation.Invalid).reason
        }

        val amountString = PaiseUtils.paiseToRupeesString(amountPaise)
        val name = payeeName.trim().ifBlank { trimmedUpiId }

        val encodeParam = { s: String ->
            URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

        val sb = StringBuilder("upi://pay?")
        sb.append("pa=").append(encodeParam(trimmedUpiId))
        sb.append("&pn=").append(encodeParam(name))
        sb.append("&am=").append(amountString)
        sb.append("&cu=INR")

        val cleanNote = note.trim().take(50)
        if (cleanNote.isNotEmpty()) {
            sb.append("&tn=").append(encodeParam(cleanNote))
        }

        if (!transactionRef.isNullOrBlank()) {
            val cleanTr = transactionRef.trim().filter { it.isLetterOrDigit() }.take(35)
            if (cleanTr.isNotEmpty()) {
                sb.append("&tr=").append(encodeParam(cleanTr))
            }
        }

        return sb.toString()
    }

    /**
     * Internally regenerates an amount-prefilled UPI payment URI from an original scanned QR payload,
     * preserving all relevant original parameters (merchant code, terminal ID, mode, orgid, etc.).
     *
     * SECURITY & COMPLIANCE RULES:
     * - Uses exact decimal rupees formatting (e.g. 10.00).
     * - Strictly prevents altering fixed-amount QR payloads.
     * - Preserves P2P vs P2M characteristics — never injects synthetic merchant parameters into P2P QR.
     * - Preserves all original query parameters (mc, tr, mode, purpose, orgid, tid, mid, url, sign, etc.).
     * - Never adds empty note parameters.
     */
    fun regenerateUpiUriString(
        originalData: UpiQrData,
        amountPaise: Long,
        overrideNote: String? = null
    ): String {
        require(amountPaise > 0L) { "Amount must be strictly positive" }

        // If the original QR had a fixed amount, it must not be modified
        if (originalData.isAmountFixed && originalData.amountPaise != null) {
            require(originalData.amountPaise == amountPaise) {
                "Cannot alter amount for a fixed-amount QR code (original: ${originalData.amountPaise} paise, requested: $amountPaise paise)"
            }
        }

        val trimmedUpiId = originalData.upiId.trim()
        val validation = validateUpiId(trimmedUpiId)
        require(validation is UpiIdValidation.Valid) {
            (validation as UpiIdValidation.Invalid).reason
        }

        val amountString = PaiseUtils.paiseToRupeesString(amountPaise)
        val name = originalData.recipientName.trim().ifBlank { trimmedUpiId }

        val encodeParam = { s: String ->
            URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

        val sb = StringBuilder("upi://pay?")
        sb.append("pa=").append(encodeParam(trimmedUpiId))
        sb.append("&pn=").append(encodeParam(name))

        // Preserve Merchant Code (mc) if present
        val mc = originalData.merchantCode ?: originalData.allParameters["mc"]
        if (!mc.isNullOrBlank()) {
            sb.append("&mc=").append(encodeParam(mc.trim()))
        }

        // Preserve Transaction Reference (tr) if present
        val tr = originalData.transactionRef ?: originalData.allParameters["tr"]
        if (!tr.isNullOrBlank()) {
            sb.append("&tr=").append(encodeParam(tr.trim()))
        }

        // Standard amount and currency
        sb.append("&am=").append(amountString)
        sb.append("&cu=INR")

        // Note (tn)
        val cleanNote = (overrideNote?.takeIf { it.isNotBlank() } ?: originalData.note).trim().take(50)
        if (cleanNote.isNotEmpty()) {
            sb.append("&tn=").append(encodeParam(cleanNote))
        }

        // Preserve all remaining original parameters (excluding pa, pn, mc, tr, am, cu, tn)
        val standardHandled = setOf("pa", "pn", "mc", "tr", "am", "cu", "tn")

        if (originalData.allParameters.isNotEmpty()) {
            for ((key, value) in originalData.allParameters) {
                val lowerKey = key.lowercase()
                if (lowerKey !in standardHandled && value.isNotBlank()) {
                    sb.append("&").append(encodeParam(key)).append("=").append(encodeParam(value.trim()))
                }
            }
        } else {
            // Fallback for manually instantiated UpiQrData
            originalData.mode?.takeIf { it.isNotBlank() }?.let {
                sb.append("&mode=").append(encodeParam(it.trim()))
            }
            originalData.purpose?.takeIf { it.isNotBlank() }?.let {
                sb.append("&purpose=").append(encodeParam(it.trim()))
            }
            originalData.terminalId?.takeIf { it.isNotBlank() }?.let {
                sb.append("&tid=").append(encodeParam(it.trim()))
            }
            originalData.merchantId?.takeIf { it.isNotBlank() }?.let {
                sb.append("&mid=").append(encodeParam(it.trim()))
            }
            originalData.orgId?.takeIf { it.isNotBlank() }?.let {
                sb.append("&orgid=").append(encodeParam(it.trim()))
            }
            originalData.url?.takeIf { it.isNotBlank() }?.let {
                sb.append("&url=").append(encodeParam(it.trim()))
            }
            originalData.sign?.takeIf { it.isNotBlank() }?.let {
                sb.append("&sign=").append(encodeParam(it.trim()))
            }
        }

        return sb.toString()
    }

    /**
     * Builds a standard android.net.Uri using [buildUpiUriString].
     */
    fun buildUpiUri(
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        note: String = "",
        transactionRef: String? = null
    ): Uri {
        val uriString = buildUpiUriString(
            payeeUpiId = payeeUpiId,
            payeeName = payeeName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = transactionRef
        )
        return Uri.parse(uriString)
    }

    /**
     * Pure-Kotlin parser for UPI URI strings.
     * Extracts query parameters and flags any duplicate keys.
     */
    fun parseUpiUriString(uriString: String): ParsedUpiParams {
        val trimmed = uriString.trim()
        val schemeSplit = trimmed.split(":", limit = 2)
        val afterScheme = if (schemeSplit.size == 2) schemeSplit[1] else trimmed
        val queryStart = afterScheme.indexOf('?')
        if (queryStart == -1) {
            return ParsedUpiParams(null, null, null, null, null, null, emptyList(), emptySet())
        }
        val queryString = afterScheme.substring(queryStart + 1)
        val pairs = queryString.split("&")

        val paramNames = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        val map = mutableMapOf<String, String>()

        for (pair in pairs) {
            if (pair.isBlank()) continue
            val parts = pair.split("=", limit = 2)
            val rawKey = parts[0].trim().lowercase()
            val rawVal = if (parts.size > 1) parts[1].trim() else ""
            paramNames.add(rawKey)
            if (!seenKeys.add(rawKey)) {
                duplicates.add(rawKey)
            }
            val decoded = try {
                URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                rawVal
            }
            map[rawKey] = decoded
        }

        return ParsedUpiParams(
            pa = map["pa"],
            pn = map["pn"],
            am = map["am"],
            cu = map["cu"],
            tn = map["tn"],
            tr = map["tr"],
            allParamNames = paramNames,
            duplicateKeys = duplicates
        )
    }

    /**
     * VALIDATION LAYER:
     * Validates a generated UPI URI before launching any external payment app.
     *
     * 1. Parses the URI string.
     * 2. Verifies recipient UPI ID matches expected and is valid.
     * 3. Verifies currency is strictly INR.
     * 4. Parses the amount back into paise using [PaiseUtils.rupeesToPaise].
     * 5. Compares parsed paise against the original reviewed amount.
     * 6. Rejects the request if values differ.
     * 7. Ensures there are no duplicate query parameters.
     * 8. Emits safe diagnostic logging with masked recipient and no sensitive data.
     */
    /**
     * VALIDATION LAYER:
     * Validates a generated/regenerated UPI URI against expected financial parameters
     * by passing it directly through our existing [UpiQrParser] (Requirement 6).
     *
     * 1. Parses the regenerated URI through [UpiQrParser.parse].
     * 2. Verifies recipient UPI ID matches expected and is valid.
     * 3. Verifies currency is strictly INR.
     * 4. Verifies parsed paise matches expected amount.
     * 5. Ensures amount is strictly formatted as decimal rupees with 2 decimal places.
     * 6. Ensures there are no duplicate query parameters.
     * 7. Ensures original fixed-amount QR codes have not had their amount modified.
     * 8. Ensures P2P/P2M classification is preserved (prevents accidental P2P to P2M conversion).
     * 9. Ensures original merchant metadata (mc, tr, mode, tid, etc.) is preserved.
     * 10. Emits safe diagnostic logging with masked recipient and no sensitive data.
     */
    fun validateRegeneratedPayload(
        regeneratedUriString: String,
        expectedPaise: Long,
        expectedUpiId: String,
        originalData: UpiQrData? = null,
        selectedPackage: String? = null
    ): UpiUriValidationResult {
        val trimmedUri = regeneratedUriString.trim()
        if (!trimmedUri.startsWith("upi://pay?") && !trimmedUri.startsWith("upi:pay?")) {
            return UpiUriValidationResult.Invalid("Invalid UPI URI format: must start with upi://pay?")
        }

        // 1. Pass through existing QR parser (Requirement 6)
        val parseResult = UpiQrParser.parse(trimmedUri)
        if (parseResult is UpiQrParseResult.Invalid) {
            return UpiUriValidationResult.Invalid("Regenerated URI failed QR parser validation: ${parseResult.reason}")
        }

        val reparsed = (parseResult as UpiQrParseResult.Success).data

        // 2. Check for duplicate query parameters
        val uriParamDetails = parseUpiUriString(trimmedUri)
        if (uriParamDetails.duplicateKeys.isNotEmpty()) {
            return UpiUriValidationResult.Invalid(
                "Duplicate parameter(s) found in regenerated URI: ${uriParamDetails.duplicateKeys.joinToString()}"
            )
        }

        // 3. Verify recipient UPI ID matches
        if (!reparsed.upiId.equals(expectedUpiId.trim(), ignoreCase = true)) {
            return UpiUriValidationResult.Invalid(
                "Recipient UPI ID mismatch: expected $expectedUpiId, got ${reparsed.upiId}"
            )
        }
        val idValidation = validateUpiId(reparsed.upiId)
        if (idValidation is UpiIdValidation.Invalid) {
            return UpiUriValidationResult.Invalid("Invalid recipient UPI ID: ${idValidation.reason}")
        }

        // 4. Verify currency is strictly INR
        if (!reparsed.currency.equals("INR", ignoreCase = true)) {
            return UpiUriValidationResult.Invalid(
                "Invalid currency: expected INR, got ${reparsed.currency}"
            )
        }

        // 5. Verify amount format and parse back to paise
        val am = uriParamDetails.am
        if (am.isNullOrBlank()) {
            return UpiUriValidationResult.Invalid("Missing amount (am)")
        }
        if (!Regex("^\\d+\\.\\d{2}$").matches(am)) {
            return UpiUriValidationResult.Invalid(
                "Amount must be formatted as decimal rupees (e.g. 10.00), got: $am"
            )
        }

        if (reparsed.amountPaise == null || reparsed.amountPaise != expectedPaise) {
            return UpiUriValidationResult.Invalid(
                "Amount mismatch: expected $expectedPaise paise, but URI parsed as ${reparsed.amountPaise} paise"
            )
        }

        // 6. Ensure no empty tn= parameter
        if (uriParamDetails.allParamNames.contains("tn") && uriParamDetails.tn.isNullOrBlank()) {
            return UpiUriValidationResult.Invalid("Empty note parameter 'tn' should not be present in URI")
        }

        // 7. Verify parameter preservation against original QR (if available)
        if (originalData != null) {
            // Fixed-amount QR protection
            if (originalData.isAmountFixed && originalData.amountPaise != null && reparsed.amountPaise != originalData.amountPaise) {
                return UpiUriValidationResult.Invalid(
                    "Cannot modify amount on a fixed-amount QR code: original was ${originalData.amountPaise} paise"
                )
            }

            // Prevent accidental P2P to P2M conversion
            if (originalData.paymentType == UpiPaymentType.P2P && !reparsed.merchantCode.isNullOrBlank()) {
                return UpiUriValidationResult.Invalid(
                    "Accidental P2P to P2M conversion: merchant code '${reparsed.merchantCode}' was added to P2P payment"
                )
            }

            // Verify payment type classification is preserved
            if (originalData.paymentType != UpiPaymentType.UNKNOWN && reparsed.paymentType != originalData.paymentType) {
                return UpiUriValidationResult.Invalid(
                    "Payment type classification changed: expected ${originalData.paymentType}, got ${reparsed.paymentType}"
                )
            }

            // Merchant Code preservation
            val origMc = originalData.merchantCode ?: originalData.allParameters["mc"]
            if (!origMc.isNullOrBlank() && reparsed.merchantCode != origMc) {
                return UpiUriValidationResult.Invalid(
                    "Merchant code lost or altered: expected '$origMc', got '${reparsed.merchantCode}'"
                )
            }

            // Transaction Reference preservation
            val origTr = originalData.transactionRef ?: originalData.allParameters["tr"]
            if (!origTr.isNullOrBlank() && reparsed.transactionRef != origTr) {
                return UpiUriValidationResult.Invalid(
                    "Transaction reference lost or altered: expected '$origTr', got '${reparsed.transactionRef}'"
                )
            }

            // Mode preservation
            val origMode = originalData.mode ?: originalData.allParameters["mode"]
            if (!origMode.isNullOrBlank() && reparsed.mode != origMode) {
                return UpiUriValidationResult.Invalid(
                    "Mode parameter lost or altered: expected '$origMode', got '${reparsed.mode}'"
                )
            }
        }

        // 8. Safe diagnostic logging
        val maskedRecipient = maskUpiId(expectedUpiId)
        logSafeValidationDiagnostic(
            originalPaise = expectedPaise,
            formattedRupees = am,
            parsedPaise = reparsed.amountPaise,
            selectedPackage = selectedPackage,
            paramNames = uriParamDetails.allParamNames,
            maskedRecipient = maskedRecipient
        )

        return UpiUriValidationResult.Valid(
            uriString = trimmedUri,
            originalPaise = expectedPaise,
            parsedPaise = reparsed.amountPaise,
            recipientMasked = maskedRecipient,
            paramNames = uriParamDetails.allParamNames
        )
    }

    /**
     * Backward-compatible validator method. Delegates to [validateRegeneratedPayload].
     */
    fun validateUpiUriString(
        uriString: String,
        expectedPaise: Long,
        expectedUpiId: String,
        selectedPackage: String? = null
    ): UpiUriValidationResult {
        return validateRegeneratedPayload(
            regeneratedUriString = uriString,
            expectedPaise = expectedPaise,
            expectedUpiId = expectedUpiId,
            originalData = null,
            selectedPackage = selectedPackage
        )
    }

    /**
     * Validates an android.net.Uri using [validateRegeneratedPayload].
     */
    fun validateUpiUri(
        uri: Uri,
        expectedPaise: Long,
        expectedUpiId: String,
        selectedPackage: String? = null
    ): UpiUriValidationResult {
        return validateRegeneratedPayload(uri.toString(), expectedPaise, expectedUpiId, null, selectedPackage)
    }

    /**
     * Safe diagnostic logger that never throws on JVM unit tests and never logs sensitive data.
     */
    fun logSafeValidationDiagnostic(
        originalPaise: Long,
        formattedRupees: String,
        parsedPaise: Long,
        selectedPackage: String?,
        paramNames: List<String>,
        maskedRecipient: String
    ) {
        val message = "Payment Validation -> OriginalPaise: $originalPaise | FormattedRupees: $formattedRupees | " +
                "ParsedPaise: $parsedPaise | TargetPkg: ${selectedPackage ?: "CHOOSER"} | " +
                "Params: $paramNames | Recipient: $maskedRecipient"
        try {
            android.util.Log.d("UPI_PAYMENT_DEBUG", message)
        } catch (_: Throwable) {
            println("UPI_PAYMENT_DEBUG: $message")
        }
    }

    /**
     * Safely logs non-sensitive payment initiation parameters for debugging.
     * NEVER logs PIN, OTP, credentials, or full account identifiers.
     */
    fun logSafePaymentDebug(
        payeeUpiId: String,
        amountPaise: Long,
        amountString: String,
        uri: Uri,
        selectedPackage: String? = null
    ) {
        val masked = maskUpiId(payeeUpiId)
        val message = "Payment URI Built -> Recipient: $masked | AmountPaise: $amountPaise | am: $amountString | cu: INR | Uri: $uri | TargetPkg: $selectedPackage"
        try {
            android.util.Log.d("UPI_PAYMENT_DEBUG", message)
        } catch (_: Throwable) {
            println("UPI_PAYMENT_DEBUG: $message")
        }
    }

    /**
     * CENTRAL REDIRECTION FUNCTION:
     * Builds and validates a payment redirection using internal QR payload regeneration.
     *
     * 1. Preserves original QR metadata (merchant code, terminal ID, etc.) if [originalData] is provided.
     * 2. Formats amount as decimal rupees with BigDecimal validation.
     * 3. Re-parses and validates the generated URI string through [UpiQrParser.parse].
     * 4. Resolves the target intent (selected app or system chooser).
     */
    fun buildPaymentRedirection(
        context: Context,
        originalData: UpiQrData?,
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        selectedPackage: String?,
        note: String = "",
        transactionRef: String? = null
    ): PaymentRedirectionResult {
        val uriString = try {
            if (originalData != null) {
                regenerateUpiUriString(
                    originalData = originalData,
                    amountPaise = amountPaise,
                    overrideNote = note
                )
            } else {
                buildUpiUriString(
                    payeeUpiId = payeeUpiId,
                    payeeName = payeeName,
                    amountPaise = amountPaise,
                    note = note,
                    transactionRef = transactionRef
                )
            }
        } catch (e: Exception) {
            return PaymentRedirectionResult.ValidationFailed(e.message ?: "Failed to build UPI URI")
        }

        val validation = validateRegeneratedPayload(
            regeneratedUriString = uriString,
            expectedPaise = amountPaise,
            expectedUpiId = payeeUpiId,
            originalData = originalData,
            selectedPackage = selectedPackage
        )
        if (validation is UpiUriValidationResult.Invalid) {
            return PaymentRedirectionResult.ValidationFailed(validation.reason)
        }

        val uri = Uri.parse(uriString)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)

        if (!selectedPackage.isNullOrBlank()) {
            if (!isUpiAppAvailable(context, selectedPackage)) {
                return PaymentRedirectionResult.AppUnavailable(selectedPackage)
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(selectedPackage)
            }
            return PaymentRedirectionResult.Success(intent, uri)
        }

        // Chooser fallback if no package selected
        val apps = getInstalledUpiApps(context)
        if (apps.isEmpty()) {
            return PaymentRedirectionResult.NoAppAvailable
        }
        if (apps.size == 1) {
            val app = apps.first()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(app.packageName)
            }
            return PaymentRedirectionResult.Success(intent, uri)
        }

        val chooserIntent = Intent.createChooser(baseIntent, "Pay with UPI")
        return PaymentRedirectionResult.Success(chooserIntent, uri)
    }

    /**
     * Backward-compatible overload without originalData.
     */
    fun buildPaymentRedirection(
        context: Context,
        payeeUpiId: String,
        payeeName: String,
        amountPaise: Long,
        selectedPackage: String?,
        note: String = "",
        transactionRef: String? = null
    ): PaymentRedirectionResult {
        return buildPaymentRedirection(
            context = context,
            originalData = null,
            payeeUpiId = payeeUpiId,
            payeeName = payeeName,
            amountPaise = amountPaise,
            selectedPackage = selectedPackage,
            note = note,
            transactionRef = transactionRef
        )
    }

    /**
     * Creates an ACTION_VIEW intent for the given UPI URI targeting a specific package.
     * Uses setPackage() for maximum resilience against internal class renaming by UPI apps.
     *
     * @param forResult If true, does NOT add FLAG_ACTIVITY_NEW_TASK so that startActivityForResult
     *                  can properly deliver the result to the caller instead of immediately cancelling.
     */
    fun buildIntentForPackage(
        uri: Uri,
        packageName: String,
        activityName: String = "",
        forResult: Boolean = true
    ): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            if (!forResult) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Creates a chooser intent that lets the user pick any compatible UPI app.
     */
    fun buildChooserIntent(
        uri: Uri,
        title: String = "Pay with UPI",
        forResult: Boolean = true
    ): Intent {
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
        return Intent.createChooser(baseIntent, title).apply {
            if (!forResult) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Top Indian UPI application packages declared in AndroidManifest.xml <queries>.
     * Used as a resilient fallback probe to guarantee detection even when OEM systems
     * (such as Motorola / Lenovo Moto Android 11+ builds) restrict implicit intent queries.
     */
    val KNOWN_UPI_PACKAGES = listOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "in.amazon.mShop.android.shopping" to "Amazon Pay",
        "com.whatsapp" to "WhatsApp",
        "com.cred.club" to "CRED",
        "com.naviapps.mobile" to "Navi"
    )

    /**
     * Discovers all UPI-capable apps installed on the device.
     *
     * 1. Uses probe URI (upi://pay) with queryIntentActivities using both flag 0 and MATCH_DEFAULT_ONLY.
     * 2. Probes explicitly declared UPI packages (Google Pay, PhonePe, etc.) to ensure detection
     *    even on Android 11+ OEM devices where generic intent matching may be restricted.
     *
     * Adheres strictly to Android 11+ package visibility rules without requiring QUERY_ALL_PACKAGES.
     */
    fun getInstalledUpiApps(context: Context): List<UpiApp> {
        val pm = context.packageManager
        val probeUri = Uri.parse("upi://pay")
        val probeIntent = Intent(Intent.ACTION_VIEW, probeUri)
        val discovered = mutableListOf<UpiApp>()

        // Step 1: Query matching activities via PackageManager
        val resolveInfoList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val listZero = try {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } catch (_: Exception) {
                emptyList()
            }
            val listDefault = try {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } catch (_: Exception) {
                emptyList()
            }
            (listZero + listDefault).distinctBy { it.activityInfo?.packageName }
        } else {
            @Suppress("DEPRECATION")
            val listZero = try {
                pm.queryIntentActivities(probeIntent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            @Suppress("DEPRECATION")
            val listDefault = try {
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) {
                emptyList()
            }
            (listZero + listDefault).distinctBy { it.activityInfo?.packageName }
        }

        for (info in resolveInfoList) {
            try {
                val packageName = info.activityInfo?.packageName ?: continue
                val activityName = info.activityInfo?.name ?: ""
                val label = info.loadLabel(pm).toString()
                discovered.add(
                    UpiApp(
                        packageName = packageName,
                        activityName = activityName,
                        displayName = label.ifBlank { packageName }
                    )
                )
            } catch (_: Exception) {
                // Ignore individual parsing failures
            }
        }

        // Step 2: Targeted probe for known UPI apps (Google Pay, PhonePe, Paytm, etc.)
        for ((pkg, defaultName) in KNOWN_UPI_PACKAGES) {
            if (discovered.none { it.packageName == pkg }) {
                try {
                    val targetedIntent = Intent(Intent.ACTION_VIEW, probeUri).apply {
                        setPackage(pkg)
                    }
                    val targetedResolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(
                            targetedIntent,
                            PackageManager.ResolveInfoFlags.of(0L)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(targetedIntent, 0)
                    }

                    if (targetedResolves.isNotEmpty()) {
                        val info = targetedResolves.first()
                        val label = try {
                            info.loadLabel(pm).toString()
                        } catch (_: Exception) {
                            defaultName
                        }
                        discovered.add(
                            UpiApp(
                                packageName = pkg,
                                activityName = info.activityInfo?.name ?: "",
                                displayName = label.ifBlank { defaultName }
                            )
                        )
                    } else {
                        // Check if package is installed via getPackageInfo (permitted via <queries><package>)
                        val isInstalled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                                true
                            } catch (_: Exception) {
                                false
                            }
                        } else {
                            try {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(pkg, 0)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        if (isInstalled) {
                            val label = try {
                                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getApplicationInfo(pkg, 0)
                                }
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                defaultName
                            }
                            discovered.add(
                                UpiApp(
                                    packageName = pkg,
                                    activityName = "",
                                    displayName = label.ifBlank { defaultName }
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Package not installed, safely ignore
                }
            }
        }

        return discovered.distinctBy { it.packageName }
    }

    /**
     * Checks whether a specific package is still installed and can handle UPI intents.
     */
    fun isUpiAppAvailable(context: Context, packageName: String): Boolean {
        val availableApps = getInstalledUpiApps(context)
        return availableApps.any { it.packageName == packageName }
    }

    /**
     * Prepares the best launch result for a payment:
     * 1. If [preferredPackage] is set and available -> target it directly.
     * 2. If only one UPI app is available -> use it directly.
     * 3. If multiple apps available -> show chooser.
     * 4. If no apps available -> return NoAppAvailable.
     */
    fun resolvePaymentLaunch(
        context: Context,
        uri: Uri,
        preferredPackage: String?,
        forResult: Boolean = true
    ): UpiLaunchResult {
        val apps = getInstalledUpiApps(context)
        if (apps.isEmpty()) return UpiLaunchResult.NoAppAvailable

        // Try preferred / selected app first
        if (!preferredPackage.isNullOrBlank()) {
            val preferred = apps.firstOrNull { it.packageName == preferredPackage }
            if (preferred != null) {
                val intent = buildIntentForPackage(uri, preferred.packageName, preferred.activityName, forResult = forResult)
                return UpiLaunchResult.Success(intent)
            }
            // Preferred app is gone -> fall through
        }

        // Single app -> direct launch
        if (apps.size == 1) {
            val app = apps.first()
            val intent = buildIntentForPackage(uri, app.packageName, app.activityName, forResult = forResult)
            return UpiLaunchResult.Success(intent)
        }

        // Multiple apps -> chooser
        return UpiLaunchResult.Fallback(buildChooserIntent(uri, forResult = forResult))
    }
}
